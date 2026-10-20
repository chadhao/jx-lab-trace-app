package cn.hunanjiangxi.jxlabtrace

import cn.hunanjiangxi.jxlabtrace.net.ApiClient
import cn.hunanjiangxi.jxlabtrace.net.InMemorySidStore
import cn.hunanjiangxi.jxlabtrace.net.JxCookieJar
import cn.hunanjiangxi.jxlabtrace.net.JxLogger
import cn.hunanjiangxi.jxlabtrace.net.SessionCoordinator
import cn.hunanjiangxi.jxlabtrace.prod.FeedProgress
import cn.hunanjiangxi.jxlabtrace.prod.FeedUiState
import cn.hunanjiangxi.jxlabtrace.prod.FeedViewModel
import cn.hunanjiangxi.jxlabtrace.prod.ProdRepository
import cn.hunanjiangxi.jxlabtrace.prod.ServerTimeSource
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * M7-2：投料进度 —— 扫码确认后本地即时 +1，不依赖刷新（ViewModel 单测）。
 */
class FeedProgressViewModelTest {

    private lateinit var server: MockWebServer
    private var feedPostCount = 0

    private val batchJson =
        """{"id":3,"code":"1CCG9407940826102003000000","human":"1C-CG-9407-9408-261020-03-000-000-0",
            "customer_id":9407,"input_material_id":11,"planned_output_material_id":12,
            "batch_date":"2026-10-20","status":"进行中","remark":"","created_by":"ou_x"}"""

    private val feedRowJson =
        """{"id":20,"batch_id":3,"bag_id":99,"bag_code":"1BCG00010002261020010990008",
            "bag_human":"1B-CG-0001-0002-261020-01-099-000-8","truck_id":1,"truck_code":"1A",
            "feed_weight":null,"fed_at":"2026-10-20 10:22:41","operator":"","remark":""}"""

    @Before
    fun setUp() {
        server = MockWebServer()
        feedPostCount = 0
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.startsWith("/healthz") -> MockResponse().setResponseCode(200)
                    request.method == "GET" && path.endsWith("/feeds") ->
                        MockResponse().setResponseCode(200).setBody("""{"count":19,"rows":[]}""")
                    request.method == "GET" && path.startsWith("/api/prod/batches") ->
                        MockResponse().setResponseCode(200).setBody("""{"count":1,"rows":[$batchJson]}""")
                    request.method == "POST" && path.endsWith("/feeds") -> {
                        feedPostCount++
                        MockResponse().setResponseCode(200)
                            .setBody("""{"fed":true,"row":$feedRowJson}""")
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

    private fun vm(): FeedViewModel {
        val api = ApiClient(
            baseUrl = server.url("/").toString(),
            cookieJar = JxCookieJar(InMemorySidStore()),
            logger = JxLogger { _, _, _ -> },
        )
        return FeedViewModel(
            repo = ProdRepository(api),
            session = SessionCoordinator { true },
            clock = ServerTimeSource { 0L },
            batchId = 3,
            scope = kotlinx.coroutines.CoroutineScope(Dispatchers.Unconfined),
        )
    }

    @Test
    fun `M7-2 纯逻辑 - afterConfirm加一`() {
        assertEquals(20, FeedProgress.afterConfirm(19))
        assertEquals(1, FeedProgress.afterConfirm(0))
    }

    @Test
    fun `M7-2 ViewModel扫码确认成功 - 本地即时加一不依赖刷新`() = runBlocking {
        val viewModel = vm()
        viewModel.load()
        withTimeout(10_000) { viewModel.state.first { it is FeedUiState.Active } }
        val before = (viewModel.state.value as FeedUiState.Active).fedCount
        // 桩 rows 为空 ⇒ 初始 0；判据核心是「确认后不等刷新即时 +1」
        assertEquals(0, before)

        var failure: String? = "unset"
        viewModel.submitBag("1BCG00010002261020010990008") { f -> failure = f }

        // 不等待任何 refresh：本地状态即时 +1
        val after = withTimeout(10_000) {
            viewModel.state.first { (it as? FeedUiState.Active)?.fedCount == before + 1 }
        } as FeedUiState.Active
        assertEquals(1, after.fedCount)
        // 回调与状态更新分属两步，等待回调落地（10s 超时 ⇒ 真问题）
        withTimeout(10_000) { while (failure == "unset") kotlinx.coroutines.delay(50) }
        assertEquals("成功回调应传 null", null, failure)
        assertEquals(1, feedPostCount)
        // 新记录插入列表头部（不依赖再次 GET feeds）
        assertEquals(20L, after.feeds.first().id)
        assertEquals(1, after.feeds.size)
    }
}
