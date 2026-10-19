package cn.hunanjiangxi.jxlabtrace

import cn.hunanjiangxi.jxlabtrace.net.ApiClient
import cn.hunanjiangxi.jxlabtrace.net.InMemorySidStore
import cn.hunanjiangxi.jxlabtrace.net.JxCookieJar
import cn.hunanjiangxi.jxlabtrace.net.JxLogger
import cn.hunanjiangxi.jxlabtrace.net.SessionCoordinator
import cn.hunanjiangxi.jxlabtrace.prod.OperationTracker
import cn.hunanjiangxi.jxlabtrace.prod.OperationUiState
import cn.hunanjiangxi.jxlabtrace.prod.OperationViewModel
import cn.hunanjiangxi.jxlabtrace.prod.ProdRepository
import cn.hunanjiangxi.jxlabtrace.prod.ServerClock
import cn.hunanjiangxi.jxlabtrace.prod.ServerTimeSource
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * M7-5：作业段 —— 重复点「结束」幂等（第二次不发请求）。
 */
class OperationIdempotencyTest {

    private lateinit var server: MockWebServer
    private var opPostCount = 0
    private var opBodies = mutableListOf<String>()

    private val opRowJson =
        """{"id":1,"batch_id":3,"seq":1,"operator":"甲班",
            "start_at":"2026-10-20 09:00:00","end_at":"2026-10-20 10:30:00",
            "output_weight":null,"remark":""}"""

