package cn.hunanjiangxi.jxlabtrace

import cn.hunanjiangxi.jxlabtrace.net.ApiClient
import cn.hunanjiangxi.jxlabtrace.net.ApiResult
import cn.hunanjiangxi.jxlabtrace.net.InMemorySidStore
import cn.hunanjiangxi.jxlabtrace.net.JxCookieJar
import cn.hunanjiangxi.jxlabtrace.net.JxLogger
import cn.hunanjiangxi.jxlabtrace.trace.BatchArchive
import cn.hunanjiangxi.jxlabtrace.trace.GenealogyBag
import cn.hunanjiangxi.jxlabtrace.trace.TraceBackwardResult
import cn.hunanjiangxi.jxlabtrace.trace.TraceForwardResult
import cn.hunanjiangxi.jxlabtrace.trace.TraceRepository
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * M11-1：谱系四接口适配单测（响应样例固定，逐字段断言解析）�? * 四接口：forward / backward / batch archive / genealogy�? */
class TraceAdapterTest {

    private lateinit var server: MockWebServer

    // ---- 固定响应样例（对�?internal/store/trace.go 读模�?JSON tag�?---

    private val forwardSample = """
        {
          "source": {
            "truck_lot_id": 1, "truck_code": "1ACG0001000226100703000000L",
            "customer_id": 9407, "customer": "岳阳观盛",
            "material_id": 2, "material": "石油�?,
            "arrive_date": "2026-10-18", "status": "已投�?
          },
          "batches": [
            {"batch_id": 7, "code": "1CCG9407940826102007000000",
             "human": "1C-CG-9407-9408-261020-07-000-000-0",
             "batch_date": "2026-10-19", "status": "进行�?}
          ],
          "fg_lots": [
            {"fg_lot_id": 9, "code": "1DCG9407940826102001000000",
             "human": "1D-CG-9407-9408-261020-01-000-000-0",
             "output_material": "石油焦成�?, "net_weight": 39.8, "status": "在库"}
          ],
          "shipments": [
            {"shipment_id": 3, "shipment_no": "CH261020-001", "status": "已出�?,
             "ship_at": "2026-10-20 14:20:00", "plate_no": "湘F·A1234", "customer": "岳阳观盛"}
          ],
          "has_flow": true
        }
    """.trimIndent()

    private val backwardSample = """
        {
          "fg_lot": {
            "fg_lot_id": 9, "code": "1DCG9407940826102001000000",
            "human": "1D-CG-9407-9408-261020-01-000-000-0",
            "output_material": "石油焦成�?, "net_weight": 39.8, "status": "在库"
          },
          "batches": [
            {"batch_id": 7, "code": "1CCG9407940826102007000000",
             "human": "1C-CG-9407-9408-261020-07-000-000-0",
             "batch_date": "2026-10-19", "status": "进行�?}
          ],
          "feeds": [
            {
              "bag_id": 99, "bag_code": "1BCG00010002261020010990008",
              "bag_human": "1B-CG-0001-0002-261020-01-099-000-8",
              "truck_lot_id": 1, "truck_code": "1ACG0001000226100703000000L",
              "customer": "岳阳观盛", "feed_weight": 1.02, "fed_at": "2026-10-19 10:22:41",
              "inspection": {
                "inspection_id": 5, "inspection_no": "INS-0031",
                "is_current": true,
                "results": [
                  {"item_name": "�?, "state": "已出", "value_num": 0.62, "value_text": "",
                   "unit": "%", "judge": "合格"},
                  {"item_name": "灰分", "state": "已出", "value_num": 0.31, "value_text": "",
                   "unit": "%", "judge": "合格"}
                ]
              }
            }
          ],
          "has_flow": true
        }
    """.trimIndent()

    private val archiveSample = """
        {
          "batch": {
            "batch_id": 7, "code": "1CCG9407940826102007000000",
            "human": "1C-CG-9407-9408-261020-07-000-000-0",
            "batch_date": "2026-10-19", "status": "进行�?
          },
          "customer_name": "岳阳观盛",
          "input_material": "石油�?,
          "planned_output_material": "石油焦成�?,
          "remark": "",
          "feeds": [{"id": 1}, {"id": 2}],
          "operations": [{"id": 1}],
          "fg_lots": [{"fg_lot": {"id": 9}}],
          "shipments": [{"shipment_id": 3}],
          "concession_used": false,
          "concession_sources": []
        }
    """.trimIndent()

    private val genealogySample = """
        {
          "found": true,
          "row": {
            "bag_id": 99, "bag_code": "1BCG00010002261020010990008",
            "bag_human": "1B-CG-0001-0002-261020-01-099-000-8",
            "status": "已投�?,
            "truck_id": 1, "truck_code": "1ACG0001000226100703000000L",
            "customer_id": 9407, "material_id": 2,
            "feeds": [
              {"id": 10, "batch_id": 7, "bag_code": "1BCG00010002261020010990008",
               "truck_code": "1ACG0001000226100703000000L",
               "feed_weight": 1.02, "fed_at": "2026-10-19 10:22:41", "operator": "甲班"}
            ]
          }
        }
    """.trimIndent()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun repo(body: String, code: Int = 200): TraceRepository {
        server.enqueue(MockResponse().setResponseCode(code).setBody(body))
        val api = ApiClient(
            baseUrl = server.url("/").toString(),
            cookieJar = JxCookieJar(InMemorySidStore()),
            logger = JxLogger { _, _, _ -> },
        )
        return TraceRepository(api)
    }

