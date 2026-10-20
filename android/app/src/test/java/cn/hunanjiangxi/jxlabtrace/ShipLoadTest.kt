package cn.hunanjiangxi.jxlabtrace

import cn.hunanjiangxi.jxlabtrace.net.ApiClient
import cn.hunanjiangxi.jxlabtrace.net.ApiResult
import cn.hunanjiangxi.jxlabtrace.net.InMemorySidStore
import cn.hunanjiangxi.jxlabtrace.net.JxCookieJar
import cn.hunanjiangxi.jxlabtrace.net.JxLogger
import cn.hunanjiangxi.jxlabtrace.net.SessionCoordinator
import cn.hunanjiangxi.jxlabtrace.scan.CodeParser
import cn.hunanjiangxi.jxlabtrace.ship.CollectedBagSet
import cn.hunanjiangxi.jxlabtrace.ship.CreateShipmentBody
import cn.hunanjiangxi.jxlabtrace.ship.LoadUiState
import cn.hunanjiangxi.jxlabtrace.ship.LoadViewModel
import cn.hunanjiangxi.jxlabtrace.ship.ShipRepository
import cn.hunanjiangxi.jxlabtrace.ship.ShipSummary
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * M10-1：重复袋 —— 同码两次扫描 ⇒ 第二次**本地拦截**（服务端请求计数仍 1）。
 * M10-2：汇总实时 —— 归集成功后袋数/净重**本地即增**（不依赖刷新）。
 */
class ShipLoadTest {

    // 合法 27 位成品袋码（T='E'；校验位经 CodeParser.checkChar 计算并断言）
    private val bagA = "1ECG0001000226102001001000P"
    private val bagB = "1ECG0001000226102001002000S"

    private lateinit var server: MockWebServer
    private var createCount = 0
    private var addItemCount = 0
    private var departCount = 0

    private val shipmentJson =
        """{"id":1,"shipment_no":"CH261020-001","customer_id":9407,"customer_name":"岳阳观盛",
            "vehicle_id":null,"plate_no":"","driver":"","ship_at":null,"operator":"",
            "status":"已出厂","remark":"","created_at":"2026-10-20 09:00:00","created_by":"ou_x"}"""

    private val departedJson =
        """{"id":1,"shipment_no":"CH261020-001","customer_id":9407,"customer_name":"岳阳观盛",
            "vehicle_id":null,"plate_no":"湘F·A1234","driver":"张三",
            "ship_at":"2026-10-20 14:20:00","operator":"",
            "status":"已出厂","remark":"","created_at":"2026-10-20 09:00:00","created_by":"ou_x"}"""

