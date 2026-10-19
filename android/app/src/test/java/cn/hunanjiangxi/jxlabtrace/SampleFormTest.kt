package cn.hunanjiangxi.jxlabtrace

import cn.hunanjiangxi.jxlabtrace.net.ApiClient
import cn.hunanjiangxi.jxlabtrace.net.ApiResult
import cn.hunanjiangxi.jxlabtrace.net.InMemorySidStore
import cn.hunanjiangxi.jxlabtrace.net.JxCookieJar
import cn.hunanjiangxi.jxlabtrace.net.JxLogger
import cn.hunanjiangxi.jxlabtrace.sample.SampleForm
import cn.hunanjiangxi.jxlabtrace.sample.SampleKind
import cn.hunanjiangxi.jxlabtrace.sample.SampleRepository
import cn.hunanjiangxi.jxlabtrace.sample.SampleTargetType
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
 * M6-1：取样表单默认值填充后即有效（必填 ≤1）。
 * M6-2：POST take 请求体含 target_type/target_id/sample_weight/samples，不含 sampled_by。
 */
class SampleFormTest {

    private lateinit var server: MockWebServer
    private var takeBody: String? = null

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.path?.startsWith("/healthz") == true) {
                    return MockResponse().setResponseCode(200).setBody("ok")
                }
                if (request.method == "POST" && request.path?.contains("/sample/take") == true) {
                    takeBody = request.body.readUtf8()
                    return MockResponse().setResponseCode(200)
                        .setBody("""{"created":2,"parent":"P","code":"C","human":"H","samples":[]}""")
                }
                if (request.path?.contains("/sample/targets") == true) {
                    return MockResponse().setResponseCode(200).setBody(
                        """{"count":1,"rows":[{"id":7,"code":"1BCG00010002261007030070007",
                            "human":"1B-CG-0001-0002-261007-03-007-000-7","status":"在库","extra":"2026-10-07"}]}""",
                    )
                }
                return MockResponse().setResponseCode(404).setBody("""{"error":"no"}""")
            }
        }
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    // ---- M6-1 ----

    @Test
    fun `M6-1 默认值填充后表单有效`() {
        val defaults = SampleForm.defaults()
        assertEquals(500.0, defaults.sample_weight, 0.001)
        assertEquals(5, defaults.samples)
        assertEquals(SampleKind.INCREMENTAL.label, SampleKind.INCREMENTAL.label)
        // defaults 未定位目标 ⇒ 无效（code/target_id 必须由扫码/选对象带入）
        assertFalse(SampleForm.isValid(defaults))

        // 定位目标后（默认类型/量/仓数不变）⇒ 有效，必填 0
        val located = SampleForm.build(
            code = "1BCG00010002261007030070007",
            targetType = SampleTargetType.TRUCK,
            targetId = 7,
        )
        assertTrue("默认填充后应有效（M6-1）", SampleForm.isValid(located))
        assertEquals(500.0, located.sample_weight, 0.001)
        assertEquals(5, located.samples)
    }

    @Test
    fun `M6-1 非法取样量或份数 - 无效`() {
        val base = SampleForm.build("C", SampleTargetType.BATCH, 1)
        assertFalse(SampleForm.isValid(base.copy(sample_weight = 0.0)))
        assertFalse(SampleForm.isValid(base.copy(samples = 0)))
        assertFalse(SampleForm.isValid(base.copy(target_id = 0)))
        assertFalse(SampleForm.isValid(base.copy(code = "")))
    }

    // ---- M6-2 ----

    @Test
    fun `M6-2 POST take 请求体形状`() = runTest {
        val api = ApiClient(
            baseUrl = server.url("/").toString(),
            cookieJar = JxCookieJar(InMemorySidStore()),
            logger = JxLogger { _, _, _ -> },
        )
        val body = SampleForm.build(
            code = "1BG00010002261007030070007",
            targetType = SampleTargetType.TRUCK,
            targetId = 7,
            weightG = 500.0,
            samples = 5,
        )
        assertTrue(SampleForm.isValid(body))
        val result = SampleRepository(api).take(body)
        assertTrue(result is ApiResult.Success)

        val sent = requireNotNull(takeBody)
        assertTrue("含 target_type", sent.contains("\"target_type\""))
        assertTrue("含 target_id", sent.contains("\"target_id\""))
        assertTrue("含 sample_weight", sent.contains("\"sample_weight\""))
        assertTrue("含 samples", sent.contains("\"samples\""))
        assertFalse("不得含 sampled_by（由后端会话定）", sent.contains("sampled_by"))
        // 具体值
        assertTrue(sent.contains("\"车次\""))
        assertTrue(sent.contains("\"target_id\":7"))
    }

    @Test
    fun `M6-2 SampleTakeBody 序列化不含 sampled_by 字段`() {
        val json = SampleForm.toJson(
            SampleForm.build("X", SampleTargetType.FG_LOT, 9, 250.0, 3),
        )
        assertFalse(json.contains("sampled_by"))
        assertTrue(json.contains("\"target_type\":\"成品批\""))
        assertTrue(json.contains("\"sample_weight\":250.0"))
        assertTrue(json.contains("\"samples\":3"))
    }
}
