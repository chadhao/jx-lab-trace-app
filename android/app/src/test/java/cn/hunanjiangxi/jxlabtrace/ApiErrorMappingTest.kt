package cn.hunanjiangxi.jxlabtrace

import cn.hunanjiangxi.jxlabtrace.log.LogLevel
import cn.hunanjiangxi.jxlabtrace.net.ApiClient
import cn.hunanjiangxi.jxlabtrace.net.ApiError
import cn.hunanjiangxi.jxlabtrace.net.ApiResult
import cn.hunanjiangxi.jxlabtrace.net.InMemorySidStore
import cn.hunanjiangxi.jxlabtrace.net.JxCookieJar
import cn.hunanjiangxi.jxlabtrace.net.JxLogger
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * J3：401 / 403 / 500 / Biz / Parse 五类均可解析为含中文 userMessage 的领域错误。
 */
class ApiErrorMappingTest {

    private lateinit var server: MockWebServer
    private val logs = mutableListOf<Pair<LogLevel, String>>()

    @Before
    fun setUp() {
        server = MockWebServer()
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

    private fun MockWebServer.enqueueJson(code: Int, body: String) {
        // 第一条响应给前置探测 HEAD /healthz（须 2xx 放行），第二条给真实写请求
        enqueue(MockResponse().setResponseCode(200))
        enqueue(MockResponse().setResponseCode(code).setBody(body))
    }

    private suspend fun post(path: String = "/api/demo"): ApiResult<String> =
        client().executeWrite("POST", path, body = "{}", parse = { it })

    @Test
    fun `401 映射为 Unauthorized 且 userMessage 非空`() = runTest {
        server.enqueueJson(401, """{"message":"unauthorized"}""")
        val result = post()
        assertTrue(result is ApiResult.Failure)
        val error = (result as ApiResult.Failure).error
        assertTrue("期望 Unauthorized，实际 $error", error is ApiError.Unauthorized)
        assertTrue(error.userMessage.isNotBlank())
    }

    @Test
    fun `403 映射为 Forbidden 且话术为越权引导`() = runTest {
        server.enqueueJson(403, """{"message":"forbidden"}""")
        val result = post()
        assertTrue(result is ApiResult.Failure)
        val error = (result as ApiResult.Failure).error
        assertTrue("期望 Forbidden，实际 $error", error is ApiError.Forbidden)
        assertTrue("403 话术应引导联系综合运营部：${error.userMessage}",
            error.userMessage.contains("综合运营部"))
    }

    @Test
    fun `500 映射为 Server 且保留状态码`() = runTest {
        server.enqueueJson(500, """{"message":"boom"}""")
        val result = post()
        assertTrue(result is ApiResult.Failure)
        val error = (result as ApiResult.Failure).error
        assertTrue("期望 Server，实际 $error", error is ApiError.Server)
        assertEquals(500, (error as ApiError.Server).httpCode)
        assertTrue(error.userMessage.isNotBlank())
    }

    @Test
    fun `业务错误码映射为 Biz 并透传 message`() = runTest {
        server.enqueueJson(200, """{"code":40901,"message":"该袋已在本次出货单中"}""")
        val result = post()
        assertTrue(result is ApiResult.Failure)
        val error = (result as ApiResult.Failure).error
        assertTrue("期望 Biz，实际 $error", error is ApiError.Biz)
        error as ApiError.Biz
        assertEquals("40901", error.code)
        assertEquals("该袋已在本次出货单中", error.message)
        assertEquals("该袋已在本次出货单中", error.userMessage)
    }

    @Test
    fun `200 但响应体不可解析映射为 Parse`() = runTest {
        server.enqueueJson(200, "<<<not-json>>>")
        val result = post()
        assertTrue(result is ApiResult.Failure)
        val error = (result as ApiResult.Failure).error
        println("诊断：Parse 用例实际 error = $error")
        assertTrue("期望 Parse，实际 $error", error is ApiError.Parse)
        assertTrue(error.userMessage.isNotBlank())
    }

    @Test
    fun `成功路径 code=0 继续解析 data`() = runTest {
        server.enqueueJson(200, """{"code":0,"message":"ok","data":"hello"}""")
        val result = client().executeWrite("POST", "/api/demo", body = "{}", parse = { it })
        assertTrue("期望 Success，实际 $result", result is ApiResult.Success)
    }
}