    @Before
    fun setUp() {
        server = MockWebServer()
        opPostCount = 0
        opBodies = mutableListOf()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.startsWith("/healthz") ->
                        MockResponse().setResponseCode(200)
                            .setHeader("Date", httpDate(serverNowMillis()))
                    request.method == "GET" && path.endsWith("/operations") ->
                        MockResponse().setResponseCode(200).setBody("""{"count":0,"rows":[]}""")
                    request.method == "POST" && path.endsWith("/operations") -> {
                        opPostCount++
                        opBodies += request.body.readUtf8()
                        MockResponse().setResponseCode(200)
                            .setBody("""{"created":true,"row":$opRowJson}""")
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

    private fun serverNowMillis(): Long = System.currentTimeMillis()

    private fun httpDate(millis: Long): String =
        java.time.ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(millis), ZoneId.of("GMT"))
            .format(DateTimeFormatter.RFC_1123_DATE_TIME)

    private fun vm(failPost: Boolean = false): OperationViewModel {
        val api = ApiClient(
            baseUrl = server.url("/").toString(),
            cookieJar = JxCookieJar(InMemorySidStore()),
            logger = JxLogger { _, _, _ -> },
        )
        if (failPost) {
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.path.orEmpty()
                    return when {
                        path.startsWith("/healthz") ->
                            MockResponse().setResponseCode(200)
                                .setHeader("Date", httpDate(serverNowMillis()))
                        request.method == "GET" && path.endsWith("/operations") ->
                            MockResponse().setResponseCode(200).setBody("""{"count":0,"rows":[]}""")
                        request.method == "POST" && path.endsWith("/operations") -> {
                            opPostCount++
                            MockResponse().setResponseCode(500).setBody("""{"error":"boom"}""")
                        }
                        else -> MockResponse().setResponseCode(404).setBody("{}")
                    }
                }
            }
        }
        return OperationViewModel(
            repo = ProdRepository(api),
            session = SessionCoordinator { true },
            clock = ServerTimeSource { serverNowMillis() },
            batchId = 3,
            operatorName = "甲班",
            scope = kotlinx.coroutines.CoroutineScope(Dispatchers.Unconfined),
        )
    }

    // ---- 纯逻辑 ----

    @Test
    fun `M7-5 Tracker重复结束 - 第二次不放行`() {
        val tracker = OperationTracker()
        assertTrue(tracker.onStart(1_000L))
        assertFalse("重复开始不放行", tracker.onStart(2_000L))
        assertTrue(tracker.onEnd())
        assertFalse("★ 重复点结束应幂等（第二次 false ⇒ 不发请求）", tracker.onEnd())
        assertFalse(tracker.onEnd())
    }

    @Test
    fun `M7-5 Tracker断网回滚 - 结束失败后可重试`() {
        val tracker = OperationTracker()
        tracker.onStart(1_000L)
        assertTrue(tracker.onEnd())
        tracker.onEndFailed()
        assertEquals(OperationTracker.Phase.Running, tracker.phase)
        assertTrue("回滚后重试应放行", tracker.onEnd())
    }

    @Test
    fun `M7-5 Tracker未开始不能结束`() {
        val tracker = OperationTracker()
        assertFalse(tracker.onEnd())
    }

    @Test
    fun `M7-5 reset - 下一段重新打点`() {
        val tracker = OperationTracker()
        tracker.onStart(1_000L)
        tracker.onEnd()
        tracker.reset()
        assertEquals(OperationTracker.Phase.Idle, tracker.phase)
        assertTrue(tracker.onStart(5_000L))
    }

    // ---- ViewModel ----

    @Test
    fun `M7-5 ViewModel重复点结束 - 只发一次POST`() = runBlocking {
        val viewModel = vm()
        viewModel.load()
        withTimeout(10_000) {
            viewModel.state.first { it is OperationUiState.Ready }
        }
        viewModel.start()
        withTimeout(10_000) {
            viewModel.state.first {
                (it as? OperationUiState.Ready)?.phase == OperationTracker.Phase.Running
            }
        }

        // 连点两次结束
        viewModel.end()
        viewModel.end()
        viewModel.end()

        val done = withTimeout(10_000) {
            viewModel.state.first { it is OperationUiState.Done }
        } as OperationUiState.Done
        assertEquals(1, done.operations.size)
        // ★ 幂等：三次点击只发一次 POST
        assertEquals("重复结束不得多发请求", 1, opPostCount)
    }

    @Test
    fun `M7-5 断网提交失败 - 状态保留且重试再发`() = runBlocking {
        val viewModel = vm(failPost = true)
        viewModel.load()
        withTimeout(10_000) {
            viewModel.state.first { it is OperationUiState.Ready }
        }
        viewModel.start()
        withTimeout(10_000) {
            viewModel.state.first {
                (it as? OperationUiState.Ready)?.phase == OperationTracker.Phase.Running
            }
        }
        viewModel.end()
        // 失败后回 Running（表单态保留）
        val afterFail = withTimeout(10_000) {
            viewModel.state.first {
                (it as? OperationUiState.Ready)?.phase == OperationTracker.Phase.Running
            }
        } as OperationUiState.Ready
        assertEquals(OperationTracker.Phase.Running, afterFail.phase)
        assertEquals(1, opPostCount)
        // 再点结束会再试（合法重试，不是幂等重复）
        viewModel.end()
        kotlinx.coroutines.delay(300)
        assertEquals(2, opPostCount)
    }

    @Test
    fun `M7-5 请求体 - 含start_at与end_at`() = runBlocking {
        val viewModel = vm()
        viewModel.load()
        withTimeout(10_000) { viewModel.state.first { it is OperationUiState.Ready } }
        viewModel.start()
        withTimeout(10_000) {
            viewModel.state.first {
                (it as? OperationUiState.Ready)?.phase == OperationTracker.Phase.Running
            }
        }
        viewModel.end()
        withTimeout(10_000) { viewModel.state.first { it is OperationUiState.Done } }
        val body = opBodies.first()
        assertTrue("含 start_at：$body", body.contains("\"start_at\""))
        assertTrue("含 end_at：$body", body.contains("\"end_at\""))
        assertTrue("含 operator：$body", body.contains("\"operator\":\"甲班\""))
    }

    @Test
    fun `M7-5 formatElapsed - 时分秒展示`() {
        assertEquals("00:00:00", cn.hunanjiangxi.jxlabtrace.ui.prod.formatElapsed(0))
        assertEquals("01:42:18", cn.hunanjiangxi.jxlabtrace.ui.prod.formatElapsed(6_138_000))
    }

    @Test
    fun `M7-5 toBackendDateTime - 后端时间格式`() {
        val millis = java.time.LocalDateTime.of(2026, 10, 20, 9, 40, 0)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        assertEquals("2026-10-20 09:40:00", ServerClock.toBackendDateTime(millis))
    }
}