    @Before
    fun setUp() {
        assertTrue(
            "样例码 A 校验位必须与算法一致",
            CodeParser.checkChar(bagA.dropLast(1)) == bagA.last(),
        )
        assertTrue(
            "样例码 B 校验位必须与算法一致",
            CodeParser.checkChar(bagB.dropLast(1)) == bagB.last(),
        )
        server = MockWebServer()
        resetCounts()
        server.dispatcher = defaultDispatcher()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun resetCounts() {
        createCount = 0
        addItemCount = 0
        departCount = 0
    }

    private fun defaultDispatcher() = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            val path = request.path.orEmpty()
            return when {
                path.startsWith("/healthz") -> MockResponse().setResponseCode(200)
                request.method == "POST" && path == "/api/ship/shipments" -> {
                    createCount++
                    MockResponse().setResponseCode(200)
                        .setBody("""{"created":true,"row":$shipmentJson}""")
                }
                request.method == "POST" && path.matches(Regex("/api/ship/shipments/\\d+/items")) -> {
                    addItemCount++
                    MockResponse().setResponseCode(200)
                        .setBody("""{"added":true,"row":$shipmentJson}""")
                }
                request.method == "POST" && path.matches(Regex("/api/ship/shipments/\\d+/depart")) -> {
                    departCount++
                    MockResponse().setResponseCode(200)
                        .setBody("""{"departed":true,"row":$departedJson}""")
                }
                else -> MockResponse().setResponseCode(404).setBody("""{"error":"no"}""")
            }
        }
    }

    private fun vm(weightOf: suspend (String) -> Double? = { null }): LoadViewModel {
        val api = ApiClient(
            baseUrl = server.url("/").toString(),
            cookieJar = JxCookieJar(InMemorySidStore()),
            logger = JxLogger { _, _, _ -> },
        )
        return LoadViewModel(
            repo = ShipRepository(api),
            session = SessionCoordinator { true },
            weightOf = weightOf,
            scope = kotlinx.coroutines.CoroutineScope(Dispatchers.Unconfined),
        )
    }

    // ---- 纯逻辑 ----

    @Test
    fun `M10-1 CollectedBagSet - 同码第二次返回false`() {
        val set = CollectedBagSet()
        assertTrue(set.add(bagA))
        assertFalse("第二次同码应判重复", set.add(bagA))
        assertTrue(set.contains(bagA))
        assertEquals(1, set.size)
    }

    @Test
    fun `M10-2 ShipSummary - 袋数与净重本地累加`() {
        var s = ShipSummary()
        s = s.add(bagA, 1.01)
        assertEquals(1, s.bagCount)
        assertEquals(1.01, s.netWeightTons!!, 1e-9)
        s = s.add(bagB, 1.02)
        assertEquals(2, s.bagCount)
        assertEquals(2.03, s.netWeightTons!!, 1e-9)
        assertTrue(s.collectedCodes.isNotEmpty())
    }

    @Test
    fun `M10-2 ShipSummary - 无重量源时净重为null只计袋数`() {
        val s = ShipSummary().add(bagA)
        assertEquals(1, s.bagCount)
        assertNull(s.netWeightTons)
    }

    // ---- ViewModel + MockWebServer ----

    @Test
    fun `M10-1 同码两次 - 第二次本地拦截服务端计数仍1`() = runBlocking {
        val viewModel = vm()
        var firstFailure: String? = "unset"
        viewModel.submitBag(bagA) { firstFailure = it }
        withTimeout(10_000) {
            viewModel.state.first { it is LoadUiState.Collecting }
        }
        assertEquals("首袋应成功", null, firstFailure)
        assertEquals("建单应恰好 1 次", 1, createCount)
        assertEquals("首袋走建单，items 不发", 0, addItemCount)

        // 第二次同码 —— 本地拦截，不发任何请求
        var secondFailure: String? = "unset"
        viewModel.submitBag(bagA) { secondFailure = it }
        assertEquals(
            "第二次应本地拦截并给话术",
            LoadViewModel.DUPLICATE_BAG_MESSAGE,
            secondFailure,
        )
        assertEquals("服务端建单计数仍 1（未发出第二次）", 1, createCount)
        assertEquals("服务端 items 计数仍 0", 0, addItemCount)

        // 第三码正常追加 ⇒ items=1
        var thirdFailure: String? = "unset"
        viewModel.submitBag(bagB) { thirdFailure = it }
        withTimeout(10_000) {
            viewModel.state.first {
                (it as? LoadUiState.Collecting)?.summary?.bagCount == 2
            }
        }
        assertEquals(null, thirdFailure)
        assertEquals("不同码应发 items", 1, addItemCount)
    }

    @Test
    fun `M10-2 归集成功后袋数净重本地即增 - 不依赖刷新`() = runBlocking {
        val weights = mapOf(bagA to 1.01, bagB to 1.02)
        val viewModel = vm(weightOf = { codes -> weights[codes] })
        viewModel.submitBag(bagA) {}
        val afterFirst = withTimeout(10_000) {
            viewModel.state.first { it is LoadUiState.Collecting }
        } as LoadUiState.Collecting
        assertEquals("首袋后袋数=1", 1, afterFirst.summary.bagCount)
        assertEquals("首袋后净重=1.01", 1.01, afterFirst.summary.netWeightTons!!, 1e-9)

        viewModel.submitBag(bagB) {}
        val afterSecond = withTimeout(10_000) {
            viewModel.state.first {
                (it as? LoadUiState.Collecting)?.summary?.bagCount == 2
            }
        } as LoadUiState.Collecting
        assertEquals("次袋后袋数=2（本地即增）", 2, afterSecond.summary.bagCount)
        assertEquals("次袋后净重=2.03（本地即增）", 2.03, afterSecond.summary.netWeightTons!!, 1e-9)
        // 全程只有 2 次 POST（建单1+items1），零 GET 刷新
        assertEquals("POST 总数 = 建单1 + items1", 2, createCount + addItemCount)
    }

    @Test
    fun `M10 建单请求体 - 首袋入bag_codes客户由后端推定`() = runBlocking {
        var createBody: String? = null
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.startsWith("/healthz") -> MockResponse().setResponseCode(200)
                    request.method == "POST" && path == "/api/ship/shipments" -> {
                        createBody = request.body.readUtf8()
                        MockResponse().setResponseCode(200)
                            .setBody("""{"created":true,"row":$shipmentJson}""")
                    }
                    else -> MockResponse().setResponseCode(404).setBody("{}")
                }
            }
        }
        val api = ApiClient(
            baseUrl = server.url("/").toString(),
            cookieJar = JxCookieJar(InMemorySidStore()),
            logger = JxLogger { _, _, _ -> },
        )
        val result = ShipRepository(api)
            .createShipment(CreateShipmentBody(bag_codes = listOf(bagA)))
        assertTrue("建单应成功，实际=$result", result is ApiResult.Success)
        val body = requireNotNull(createBody)
        assertTrue("body 应含 bag_codes：$body", body.contains("\"bag_codes\":[\"$bagA\"]"))
        assertTrue("customer_id 缺省 0（后端由首袋推定）", body.contains("\"customer_id\":0"))
    }

    @Test
    fun `M10 出场登记 - 必填校验与请求体`() = runBlocking {
        val viewModel = vm()
        viewModel.submitBag(bagA) {}
        withTimeout(10_000) { viewModel.state.first { it is LoadUiState.Collecting } }
        viewModel.goDepart()
        withTimeout(10_000) { viewModel.state.first { it is LoadUiState.DepartReady } }

        // 空车牌 ⇒ 拦截不发请求
        viewModel.confirmDepart("", "张三")
        val message = withTimeout(10_000) { viewModel.message.first { it != null } }.orEmpty()
        assertTrue("空车牌应提示必填：$message", message.contains("必填"))
        assertEquals("空车牌不发 depart", 0, departCount)
        viewModel.clearMessage()
        // 回到 DepartReady（confirmDepart 失败不改状态）
        assertTrue(viewModel.state.value is LoadUiState.DepartReady)

        // 正常出场——捕获请求体
        var captured: String? = null
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.startsWith("/healthz") -> MockResponse().setResponseCode(200)
                    request.method == "POST" && path.endsWith("/depart") -> {
                        captured = request.body.readUtf8()
                        departCount++
                        MockResponse().setResponseCode(200)
                            .setBody("""{"departed":true,"row":$departedJson}""")
                    }
                    else -> MockResponse().setResponseCode(404).setBody("{}")
                }
            }
        }
        viewModel.confirmDepart("湘F·A1234", "张三")
        withTimeout(10_000) { viewModel.state.first { it is LoadUiState.Departed } }
        val body = requireNotNull(captured)
        assertTrue("body 应含 plate_no：$body", body.contains("\"plate_no\":\"湘F·A1234\""))
        assertTrue("body 应含 driver", body.contains("\"driver\":\"张三\""))
        assertFalse("ship_at 留空走服务端时间", body.contains("\"ship_at\""))

        // 已出场状态展示服务端回写字段
        val departed = (viewModel.state.value as LoadUiState.Departed).shipment
        assertEquals("湘F·A1234", departed.plate_no)
        assertTrue("ship_at 应非空", !departed.ship_at.isNullOrBlank())
    }
}
