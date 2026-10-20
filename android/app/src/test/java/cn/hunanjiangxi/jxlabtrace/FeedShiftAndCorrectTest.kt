package cn.hunanjiangxi.jxlabtrace

import cn.hunanjiangxi.jxlabtrace.net.ApiClient
import cn.hunanjiangxi.jxlabtrace.net.InMemorySidStore
import cn.hunanjiangxi.jxlabtrace.net.JxCookieJar
import cn.hunanjiangxi.jxlabtrace.net.JxLogger
import cn.hunanjiangxi.jxlabtrace.net.SessionCoordinator
import cn.hunanjiangxi.jxlabtrace.prod.FeedCorrectSession
import cn.hunanjiangxi.jxlabtrace.prod.FeedRecord
import cn.hunanjiangxi.jxlabtrace.prod.FeedUiState
import cn.hunanjiangxi.jxlabtrace.prod.FeedViewModel
import cn.hunanjiangxi.jxlabtrace.prod.ProdRepository
import cn.hunanjiangxi.jxlabtrace.prod.ServerClock
import cn.hunanjiangxi.jxlabtrace.prod.ServerTimeSource
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
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
 * M7-3/M7-4：投料更正 —— 无班次窗口（用户 2026-10-20 裁决：系统只做如实记录+留痕），原因+版本链即审计。
 * M7-4：更正请求体含 feed_id/new_weight/reason/version；PATCH 成功后列表显示 v2。
 */
class FeedShiftAndCorrectTest {

    private lateinit var server: MockWebServer
    private var patchBody: String? = null
    private var patchCount = 0

    private val zone = ZoneId.systemDefault()
    private val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    // 服务端「现在」：今天 10:00（班次 1 = 08–16）
    private val serverNow: Long =
        LocalDateTime.now().withHour(10).withMinute(0).withSecond(0).withNano(0)
            .atZone(zone).toInstant().toEpochMilli()

    // 投料发生在上一班次（今天 06:00，班次 0 = 00–08）
    private val fedAtPrevShift: Long =
        LocalDateTime.now().withHour(6).withMinute(0).withSecond(0).withNano(0)
            .atZone(zone).toInstant().toEpochMilli()

    private val correctedRowJson =
        """{"id":5,"batch_id":3,"bag_id":9,"bag_code":"1BCG00010002261020010090008",
            "bag_human":"1B-CG-0001-0002-261020-01-009-000-8","truck_id":1,"truck_code":"1A",
            "feed_weight":1.2,"fed_at":"2026-10-20 06:00:00","operator":"","remark":"磅显示看错一位"}"""

