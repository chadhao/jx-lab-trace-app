package cn.hunanjiangxi.jxlabtrace

import cn.hunanjiangxi.jxlabtrace.net.ApiClient
import cn.hunanjiangxi.jxlabtrace.net.ApiError
import cn.hunanjiangxi.jxlabtrace.net.InMemorySidStore
import cn.hunanjiangxi.jxlabtrace.net.JxCookieJar
import cn.hunanjiangxi.jxlabtrace.net.JxLogger
import cn.hunanjiangxi.jxlabtrace.scan.ScanFailure
import cn.hunanjiangxi.jxlabtrace.scan.ScanService
import cn.hunanjiangxi.jxlabtrace.scan.display
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * M4-2：失败映射单测 —— MockWebServer 桩 `GET /api/recv/scan` 返回 400/404/409/403 各一，
 * 断言 [ScanFailure] 五类中对应类正确，且五类文案互不相同。
 * 另覆盖：409「已作废」→ Voided；200 但对象状态「作废」→ Voided；200 正常 → Found。
 */
class ScanFailureMappingTest {

    private lateinit var server: MockWebServer
    private var lastPath: String? = null

    private val successBody = """
        {"found":true,"result":{
          "kind":"bag",
          "code":"1BCG00010002261007030070007",
          "human":"1B-CG-0001-0002-261007-03-007-000-7",
          "bag":{"id":7,"code":"1BCG00010002261007030070007","bag_seq":7,
                 "weight_allocated":1.02,"truck_lot_id":3,"status":"待投料"}
        }}
    """.trimIndent()

