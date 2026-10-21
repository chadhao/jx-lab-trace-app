package cn.hunanjiangxi.jxlabtrace

import cn.hunanjiangxi.jxlabtrace.auth.FeishuAuthRepo
import cn.hunanjiangxi.jxlabtrace.net.ApiClient
import cn.hunanjiangxi.jxlabtrace.net.InMemorySidStore
import cn.hunanjiangxi.jxlabtrace.net.JxCookieJar
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * MIMO-TASK-LARKSSO 判据 S-2：exchange 单测（MockWebServer 桩）。
 * 链路口径：POST /api/auth/feishu/exchange {code,state} ⇒ 2xx 换会话（Set-Cookie 落 CookieJar）／
 * 401 透出后端 error 话术／离线不发请求。
 */
class FeishuAuthRepoTest {

    private lateinit var server: MockWebServer
    private lateinit var sidStore: InMemorySidStore

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        sidStore = InMemorySidStore()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun api(online: () -> Boolean = { true }) = ApiClient(
        baseUrl = server.url("/").toString(),
        cookieJar = JxCookieJar(sidStore),
        online = online,
    )

    @Test
    fun `2xx 返回 Ok 且请求体为 code-state 且 Set-Cookie 落入 CookieJar`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"ok":true}""")
                .addHeader("Set-Cookie", "jx_sid=sid-abc; Path=/; HttpOnly"),
        )

        val outcome = FeishuAuthRepo(api()).exchange("code-1", "state-1")

        assertTrue("实际 $outcome", outcome is FeishuAuthRepo.ExchangeOutcome.Ok)
        assertEquals("sid-abc", sidStore.read())

        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/api/auth/feishu/exchange", recorded.path)
        // REQ-05：序列化含 source=sdk（后端据此走 passport 移动端契约）
        assertEquals(
            """{"code":"code-1","state":"state-1","source":"sdk"}""",
            recorded.body.readUtf8(),
        )
        assertEquals("application/json; charset=utf-8", recorded.getHeader("Content-Type"))
    }

    @Test
    fun `401 透出后端 error 话术到 Rejected`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(401).setBody("""{"error":"授权码无效或已过期"}"""),
        )

        val outcome = FeishuAuthRepo(api()).exchange("bad-code", "state-1")

        assertTrue("实际 $outcome", outcome is FeishuAuthRepo.ExchangeOutcome.Rejected)
        assertEquals(
            "授权码无效或已过期",
            (outcome as FeishuAuthRepo.ExchangeOutcome.Rejected).message,
        )
        assertEquals(null, sidStore.read())
    }

    @Test
    fun `403 角色未映射 透出后端 error 话术`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(403).setBody("""{"error":"账号未分配角色"}"""),
        )

        val outcome = FeishuAuthRepo(api()).exchange("code-1", "state-1")

        assertTrue(outcome is FeishuAuthRepo.ExchangeOutcome.Rejected)
        assertEquals(
            "账号未分配角色",
            (outcome as FeishuAuthRepo.ExchangeOutcome.Rejected).message,
        )
    }

    @Test
    fun `5xx 归为 Rejected 且文案带 HTTP 状态`() = runTest {
        server.enqueue(MockResponse().setResponseCode(502).setBody(""))

        val outcome = FeishuAuthRepo(api()).exchange("code-1", "state-1")

        assertTrue(outcome is FeishuAuthRepo.ExchangeOutcome.Rejected)
        assertTrue(
            "实际 ${(outcome as FeishuAuthRepo.ExchangeOutcome.Rejected).message}",
            outcome.message.contains("HTTP 502"),
        )
    }

    @Test
    fun `全局离线 返回 Offline 且不发请求`() = runTest {
        val outcome = FeishuAuthRepo(api(online = { false })).exchange("code-1", "state-1")

        assertTrue("实际 $outcome", outcome is FeishuAuthRepo.ExchangeOutcome.Offline)
        assertEquals(0, server.requestCount)
    }
}