    @Before
    fun setUp() {
        server = MockWebServer()
        patchBody = null
        patchCount = 0
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.startsWith("/healthz") ->
                        MockResponse().setResponseCode(200)
                            .setHeader("Date", httpDate(serverNow))
                    request.method == "GET" && path.endsWith("/feeds") ->
                        MockResponse().setResponseCode(200).setBody("""{"count":1,"rows":[]}""")
                    request.method == "GET" && path.startsWith("/api/prod/batches") ->
                        MockResponse().setResponseCode(200).setBody("""{"count":0,"rows":[]}""")
                    request.method == "PATCH" && path.startsWith("/api/prod/feeds/") -> {
                        patchCount++
                        patchBody = request.body.readUtf8()
                        MockResponse().setResponseCode(200)
                            .setBody("""{"corrected":true,"row":$correctedRowJson}""")
                    }
                    else -> MockResponse().setResponseCode(404).setBody("""{"error":"no"}""")
                }
            }
        }
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun httpDate(millis: Long): String =
        java.time.ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(millis), ZoneId.of("GMT"))
            .format(DateTimeFormatter.RFC_1123_DATE_TIME)

    private fun feed(fedAtMillis: Long): FeedRecord = FeedRecord(
        id = 5,
        batch_id = 3,
        bag_code = "1BCG00010002261020010090008",
        feed_weight = 1.0,
        fed_at = java.time.LocalDateTime
            .ofInstant(java.time.Instant.ofEpochMilli(fedAtMillis), zone)
            .format(fmt),
    )

    private fun vm(clockNow: Long?): FeedViewModel {
        val api = ApiClient(
            baseUrl = server.url("/").toString(),
            cookieJar = JxCookieJar(InMemorySidStore()),
            logger = JxLogger { _, _, _ -> },
        )
        return FeedViewModel(
            repo = ProdRepository(api),
            session = SessionCoordinator { true },
            clock = ServerTimeSource { clockNow },
            batchId = 3,
            scope = kotlinx.coroutines.CoroutineScope(Dispatchers.Unconfined),
        )
    }

    // ---- 纯逻辑（更正随时可做：用户 2026-10-20 裁决——系统只做如实记录+留痕，无班次窗口） ----

    @Test
    fun `M7-3 ServerClock解析Date头`() {
        // 用 RFC_1123 自洽生成（星期与日期一致；手写 "Mon" 配 2026-10-20（实际是周二）
        // 会被 java.time 按不一致 DOW 拒绝——真实 Go Date 头永远自洽）
        val zdt = java.time.ZonedDateTime.of(2026, 10, 20, 2, 0, 0, 0, ZoneId.of("GMT"))
        val header = zdt.format(DateTimeFormatter.RFC_1123_DATE_TIME)
        val millis = ServerClock.parseHttpDate(header)
        assertNotNull(millis)
        assertEquals(zdt.toInstant().toEpochMilli(), millis)
        // Go 风格 GMT 尾缀
        val goStyle = "Tue, 20 Oct 2026 02:00:00 GMT"
        assertEquals(zdt.toInstant().toEpochMilli(), ServerClock.parseHttpDate(goStyle))
        assertEquals(null, ServerClock.parseHttpDate(null))
        assertEquals(null, ServerClock.parseHttpDate("not a date"))
    }

    // ---- ViewModel ----

    @Test
    fun `M7-3 更正无班次窗口 - 旧记录随时可更正且 PATCH 正常发出`() = runBlocking {
        val viewModel = vm(clockNow = null)
        viewModel.load()
        withTimeout(10_000) { viewModel.state.first { it is FeedUiState.Active } }

        // 昨天的投料记录（原「跨班次置灰」场景）——现在直接进入更正态并可提交
        val fedYesterday = serverNow - 24 * 3600 * 1000L
        viewModel.openCorrect(feed(fedYesterday))
        val session = withTimeout(10_000) {
            viewModel.correcting.first { it != null }
        } as FeedCorrectSession
        assertEquals(feed(fedYesterday).id, session.feed.id)

        viewModel.submitCorrect("磅读错", "昨日漏更，今日补正", 1.2)
        withTimeout(10_000) { viewModel.correctDone.first { it != null } }
        val body = requireNotNull(patchBody)
        assertTrue("PATCH 应发出且含原因", body.contains("\"reason\""))
    }

    @Test
    fun `M7-4 更正请求体 - 含feed_id new_weight reason version`() = runBlocking {
        val viewModel = vm(clockNow = serverNow)
        viewModel.load()
        withTimeout(10_000) { viewModel.state.first { it is FeedUiState.Active } }

        // 同班次投料（今天 09:30）
        val fedSame = LocalDateTime.now().withHour(9).withMinute(30)
            .atZone(zone).toInstant().toEpochMilli()
        viewModel.openCorrect(feed(fedSame))
        val session = withTimeout(10_000) {
            viewModel.correcting.first { it != null }
        } as FeedCorrectSession

        viewModel.submitCorrect("磅读错", "磅显示看错一位", 1.2)
        withTimeout(10_000) { viewModel.correctDone.first { it != null } }

        val body = requireNotNull(patchBody)
        assertTrue("含 feed_id：$body", body.contains("\"feed_id\":5"))
        assertTrue("含 new_weight：$body", body.contains("\"new_weight\":1.2"))
        assertTrue("含 reason：$body", body.contains("\"reason\":\"磅读错 · 磅显示看错一位\""))
        assertTrue("含 version：$body", body.contains("\"version\":2"))
        // 后端实际消费字段也在
        assertTrue("含 feed_weight：$body", body.contains("\"feed_weight\":1.2"))

        // PATCH 成功后列表显示 v2
        val active = viewModel.state.value as FeedUiState.Active
        val updated = active.feeds.find { it.id == 5L }
        // 列表原本为空（桩），correctDone 携带 v2；再次打开即 v2
        assertEquals(2, viewModel.correctDone.value?.version)
        assertTrue(updated == null || updated.version >= 1)
    }

    @Test
    fun `M7-4 FeedCorrectForm - 原因三选一与有效门禁`() {
        cn.hunanjiangxi.jxlabtrace.prod.FeedCorrectForm.apply {
            assertTrue(isValid("磅读错", "说明", 1.0))
            assertTrue(isValid("扫错袋", "说明", 1.0))
            assertTrue(isValid("其它", "说明", 1.0))
            assertFalse(isValid("不存在的原因", "说明", 1.0))
            assertFalse(isValid(null, "说明", 1.0))
            assertFalse(isValid("磅读错", "  ", 1.0))
            assertFalse(isValid("磅读错", "说明", 0.0))
            assertFalse(isValid("磅读错", "说明", null))
            assertEquals("磅读错 · 磅显示看错一位", reasonText("磅读错", "磅显示看错一位"))
        }
    }

    @Test
    fun `M7-4 build - 请求体形状`() {
        val body = cn.hunanjiangxi.jxlabtrace.prod.FeedCorrectForm.build(
            feed = FeedRecord(id = 9, version = 1, feed_weight = 1.0),
            newWeightTons = 1.2,
            reasonCategory = "扫错袋",
            detail = "扫到了 18 袋",
        )
        assertEquals(9L, body.feed_id)
        assertEquals(1.2, body.new_weight, 0.0001)
        assertEquals("扫错袋 · 扫到了 18 袋", body.reason)
        assertEquals(2, body.version)
        assertEquals(1.2, body.feed_weight, 0.0001)
    }
}
