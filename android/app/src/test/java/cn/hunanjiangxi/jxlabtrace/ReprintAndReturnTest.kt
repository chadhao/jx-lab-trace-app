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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * M5-3：补打 —— reason 为空 ⇒ 提交按钮禁用（纯逻辑）；reprint 请求体含 reason。
 * M5-4：退车 —— POST return 请求体与二次确认文案。
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
                    request.path?.contains("/labels/reprint") == true ->
                        MockResponse().setResponseCode(200)
                            .setHeader("Content-Type", "application/json")
                            .setBody("""{"count":1,"labels":[],"page_url":"/p"}""")
                    request.path?.contains("/return") == true ->
                        MockResponse().setResponseCode(200)
                            .setHeader("Content-Type", "application/json")
                            .setBody("""{"returned":true,"row":{"id":3,"status":"已退货"}}""")
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
    fun `M5-3 reprint请求体含reason`() = runTest {
        val result = repo().reprintLabels(
            codes = listOf("1BCG0001000226102003007007"),
            reason = ReprintForm.reasonText("标签破损", "撕破重贴，位置不变"),
        )
        assertTrue("reprint 应成功，实际=$result", result is ApiResult.Success)
        val request = recorded.last()
        assertEquals("POST", request.method)
        assertTrue(request.path!!.contains("/labels/reprint"))
        val body = request.body.readUtf8()
        assertTrue("body 应含 reason 字段：$body", body.contains("\"reason\""))
        assertTrue("body 应含原因文本：$body", body.contains("标签破损"))
        assertTrue("body 应含说明：$body", body.contains("撕破重贴"))
        assertTrue("body 应含 codes：$body", body.contains("\"codes\""))
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
    fun `M5-4 POST return 单测`() = runTest {
        val result = repo().returnTruck(3, reason = "质检判定退货")
        assertTrue("return 应成功，实际=$result", result is ApiResult.Success)
        val request = recorded.last()
        assertEquals("POST", request.method)
        assertTrue(request.path!!.contains("/trucks/3/return"))
        val body = request.body.readUtf8()
        assertTrue("body 应含 reason：$body", body.contains("质检判定退货"))
        assertEquals("已退货", (result as ApiResult.Success).data.status)
    }

    @Test
    fun `M5-4 退车无退货判定 - 409 映射为业务错误`() = runTest {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.path?.startsWith("/healthz") == true) {
                    return MockResponse().setResponseCode(200).setBody("ok")
                }
                return MockResponse().setResponseCode(409)
                    .setBody("""{"error":"该车尚无退货判定，不能退车"}""")
            }
        }
        val result = repo().returnTruck(3)
        // 409 走 Server 分类（网络层 mapResponse 对非 2xx 归 Server）——
        // 但 409 非 401/403 ⇒ ApiError.Server；断言失败类型非 Success 且有话术
        assertNotNull(result)
        assertTrue(result is ApiResult.Failure)
        val error = (result as ApiResult.Failure).error
        assertFalse(error.userMessage.isBlank())
        assertTrue(error is ApiError.Server || error is ApiError.Biz)
    }
}
