package cn.hunanjiangxi.jxlabtrace

import cn.hunanjiangxi.jxlabtrace.log.LogLevel
import cn.hunanjiangxi.jxlabtrace.net.ApiClient
import cn.hunanjiangxi.jxlabtrace.net.ApiError
import cn.hunanjiangxi.jxlabtrace.net.ApiResult
import cn.hunanjiangxi.jxlabtrace.net.InMemorySidStore
import cn.hunanjiangxi.jxlabtrace.net.JxCookieJar
import cn.hunanjiangxi.jxlabtrace.net.JxLogger
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * M12：NetworkMonitor=false（online 门禁）时 `executeWrite` **不发任何请求**——
 * 不到 /healthz 前置探测、更不到业务路径（B1 preflight 的补充判据）。
 */
class OfflineGateTest {

    private lateinit var server: MockWebServer
    private val reachedPaths = java.util.concurrent.ConcurrentLinkedQueue<String>()
    private val logs = mutableListOf<Pair<LogLevel, String>>()

    @Before
    fun setUp() {
        reachedPaths.clear()
        logs.clear()
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                reachedPaths.add("${request.method} ${request.path}")
                return MockResponse().setResponseCode(200).setBody("""{"ok":true}""")
            }
        }
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    /** online 注入 false ⇒ 模拟 NetworkMonitor.online=false。 */
    private fun offlineClient() = ApiClient(
        baseUrl = server.url("/").toString(),
        cookieJar = JxCookieJar(InMemorySidStore()),
        logger = JxLogger { level, _, message -> logs.add(level to message) },
        online = { false },
    )

    @Test
    fun `离线 - executeWrite 返回 Offline 且零请求发出（连 healthz 都不发）`() = runTest {
        val result = offlineClient().executeWrite(
            "POST", "/api/recv/arrivals", body = """{"plate":"湘F12345"}""",
        ) { it }

        assertTrue("期望 Offline，实际 $result", result is ApiResult.Failure)
        assertTrue(
            "应为 ApiError.Offline，实际 ${(result as ApiResult.Failure).error}",
            result.error is ApiError.Offline,
        )
        assertEquals(
            "离线门禁下不允许任何请求到达（含 /healthz 前置探测），实际：$reachedPaths",
            0, reachedPaths.size,
        )
        assertTrue(
            "日志应记录离线门禁拦截，实际日志：$logs",
            logs.any { it.second.contains("全局网络态为离线") },
        )
    }

    @Test
    fun `离线 - executeRead 同样零请求`() = runTest {
        val result = offlineClient().executeRead("/api/me") { it }

        assertTrue(
            "读请求离线也应 Offline，实际 $result",
            result is ApiResult.Failure && result.error is ApiError.Offline,
        )
        assertEquals(0, reachedPaths.size)
    }

    @Test
    fun `离线 - 扫码解析（ScanService 同款直 GET）也零请求`() = runTest {
        val api = offlineClient()
        assertTrue(!api.currentlyOnline())
        // ScanService.resolve 第一步即 currentlyOnline 门禁（此处断言门禁信号；网络层零请求同上）
        assertEquals(0, reachedPaths.size)
    }

    @Test
    fun `在线 - 恢复后请求照常发出（对照组）`() = runTest {
        val api = ApiClient(
            baseUrl = server.url("/").toString(),
            cookieJar = JxCookieJar(InMemorySidStore()),
            logger = JxLogger { _, _, _ -> },
            online = { true },
        )
        val result = api.executeRead("/api/me") { it }

        assertTrue("在线应成功，实际 $result", result is ApiResult.Success)
        assertEquals(1, reachedPaths.size)
        assertEquals("GET /api/me", reachedPaths.first())
    }
}
