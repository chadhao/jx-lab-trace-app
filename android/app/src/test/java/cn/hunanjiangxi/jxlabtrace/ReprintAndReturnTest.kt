package cn.hunanjiangxi.jxlabtrace

import cn.hunanjiangxi.jxlabtrace.net.ApiClient
import cn.hunanjiangxi.jxlabtrace.net.ApiError
import cn.hunanjiangxi.jxlabtrace.net.ApiResult
import cn.hunanjiangxi.jxlabtrace.net.InMemorySidStore
import cn.hunanjiangxi.jxlabtrace.net.JxCookieJar
import cn.hunanjiangxi.jxlabtrace.net.JxLogger
import cn.hunanjiangxi.jxlabtrace.recv.RecvRepository
import cn.hunanjiangxi.jxlabtrace.recv.ReprintForm
import cn.hunanjiangxi.jxlabtrace.recv.ReturnFlow
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * M5-3：补打 —— reason 为空 ⇒ 提交按钮禁用（纯逻辑）；reprint 请求体含 reason（§4.6 bag_code+reason）。
 * M5-4：退车 —— POST /api/app/recv/return 请求体（notice_id+reason）与二次确认文案。
 */
class ReprintAndReturnTest {

    private lateinit var server: MockWebServer
    private val recorded = ArrayList<RecordedRequest>()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.method == "POST") recorded.add(request)
                return when {
                    request.path?.startsWith("/healthz") == true ->
                        MockResponse().setResponseCode(200)
                    request.path?.contains("/api/app/label/reprint") == true ->
                        MockResponse().setResponseCode(200)
                            .setHeader("Content-Type", "application/json")
                            .setBody(
                                """{"count":1,"labels":[{"label_id":1,"code":"1BCG0001000226102003007007",
                                   "human":"H","kind":"原料吨袋","is_reprint":1,"reason":"标签破损 · 撕破重贴",
                                   "printed_by":"ou_x"}],"page_url":"/p"}""",
                            )
                    request.path?.contains("/api/app/recv/return") == true ->
                        MockResponse().setResponseCode(200)
                            .setHeader("Content-Type", "application/json")
                            .setBody(
                                """{"returned":true,"truck_lot_id":3,"truck_code":"1A",
                                   "notice_id":12,"status":"已退货","bag_count":0}""",
                            )
                    else ->
                        MockResponse().setResponseCode(404).setBody("""{"error":"no"}""")
                }
            }
        }
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun repo(): RecvRepository {
        val api = ApiClient(
            baseUrl = server.url("/").toString(),
            cookieJar = JxCookieJar(InMemorySidStore()),
            logger = JxLogger { _, _, _ -> },
        )
        return RecvRepository(api)
    }

    // ---- M5-3 ----

    @Test
    fun `M5-3 空原因 - 表单无效提交禁用`() {
        assertFalse("空类别", ReprintForm.isValid(null, "说明有"))
        assertFalse("空说明", ReprintForm.isValid("标签破损", "   "))
        assertFalse("两者皆空", ReprintForm.isValid(null, ""))
        assertFalse("非三选一", ReprintForm.isValid("随便写的", "说明"))
    }

    @Test
    fun `M5-3 有效原因 - 表单可提交`() {
        for (reason in ReprintForm.REASONS) {
            assertTrue(reason, ReprintForm.isValid(reason, "撕破重贴"))
        }
        assertEquals(listOf("标签破损", "打印模糊", "贴错重贴"), ReprintForm.REASONS)
    }

    @Test
    fun `M5-3 reprint请求体含bag_code与reason`() = runTest {
        val result = repo().reprintLabel(
            bagCode = "1BCG0001000226102003007007",
            reason = ReprintForm.reasonText("标签破损", "撕破重贴，位置不变"),
        )
        assertTrue("reprint 应成功，实际=$result", result is ApiResult.Success)
        val request = recorded.last()
        assertEquals("POST", request.method)
        assertTrue(request.path!!.contains("/api/app/label/reprint"))
        val body = request.body.readUtf8()
        assertTrue("body 应含 bag_code：$body", body.contains("\"bag_code\""))
        assertTrue("body 应含 reason 字段：$body", body.contains("\"reason\""))
        assertTrue("body 应含原因文本：$body", body.contains("标签破损"))
        assertTrue("body 应含说明：$body", body.contains("撕破重贴"))
        val success = result as ApiResult.Success
        assertEquals(1, success.data.count)
        assertEquals(1, success.data.labels.size)
        assertEquals(1, success.data.labels.first().is_reprint)
    }

    // ---- M5-4 ----

    @Test
    fun `M5-4 退车二次确认文案 - 组件复用口径固定`() {
        assertTrue(ReturnFlow.CONFIRM_TITLE.contains("退车"))
        assertTrue(ReturnFlow.CONFIRM_MESSAGE.contains("不可撤销"))
        assertTrue(ReturnFlow.canReturn("待检"))
        assertFalse(ReturnFlow.canReturn("已退货"))
    }

    @Test
    fun `M5-4 POST return 按notice_id`() = runTest {
        val result = repo().returnTruck(12, reason = "质检判定退货")
        assertTrue("return 应成功，实际=$result", result is ApiResult.Success)
        val request = recorded.last()
        assertEquals("POST", request.method)
        assertTrue(request.path!!.contains("/api/app/recv/return"))
        val body = request.body.readUtf8()
        assertTrue("body 应含 notice_id：$body", body.contains("\"notice_id\":12"))
        assertTrue("body 应含 reason：$body", body.contains("质检判定退货"))
        val data = (result as ApiResult.Success).data
        assertEquals("已退货", data.status)
        assertEquals(3L, data.truck_lot_id)
    }

    @Test
    fun `M5-4 退车无退货判定 - 409 结构化拦截四字段`() = runTest {
        val s409 = MockWebServer()
        s409.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                if (request.path?.startsWith("/healthz") == true) {
                    MockResponse().setResponseCode(200)
                } else {
                    MockResponse().setResponseCode(409)
                        .setBody(
                            """{"error":"尚无退货判定","reason":"该车尚无质检「退货」判定，不能退车。",
                               "owner":"收货文员（收货与打码）","contact":"飞书联系收货文员按页面提示处理"}""",
                        )
                }
        }
        s409.start()
        try {
            val api = ApiClient(
                baseUrl = s409.url("/").toString(),
                cookieJar = JxCookieJar(InMemorySidStore()),
                logger = JxLogger { _, _, _ -> },
            )
            val result = RecvRepository(api).returnTruck(12)
            assertTrue("409 应为 Failure，实际=$result", result is ApiResult.Failure)
            val error = (result as ApiResult.Failure).error
            assertTrue("应映射为结构化拦截，实际=${error::class.simpleName}", error is ApiError.Intercept)
            error as ApiError.Intercept
            assertEquals(409, error.httpCode)
            assertEquals("尚无退货判定", error.error)
            assertTrue(error.reason.contains("退货"))
            assertTrue(error.owner.isNotBlank())
            assertTrue(error.contact.isNotBlank())
        } finally {
            s409.shutdown()
        }
    }
}