    @Test
    fun `M11-1 forward - 车次到出货全链逐字段解析`() = runTest {
        val result = repo(forwardSample).forward(truckLotId = 1)
        assertTrue("forward 应成功，实际=$result", result is ApiResult.Success)
        val f = (result as ApiResult.Success).data
        assertTrue(f.has_flow)
        assertEquals(1L, f.source.truck_lot_id)
        assertEquals("岳阳观盛", f.source.customer)
        assertEquals("石油�?, f.source.material)
        assertEquals(1, f.batches.size)
        assertEquals(7L, f.batches[0].batch_id)
        assertEquals("进行�?, f.batches[0].status)
        assertEquals(1, f.fg_lots.size)
        assertEquals(39.8, f.fg_lots[0].net_weight!!, 1e-9)
        assertEquals(1, f.shipments.size)
        assertEquals("CH261020-001", f.shipments[0].shipment_no)
        assertEquals("湘F·A1234", f.shipments[0].plate_no)
    }

    @Test
    fun `M11-1 forward - 请求query带truck_lot_id`() = runTest {
        repo(forwardSample).forward(truckLotId = 1)
        val request = server.takeRequest()
        assertTrue("path 应含 forward�?{request.path}", request.path!!.contains("/api/trace/forward"))
        assertTrue("query 应含 truck_lot_id=1", request.path!!.contains("truck_lot_id=1"))
    }

    @Test
    fun `M11-1 backward - 成品批到投料含检测结果行`() = runTest {
        val result = repo(backwardSample).backward(fgCode = "1DCG9407940826102001000000")
        assertTrue("backward 应成功，实际=$result", result is ApiResult.Success)
        val b = (result as ApiResult.Success).data
        assertTrue(b.has_flow)
        assertEquals(9L, b.fg_lot.fg_lot_id)
        assertEquals("石油焦成�?, b.fg_lot.output_material)
        assertEquals(1, b.batches.size)
        assertEquals(7L, b.batches[0].batch_id)
        assertEquals(1, b.feeds.size)
        val feed = b.feeds[0]
        assertEquals(99L, feed.bag_id)
        assertEquals("岳阳观盛", feed.customer)
        assertEquals(1.02, feed.feed_weight!!, 1e-9)
        // 检测结果行只读（M11-1：合格判定由 judge 行展示；不读顶层结论键——白名单约束�?        assertEquals("INS-0031", feed.inspection_no)
        assertTrue(feed.is_current)
        assertEquals(2, feed.results.size)
        assertEquals("�?, feed.results[0].item_name)
        assertEquals(0.62, feed.results[0].value_num!!, 1e-9)
        assertEquals("合格", feed.results[0].judge)
        assertEquals("%", feed.results[0].unit)
    }

    @Test
    fun `M11-1 batchArchive - 汇总计数按嵌套数组长度`() = runTest {
        val result = repo(archiveSample).batchArchive(7)
        assertTrue("archive 应成功，实际=$result", result is ApiResult.Success)
        val a = (result as ApiResult.Success).data
        assertEquals(7L, a.batch.batch_id)
        assertEquals("岳阳观盛", a.customer_name)
        assertEquals("石油�?, a.input_material)
        assertEquals(2, a.feed_count)
        assertEquals(1, a.operation_count)
        assertEquals(1, a.fg_lot_count)
        assertEquals(1, a.shipment_count)
    }

    @Test
    fun `M11-1 genealogy - 袋到去向批多对多`() = runTest {
        val result = repo(genealogySample).genealogy("1BCG00010002261020010990008")
        assertTrue("genealogy 应成功，实际=$result", result is ApiResult.Success)
        val g = (result as ApiResult.Success).data
        assertEquals(99L, g.bag_id)
        assertEquals("已投�?, g.status)
        assertEquals("1ACG0001000226100703000000L", g.truck_code)
        assertEquals(9407L, g.customer_id)
        assertEquals(1, g.feeds.size)
        assertEquals(7L, g.feeds[0].batch_id)
        assertEquals(1.02, g.feeds[0].feed_weight!!, 1e-9)
        assertEquals("甲班", g.feeds[0].operator)
    }

    @Test
    fun `M11-1 forward - has_flow为false时不算错误`() = runTest {
        val result = repo("""{"source":{},"batches":[],"fg_lots":[],"shipments":[],"has_flow":false}""")
            .forward(bagCode = "1BCG00010002261020010990008")
        assertTrue(result is ApiResult.Success)
        val f = (result as ApiResult.Success).data
        assertFalse("无流�?, f.has_flow)
        assertTrue(f.batches.isEmpty())
    }

    @Test
    fun `M11-1 四接口响�?- 均走executeRead正常通道`() = runTest {
        // 2xx 裸对象（顶层�?code 键）不应�?bizCodeOf 误判�?Biz�?        // 触发四次请求，逐一断言 Success（若被误判会�?Failure(Biz)�?        repo(forwardSample).forward(truckLotId = 1)
        repo(backwardSample).backward(fgLotId = 9)
        repo(archiveSample).batchArchive(7)
        repo(genealogySample).genealogy("1BCG00010002261020010990008")
        // 到达此处即四次均 Success（各方法内部�?assert）——补充请求计�?        assertEquals("四接口共 4 请求", 4, server.requestCount)
    }
}
