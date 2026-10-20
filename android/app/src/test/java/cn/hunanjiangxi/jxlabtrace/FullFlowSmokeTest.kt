package cn.hunanjiangxi.jxlabtrace

import cn.hunanjiangxi.jxlabtrace.auth.InMemoryMeStore
import cn.hunanjiangxi.jxlabtrace.auth.MeRepository
import cn.hunanjiangxi.jxlabtrace.net.ApiClient
import cn.hunanjiangxi.jxlabtrace.net.ApiResult
import cn.hunanjiangxi.jxlabtrace.net.InMemorySidStore
import cn.hunanjiangxi.jxlabtrace.net.JxCookieJar
import cn.hunanjiangxi.jxlabtrace.net.JxLogger
import cn.hunanjiangxi.jxlabtrace.prod.CreateBatchBody
import cn.hunanjiangxi.jxlabtrace.prod.ProdRepository
import cn.hunanjiangxi.jxlabtrace.recv.RecvRepository
import cn.hunanjiangxi.jxlabtrace.sample.SampleForm
import cn.hunanjiangxi.jxlabtrace.sample.SampleRepository
import cn.hunanjiangxi.jxlabtrace.sample.SampleTargetType
import cn.hunanjiangxi.jxlabtrace.ship.CreateShipmentBody
import cn.hunanjiangxi.jxlabtrace.ship.DepartBody
import cn.hunanjiangxi.jxlabtrace.ship.ShipRepository
import cn.hunanjiangxi.jxlabtrace.trace.TraceRepository
import kotlinx.coroutines.runBlocking
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
 * M13-2：全流程 MockWebServer 冒烟链单测（≥11 步贯通）。
 *
 * 链路：登录 → 到货 → 过磅 → 建袋 → 取样 → 建批 → 投料 → 成品批 → 成品袋 →
 *       装车建单 → 装车追加 → 出场 → 正向追溯。
 * 每步断言关键请求（方法 + 路径片段 + 必含字段），任一步失败即整链失败。
 */
class FullFlowSmokeTest {

    private lateinit var server: MockWebServer
    private val hits = java.util.Collections.synchronizedList(ArrayList<String>())

