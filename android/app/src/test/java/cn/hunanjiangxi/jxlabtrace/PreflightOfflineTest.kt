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
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * J2：断网（模拟）发起请求 ⇒ 返回 Offline 且日志可见前置探测拦截，无 OkHttp 超时等待。
 *
 * 模拟断网：/healthz 连接直接掐断（DISCONNECT_AT_START），
 * 真实写路径始终可用——若客户端绕过前置探测，测试会看到真实请求到达。
 */
class PreflightOfflineTest {

    private lateinit var server: MockWebServer
    private val logs = mutableListOf<Pair<LogLevel, String>>()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return if (request.path?.startsWith("/healthz") == true) {
                    MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START)
                } else {
                    MockResponse().setResponseCode(200).setBody("""{"ok":true}""")
                }
            }
        }
        server.start()
        logs.clear()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun client() = ApiClient(
        baseUrl = server.url("/").toString(),
        cookieJar = JxCookieJar(InMemorySidStore()),
        logger = JxLogger { level, _, message -> logs.add(level to message) },
    )

    @Test
    fun `断网时写请求返回 Offline 且真实请求未发出`() = runTest {
        val api = client()

        val startNs = System.nanoTime()
        val result = api.executeWrite("POST", "/api/recv/arrivals", body = """{"plate":"湘F12345"}""", parse = { it })
        val elapsedMs = (System.nanoTime() - startNs) / 1_000_000

        assertTrue("期望 Failure，实际 $result", result is ApiResult.Failure)
        val error = (result as ApiResult.Failure).error
        assertTrue("期望 ApiError.Offline，实际 $error", error is ApiError.Offline)
        assertTrue("Offline 必须有中文 userMessage", error.userMessage.isNotBlank())

        // 无 OkHttp 超时等待：主线客户端超时为 10s，前置探测硬顶 2s，总耗时应远低于 10s
        assertTrue("耗时 ${elapsedMs}ms 应 < 5000ms（说明未进入真实请求的 10s 超时等待）", elapsedMs < 5_000)

        // 真实写请求一次都未到达（只可能有 /healthz 探测）
        val realRequests = server.requestQueue.filter { it.path?.startsWith("/healthz") != true }
        assertTrue("不应有任何真实写请求到达服务端，实际：${realRequests.map { it.path }}", realRequests.isEmpty())

        // 日志可见前置探测拦截
        val intercepted = logs.any { it.second.contains("前置探测拦截") }
        assertTrue("日志应包含前置探测拦截记录，实际日志：$logs", intercepted)
    }

    @Test
    fun `连接被拒绝时同样返回 Offline 而非抛异常`() = runTest {
        val dead = MockWebServer().apply { start() }
        val deadUrl = dead.url("/")
        dead.shutdown()

        val api = ApiClient(
            baseUrl = deadUrl.toString(),
            cookieJar = JxCookieJar(InMemorySidStore()),
            logger = JxLogger { level, _, message -> logs.add(level to message) },
        )
        val result = api.executeWrite("POST", "/api/prod/batches", body = "{}", parse = { it })
        assertTrue("期望 Failure(Offline)，实际 $result",
            result is ApiResult.Failure && result.error is ApiError.Offline)
    }
}
