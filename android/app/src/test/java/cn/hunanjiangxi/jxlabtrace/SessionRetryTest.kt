package cn.hunanjiangxi.jxlabtrace

import cn.hunanjiangxi.jxlabtrace.net.ApiClient
import cn.hunanjiangxi.jxlabtrace.net.ApiError
import cn.hunanjiangxi.jxlabtrace.net.ApiResult
import cn.hunanjiangxi.jxlabtrace.net.ErrorMessages
import cn.hunanjiangxi.jxlabtrace.net.InMemorySidStore
import cn.hunanjiangxi.jxlabtrace.net.JxCookieJar
import cn.hunanjiangxi.jxlabtrace.net.JxLogger
import cn.hunanjiangxi.jxlabtrace.net.LogLevel
import cn.hunanjiangxi.jxlabtrace.net.SessionCoordinator
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
 * M3-3：首次 401 → 重授权成功 → 重放成功（MockWebServer：401 后 200）。
 * M3-4：403 → ApiError.Forbidden → 话术「你的角色无此权限，请联系综合运营部」。
 */
class SessionRetryTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun client() = ApiClient(
        baseUrl = server.url("/").toString(),
        cookieJar = JxCookieJar(InMemorySidStore()),
        logger = JxLogger { _, _, _ -> },
    )

    @Test
    fun `首次 401 - 重授权成功 - 重放拿到 200`() = runTest {
        val calls = java.util.concurrent.atomic.AtomicInteger(0)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                if (calls.incrementAndGet() == 1) {
                    MockResponse().setResponseCode(401).setBody("""{"error":"未登录"}""")
                } else {
                    MockResponse().setResponseCode(200).setBody("""{"name":"刘川"}""")
                }
        }

        var reauthCount = 0
        val session = SessionCoordinator(
            reauthorize = {
                reauthCount++
                true // 全屏 WebView 重授权成功
            },
        )

        val result = session.withSessionRetry {
            client().executeRead("/api/me") { it }
        }

        assertTrue("重放后应成功，实际 $result", result is ApiResult.Success)
        assertEquals("重授权应恰好触发一次", 1, reauthCount)
        assertEquals("401 一次 + 重放 200 一次 = 2 个请求", 2, calls.get())
        assertEquals("""{"name":"刘川"}""", (result as ApiResult.Success).data)
    }

    @Test
    fun `首次 401 - 重授权失败 - 原样返回 Unauthorized 不重放`() = runTest {
        val calls = java.util.concurrent.atomic.AtomicInteger(0)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                calls.incrementAndGet()
                return MockResponse().setResponseCode(401).setBody("""{"error":"未登录"}""")
            }
        }
        val session = SessionCoordinator(reauthorize = { false })

        val result = session.withSessionRetry { client().executeRead("/api/me") { it } }

        assertTrue(
            "重授权失败应返回 Unauthorized",
            result is ApiResult.Failure && result.error is ApiError.Unauthorized,
        )
        assertEquals("不得重放", 1, calls.get())
    }

    @Test
    fun `非 401 错误 - 不触发重授权`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(500).setBody("""{"error":"服务器内部错误"}"""),
        )
        var reauthCount = 0
        val session = SessionCoordinator(reauthorize = { reauthCount++; true })

        val result = session.withSessionRetry { client().executeRead("/api/me") { it } }

        assertTrue(result is ApiResult.Failure && result.error is ApiError.Server)
        assertEquals(0, reauthCount)
    }

    @Test
    fun `M3-4 - 403 映射 Forbidden 且话术为规定的越权文案`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(403).setBody("""{"error":"forbidden"}"""),
        )

        val result = client().executeRead("/api/me") { it }

        assertTrue(
            "403 应映射 ApiError.Forbidden，实际 $result",
            result is ApiResult.Failure && result.error is ApiError.Forbidden,
        )
        val message = (result as ApiResult.Failure).error.userMessage
        assertTrue(
            "话术应为「你的角色无此权限，请联系综合运营部」，实际：$message",
            message.contains("你的角色无此权限，请联系综合运营部"),
        )
        // M12 ErrorMessages 同源口径
        assertEquals(message, ErrorMessages.of(result.error))
        // 403 不触发重授权（不是会话问题）
        var reauthCount = 0
        val session = SessionCoordinator(reauthorize = { reauthCount++; true })
        session.withSessionRetry { result }
        assertEquals(0, reauthCount)
    }
}