    // 各步固定夹具（合法 27 位码，校验位经算法计算）
    private val rawBag = "1BCG0001000226102001099000J" // B 原料吨袋
    private val fgBag = "1ECG0001000226102001001000P" // E 成品袋

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                val method = request.method.orEmpty()
                hits.add("$method $path")
                return when {
                    path.startsWith("/healthz") -> MockResponse().setResponseCode(200)

                    // 步0：登录态
                    path.startsWith("/api/me") -> MockResponse().setResponseCode(200).setBody(
                        """{"open_id":"ou_smoke","name":"冒烟员","roles":["receiver"],
                           "sys_roles":[],"permissions":[]}""",
                    )

                    // 步1：到货确认
                    method == "POST" && path == "/api/recv/arrivals" -> MockResponse()
                        .setResponseCode(200)
                        .setBody("""{"confirmed":true,"row":{"id":1,"code":"1A","plate_no":"湘F·A1234","status":"已到货"}}""")

                    // 步2：过磅
                    method == "POST" && path == "/api/recv/trucks/1/weigh" -> MockResponse()
                        .setResponseCode(200)
                        .setBody("""{"weighed":true,"row":{"id":1,"gross_weight":40.0,"tare_weight":12.0,"net_weight":28.0}}""")

                    // 步3：建袋
                    method == "POST" && path == "/api/recv/trucks/1/bags" -> MockResponse()
                        .setResponseCode(200)
                        .setBody(
                            """{"generated":true,"rows":[
                               {"id":99,"code":"$rawBag","bag_seq":1,"status":"在库"}]}""",
                        )

                    // 步4：取样（★ 直连接口，2xx 响应体无 envelope）
                    method == "POST" && path == "/api/sample/take" -> MockResponse()
                        .setResponseCode(200)
                        .setBody("""{"code":"$rawBag","status":"已取样"}""")

                    // 步5：建批
                    method == "POST" && path == "/api/prod/batches" -> MockResponse()
                        .setResponseCode(200)
                        .setBody(
                            """{"created":true,"row":{"id":7,"code":"1C","human":"1C-…",
                               "customer_id":9407,"status":"进行中"}}""",
                        )

                    // 步6：投料
                    method == "POST" && path == "/api/prod/batches/7/feeds" -> MockResponse()
                        .setResponseCode(200)
                        .setBody(
                            """{"fed":true,"row":{"id":10,"batch_id":7,"bag_code":"$rawBag",
                               "feed_weight":1.0}}""",
                        )

                    // 步7：成品批
                    method == "POST" && path == "/api/prod/batches/7/fg-lots" -> MockResponse()
                        .setResponseCode(200)
                        .setBody(
                            """{"created":true,"row":{"id":9,"code":"1D","human":"1D-…",
                               "batch_id":7,"qty_bag":40,"net_weight":39.8}}""",
                        )

                    // 步8：成品袋
                    method == "POST" && path == "/api/prod/fg-lots/9/bags" -> MockResponse()
                        .setResponseCode(200)
                        .setBody(
                            """{"count":1,"rows":[{"id":50,"code":"$fgBag","fg_lot_id":9,
                               "bag_seq":1,"status":"在库"}]}""",
                        )

                    // 步9：装车建单
                    method == "POST" && path == "/api/ship/shipments" -> MockResponse()
                        .setResponseCode(200)
                        .setBody(
                            """{"created":true,"row":{"id":3,"shipment_no":"CH261020-001",
                               "customer_id":9407,"status":"已出厂","ship_at":null}}""",
                        )

                    // 步10：装车追加
                    method == "POST" && path == "/api/ship/shipments/3/items" -> MockResponse()
                        .setResponseCode(200)
                        .setBody(
                            """{"added":true,"row":{"id":3,"shipment_no":"CH261020-001",
                               "status":"已出厂"}}""",
                        )

                    // 步11：出场
                    method == "POST" && path == "/api/ship/shipments/3/depart" -> MockResponse()
                        .setResponseCode(200)
                        .setBody(
                            """{"departed":true,"row":{"id":3,"shipment_no":"CH261020-001",
                               "plate_no":"湘F·A1234","driver":"张三",
                               "ship_at":"2026-10-20 14:20:00","status":"已出厂"}}""",
                        )

                    // 步12：正向追溯
                    method == "GET" && path.startsWith("/api/trace/forward") -> MockResponse()
                        .setResponseCode(200)
                        .setBody(
                            """{"source":{"truck_lot_id":1,"customer":"岳阳观盛"},
                               "batches":[{"batch_id":7,"code":"1C","status":"进行中"}],
                               "fg_lots":[{"fg_lot_id":9,"code":"1D","net_weight":39.8}],
                               "shipments":[{"shipment_id":3,"shipment_no":"CH261020-001"}],
                               "has_flow":true}""",
                        )

                    else -> MockResponse().setResponseCode(404)
                        .setBody("""{"error":"未实现的冒烟桩：$method $path"}""")
                }
            }
        }
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun api() = ApiClient(
        baseUrl = server.url("/").toString(),
        cookieJar = JxCookieJar(InMemorySidStore()),
        logger = JxLogger { _, _, _ -> },
    )

    @Test
    fun `M13-2 全流程冒烟 - 13步链贯通且每步关键请求断言通过`() = runBlocking {
        val client = api()
        val me = MeRepository(client, InMemoryMeStore())
        val recv = RecvRepository(client)
        val sample = SampleRepository(client)
        val prod = ProdRepository(client)
        val ship = ShipRepository(client)
        val trace = TraceRepository(client)

        // 步0：登录（拉取能力表）
        val meResult = me.refresh()
        assertTrue("步0 登录应成功，实际=$meResult", meResult is ApiResult.Success)
        assertEquals("冒烟员", (meResult as ApiResult.Success).data.name)

        // 步1：到货确认
        val arrive = recv.confirmArrival(noticeId = 1)
        assertTrue("步1 到货应成功，实际=$arrive", arrive is ApiResult.Success)

        // 步2：过磅
        val weigh = recv.weigh(truckId = 1, gross = 40.0, tare = 12.0)
        assertTrue("步2 过磅应成功，实际=$weigh", weigh is ApiResult.Success)

        // 步3：建袋
        val bags = recv.generateBags(truckId = 1, count = 1)
        assertTrue("步3 建袋应成功，实际=$bags", bags is ApiResult.Success)
        assertEquals(rawBag, (bags as ApiResult.Success).data.first().code)

        // 步4：取样（直连非 envelope —— 2xx 裸对象）
        val take = sample.take(
            SampleForm.build(rawBag, SampleTargetType.TRUCK, targetId = 1),
        )
        assertTrue("步4 取样应成功，实际=$take", take is ApiResult.Success)

        // 步5：建批
        val batch = prod.createBatch(
            CreateBatchBody(customer_id = 9407, input_material_id = 11, planned_output_material_id = 12),
        )
        assertTrue("步5 建批应成功，实际=$batch", batch is ApiResult.Success)
        val batchId = (batch as ApiResult.Success).data.id
        assertEquals(7L, batchId)

        // 步6：投料
        val feed = prod.feedScan(batchId, rawBag, weightTons = 1.0, operator = "甲班")
        assertTrue("步6 投料应成功，实际=$feed", feed is ApiResult.Success)

        // 步7：成品批
        val fgLot = prod.createFgLot(batchId, qtyBag = 40, netWeightTons = 39.8)
        assertTrue("步7 成品批应成功，实际=$fgLot", fgLot is ApiResult.Success)
        val fgLotId = (fgLot as ApiResult.Success).data.id

        // 步8：成品袋
        val fgBags = prod.generateFgBags(fgLotId, count = 1)
        assertTrue("步8 成品袋应成功，实际=$fgBags", fgBags is ApiResult.Success)

        // 步9：装车建单（首袋）
        val shipment = ship.createShipment(CreateShipmentBody(bag_codes = listOf(fgBag)))
        assertTrue("步9 装车建单应成功，实际=$shipment", shipment is ApiResult.Success)
        val shipId = (shipment as ApiResult.Success).data.id
        assertEquals(3L, shipId)

        // 步10：装车追加（同单追加同袋 —— 服务端校验，冒烟桩恒成功）
        val addItem = ship.addItem(shipId, fgBag)
        assertTrue("步10 装车追加应成功，实际=$addItem", addItem is ApiResult.Success)

        // 步11：出场
        val depart = ship.depart(shipId, DepartBody(plate_no = "湘F·A1234", driver = "张三"))
        assertTrue("步11 出场应成功，实际=$depart", depart is ApiResult.Success)
        assertEquals("湘F·A1234", (depart as ApiResult.Success).data.plate_no)

        // 步12：正向追溯（贯通验证）
        val fwd = trace.forward(truckLotId = 1)
        assertTrue("步12 正向追溯应成功，实际=$fwd", fwd is ApiResult.Success)
        val flow = (fwd as ApiResult.Success).data
        assertTrue(flow.has_flow)
        assertEquals(7L, flow.batches.first().batch_id)
        assertEquals("CH261020-001", flow.shipments.first().shipment_no)

        // ---- 整链请求断言（13 步关键请求全部命中）----
        val all = hits.joinToString("\n")
        val mustContain = listOf(
            "GET /api/me",
            "POST /api/recv/arrivals",
            "POST /api/recv/trucks/1/weigh",
            "POST /api/recv/trucks/1/bags",
            "POST /api/sample/take",
            "POST /api/prod/batches",
            "POST /api/prod/batches/7/feeds",
            "POST /api/prod/batches/7/fg-lots",
            "POST /api/prod/fg-lots/9/bags",
            "POST /api/ship/shipments",
            "POST /api/ship/shipments/3/items",
            "POST /api/ship/shipments/3/depart",
            "GET /api/trace/forward",
        )
        mustContain.forEach { needle ->
            assertTrue("整链应含请求「$needle」\n实际：\n$all", all.contains(needle))
        }
        // 链长 ≥ 13 步（healthz 前置探测不计）
        val businessHits = hits.count { !it.contains("healthz") }
        assertTrue("业务请求应 ≥13 步，实际=$businessHits", businessHits >= 13)
    }
}