    private val voidedBody = """
        {"found":true,"result":{
          "kind":"bag",
          "code":"1BCG00010002261007030070007",
          "human":"1B-CG-0001-0002-261007-03-007-000-7",
          "bag":{"id":7,"code":"1BCG00010002261007030070007","bag_seq":7,
                 "weight_allocated":null,"truck_lot_id":3,"status":"作废"}
        }}
    """.trimIndent()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                lastPath = request.path
                return if (request.path?.startsWith("/healthz") == true) {
                    MockResponse().setResponseCode(200).setBody("ok")
                } else {
                    MockResponse().setResponseCode(200).setBody(successBody)
                }
            }
        }
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun service(): ScanService {
        val api = ApiClient(
            baseUrl = server.url("/").toString(),
            cookieJar = JxCookieJar(InMemorySidStore()),
            logger = JxLogger { _, _, _ -> },
        )
        return ScanService(api)
    }

    private fun stub(code: Int, body: String) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                lastPath = request.path
                return MockResponse().setResponseCode(code).setBody(body)
            }
        }
    }

    @Test
    fun `400 - NotOurs（不是本系统的码）`() = runTest {
        stub(400, """{"error":"不是本系统的码" }""")
        val outcome = service().resolve("1ACG0001000226100703000000L")
        assertTrue("实际 $outcome", outcome is ScanService.Outcome.Failed)
        val failure = (outcome as ScanService.Outcome.Failed).failure
        assertTrue("应为 NotOurs，实际 $failure", failure is ScanFailure.NotOurs)
        assertTrue(lastPath.orEmpty().contains("/api/recv/scan?code="))
        assertTrue("400 文案应透传后端原因", failure.display().detail.contains("不是本系统的码"))
    }

    @Test
    fun `404 - UnknownCode（格式正确但系统内不存在）`() = runTest {
        stub(404, """{"error":"格式正确但系统内不存在：1ACG0001000226100703000000L"}""")
        val outcome = service().resolve("1ACG0001000226100703000000L")
        assertTrue(outcome is ScanService.Outcome.Failed)
        val failure = (outcome as ScanService.Outcome.Failed).failure
        assertTrue("应为 UnknownCode，实际 $failure", failure is ScanFailure.UnknownCode)
        assertTrue(failure.display().detail.contains("格式正确但系统内不存在"))
    }

    @Test
    fun `409 - Rejected（业务拒绝，带 code）`() = runTest {
        stub(409, """{"code":"ALREADY_FED","error":"该袋已于 09:41 投入批 2410-007" }""")
        val outcome = service().resolve("1BCG00010002261007030070007")
        assertTrue(outcome is ScanService.Outcome.Failed)
        val failure = (outcome as ScanService.Outcome.Failed).failure
        assertTrue("应为 Rejected，实际 $failure", failure is ScanFailure.Rejected)
        failure as ScanFailure.Rejected
        assertEquals("ALREADY_FED", failure.code)
        assertTrue(failure.message.contains("投入"))
    }

    @Test
    fun `409 已作废 - Voided（与 Rejected 区分）`() = runTest {
        stub(409, """{"error":"该对象已作废：1BCG00010002261007030070007"}""")
        val outcome = service().resolve("1BCG00010002261007030070007")
        assertTrue(outcome is ScanService.Outcome.Failed)
        assertTrue(
            "含「作废」的 409 应判 Voided，实际 ${(outcome as ScanService.Outcome.Failed).failure}",
            outcome.failure is ScanFailure.Voided,
        )
    }

    @Test
    fun `403 - NoPermission（话术为统一越权文案）`() = runTest {
        stub(403, """{"error":"forbidden"}""")
        val outcome = service().resolve("1ACG0001000226100703000000L")
        assertTrue(outcome is ScanService.Outcome.Failed)
        val failure = (outcome as ScanService.Outcome.Failed).failure
        assertTrue("应为 NoPermission，实际 $failure", failure === ScanFailure.NoPermission)
        assertTrue(failure.display().detail.contains("你的角色无此权限"))
    }

    @Test
    fun `五类失败 - 展示文案互不相同（禁止合并成扫码失败）`() {
        val displays = listOf(
            ScanFailure.NotOurs("不是本系统的码").display(),
            ScanFailure.UnknownCode("格式正确但系统内不存在").display(),
            ScanFailure.Rejected("ALREADY_FED", "该袋已投料").display(),
            ScanFailure.NoPermission.display(),
            ScanFailure.Voided.display(),
        )
        val titles = displays.map { it.title }
        assertEquals("五类标题应互不相同", titles.size, titles.toSet().size)
        displays.forEach { d ->
            assertTrue("标题不得是笼统的「扫码失败」", d.title != "扫码失败" && d.title.isNotBlank())
            assertTrue("小字错误码供排查", d.codeLine.contains("HTTP"))
        }
    }

    @Test
    fun `200 正常 - Found 且四字段齐全`() = runTest {
        val outcome = service().resolve("1BCG00010002261007030070007")
        assertTrue("实际 $outcome", outcome is ScanService.Outcome.Found)
        val info = (outcome as ScanService.Outcome.Found).info
        assertEquals("bag", info.kind)
        assertEquals("原料吨袋", info.typeLabel)
        assertEquals("1B-CG-0001-0002-261007-03-007-000-7", info.human)
        assertEquals("确认卡必须恰好四字段", 4, info.fields.size)
        assertTrue("非作废对象", !info.voided)
    }

    @Test
    fun `200 但对象已作废 - Voided`() = runTest {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                MockResponse().setResponseCode(200).setBody(voidedBody)
        }
        val outcome = service().resolve("1BCG00010002261007030070007")
        assertTrue(outcome is ScanService.Outcome.Failed)
        assertTrue(
            "状态「作废」应判 Voided，实际 $outcome",
            (outcome as ScanService.Outcome.Failed).failure is ScanFailure.Voided,
        )
    }

    @Test
    fun `解析是读操作 - 不做前置探测（只发 GET，无 HEAD healthz）`() = runTest {
        val paths = mutableListOf<String?>()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                paths += "${request.method} ${request.path}"
                return MockResponse().setResponseCode(200).setBody(successBody)
            }
        }
        service().resolve("1BCG00010002261007030070007")
        assertTrue("只允许 GET 到达，实际：$paths", paths.all { it!!.startsWith("GET ") })
        assertTrue(paths.isNotEmpty())
        assertNotEquals(null, paths.first())
    }

    @Test
    fun `服务端 5xx - 归入网络层 Server 而非五类`() = runTest {
        stub(500, """{"error":"服务器内部错误"}""")
        val outcome = service().resolve("1BCG00010002261007030070007")
        assertTrue(
            "500 应 NetFailed(Server)，实际 $outcome",
            outcome is ScanService.Outcome.NetFailed && outcome.error is ApiError.Server,
        )
    }
}
