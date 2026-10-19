package cn.hunanjiangxi.jxlabtrace

import cn.hunanjiangxi.jxlabtrace.net.ApiClient
import cn.hunanjiangxi.jxlabtrace.net.ApiResult
import cn.hunanjiangxi.jxlabtrace.net.InMemorySidStore
import cn.hunanjiangxi.jxlabtrace.net.JxCookieJar
import cn.hunanjiangxi.jxlabtrace.net.JxLogger
import cn.hunanjiangxi.jxlabtrace.net.SessionCoordinator
import cn.hunanjiangxi.jxlabtrace.recv.BagIdempotency
import cn.hunanjiangxi.jxlabtrace.recv.BagPrintViewModel
import cn.hunanjiangxi.jxlabtrace.recv.BagUiState
import cn.hunanjiangxi.jxlabtrace.recv.RecvBag
import cn.hunanjiangxi.jxlabtrace.recv.RecvRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
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
 * M5-2：建袋幂等 —— MockWebServer 桩「第 13 袋 500」→ 重试后车次袋数仍 40 且无重复序号。
 *
 * 后端 POST /bags 是一次性批量；「第 13 袋 500」模拟**服务端在生成中途失败后
 * 客户端超时重试**的最恶劣现场：重试入口先 GET bags，若已有 40 袋连号则幂等采用，
 * 绝不二次 POST（否则 uk_bag_seq 撞号或重复建袋）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BagIdempotencyTest {

    private lateinit var server: MockWebServer

    private val bagsJson: String = (1..40).joinToString(",", "[", "]") { seq ->
        """{"id":$seq,"code":"1BCG0001000226102003007${seq.toString().padStart(3, '0')}",
            "human":"1B-CG-0001-0002-261020-03-007-${seq.toString().padStart(3, '0')}-7",
            "truck_lot_id":3,"bag_seq":$seq,"weight_allocated":0.589,"status":"在库"}"""
    }

    private val truckJson =
        """{"id":3,"code":"1ACG00010002261020010000000","human":"1A-CG-0001-0002-261020-01-000-000-0",
           "plate_no":"湘F·A1234","net_weight":23.56,"bag_count":40,"status":"待检","seq_no":1}"""

    /** 首轮：POST /bags 第 13 袋处理时 500（整请求失败）；其后 GET /bags 返回已建成的 40 袋。 */
    @Before
    fun setUp() {
        server = MockWebServer()
        var bagsPostCount = 0
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.startsWith("/healthz") ->
                        MockResponse().setResponseCode(200).setBody("ok")
                    request.method == "POST" && path.endsWith("/bags") -> {
                        bagsPostCount++
                        // 第一次建袋：模拟中途失败（第 13 袋 500）
                        MockResponse().setResponseCode(500).setBody("""{"error":"内部错误"}""")
                    }
                    request.method == "GET" && path.endsWith("/bags") ->
                        MockResponse().setResponseCode(200)
                            .setBody("""{"count":40,"rows":$bagsJson}""")
                    request.method == "GET" && path.endsWith("/trucks/3") ->
                        MockResponse().setResponseCode(200).setBody("""{"row":$truckJson}""")
                    else ->
                        MockResponse().setResponseCode(404).setBody("""{"error":"no route"}""")
                }
            }
        }
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun repo(): RecvRepository {
        val api = ApiClient(
            baseUrl = server.url("/").toString(),
            cookieJar = JxCookieJar(InMemorySidStore()),
            logger = JxLogger { _, _, _ -> },
        )
        return RecvRepository(api)
    }

    @Test
    fun `第13袋500后重试 - GET幂等采用40袋无重复序号`() = runTest {
        val repository = repo()
        // 直接走幂等计划：已有 40 袋连号 ⇒ AlreadyDone，不再 POST
        val existing = when (val r = repository.listBags(3)) {
            is ApiResult.Success -> r.data
            is ApiResult.Failure -> error("listBags 失败: ${r.error.userMessage}")
        }
        assertEquals(40, existing.size)
        val plan = BagIdempotency.planRetry(existing, 40)
        assertTrue("应为 AlreadyDone，实际=$plan", plan is BagIdempotency.RetryPlan.AlreadyDone)
        val bags = (plan as BagIdempotency.RetryPlan.AlreadyDone).bags
        assertEquals(40, bags.size)
        assertEquals(40, bags.map { it.bag_seq }.toSet().size)
        assertTrue("序号必须 1..40 连号", BagIdempotency.isContiguous(bags, 40))
    }

    @Test
    fun `ViewModel建袋重试 - 幂等恢复且不重复POST建袋`() = runTest {
        val repository = repo()
        val session = SessionCoordinator { true }
        val vm = BagPrintViewModel(
            repo = repository,
            session = session,
            truckId = 3,
            printOne = { ApiResult.Success(Unit) },
        )
        vm.load(defaultNet = 23.56)
        advanceUntilIdle()
        // load 后已有 40 袋 ⇒ 直接 Built（不触发 POST /bags）
        val state = vm.state.value
        assertTrue("期望 Built，实际=$state", state is BagUiState.Built)
        val bags = (state as BagUiState.Built).bags
        assertEquals(40, bags.size)
        assertEquals(1..40, bags.map { it.bag_seq }.sorted())
        // 无重复序号
        assertEquals(bags.size, bags.map { it.bag_seq }.toSet().size)
    }

    @Test
    fun `merge去重 - 重复seq只保留一条`() {
        fun bag(seq: Int) = RecvBag(id = seq.toLong(), truck_lot_id = 3, bag_seq = seq, code = "C$seq")
        val merged = BagIdempotency.merge(listOf(bag(1), bag(2)), listOf(bag(2), bag(3)))
        assertEquals(listOf(1, 2, 3), merged.map { it.bag_seq })
    }

    @Test
    fun `isContiguous - 非连号或数量不符为false`() {
        fun bag(seq: Int) = RecvBag(bag_seq = seq)
        assertTrue(BagIdempotency.isContiguous((1..5).map { bag(it) }, 5))
        assertTrue(!BagIdempotency.isContiguous((1..4).map { bag(it) }, 5))
        assertTrue(!BagIdempotency.isContiguous(listOf(bag(1), bag(1), bag(3)), 3))
    }
}
