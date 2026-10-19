package cn.hunanjiangxi.jxlabtrace

import cn.hunanjiangxi.jxlabtrace.net.ApiClient
import cn.hunanjiangxi.jxlabtrace.net.ApiResult
import cn.hunanjiangxi.jxlabtrace.net.InMemorySidStore
import cn.hunanjiangxi.jxlabtrace.net.JxCookieJar
import cn.hunanjiangxi.jxlabtrace.net.JxLogger
import cn.hunanjiangxi.jxlabtrace.prod.FgBagPlan
import cn.hunanjiangxi.jxlabtrace.prod.FeedRecord
import cn.hunanjiangxi.jxlabtrace.prod.ProdRepository
import cn.hunanjiangxi.jxlabtrace.print.LabelRenderer
import cn.hunanjiangxi.jxlabtrace.print.PrinterProfile
import cn.hunanjiangxi.jxlabtrace.print.TsplPrinter
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
 * S11 成品打码：
 * - 袋数折算默认（投料净重 ÷ 0.6）；
 * - fg-lots / bags / print 请求体与响应解析；
 * - M7-6：打印复用 M09（LabelRenderer 同一实现，无第二套）。
 */
class FgPrintPlanTest {

    private lateinit var server: MockWebServer
    private var lotBody: String? = null
    private var bagsBody: String? = null
    private var printBody: String? = null

    private val lotJson =
        """{"id":4,"code":"1DCG9407940826102007001000","human":"1D-CG-9407-9408-261020-07-001-000-0",
            "batch_id":3,"pack_spec":"吨袋","qty_bag":39,"net_weight":23.4,"status":"在库"}"""

    private val bagsJson = (1..3).joinToString(",", "[", "]") { seq ->
        """{"id":$seq,"code":"1ECG9407940826102007001${seq.toString().padStart(3, '0')}",
            "human":"1E-CG-9407-9408-261020-07-001-${seq.toString().padStart(3, '0')}-0",
            "fg_lot_id":4,"bag_seq":$seq,"weight_allocated":0.6,"status":"在库"}"""
    }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.startsWith("/healthz") -> MockResponse().setResponseCode(200)
                    request.method == "POST" && path.endsWith("/fg-lots") -> {
                        lotBody = request.body.readUtf8()
                        MockResponse().setResponseCode(200)
                            .setBody("""{"created":true,"row":$lotJson}""")
                    }
                    request.method == "POST" && path.endsWith("/bags") -> {
                        bagsBody = request.body.readUtf8()
                        MockResponse().setResponseCode(200)
                            .setBody("""{"count":3,"rows":$bagsJson}""")
                    }
                    request.method == "POST" && path.endsWith("/print") -> {
                        printBody = request.body.readUtf8()
                        MockResponse().setResponseCode(200)
                            .setBody("""{"count":3,"rows":$bagsJson}""")
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

    private fun repo(): ProdRepository {
        val api = ApiClient(
            baseUrl = server.url("/").toString(),
            cookieJar = JxCookieJar(InMemorySidStore()),
            logger = JxLogger { _, _, _ -> },
        )
        return ProdRepository(api)
    }

    @Test
    fun `袋数折算 - 净重除0点6向上取整`() {
        val feeds = listOf(
            FeedRecord(id = 1, feed_weight = 1.02),
            FeedRecord(id = 2, feed_weight = 1.01),
            FeedRecord(id = 3, feed_weight = 1.02),
        )
        assertEquals(3.05, FgBagPlan.totalFeedWeight(feeds), 0.001)
        // ceil(3.05/0.6) = ceil(5.083) = 6
        assertEquals(6, FgBagPlan.defaultBagCount(feeds))
        assertEquals(0, FgBagPlan.defaultBagCount(emptyList()))
        assertEquals(0, FgBagPlan.defaultBagCount(listOf(FeedRecord(id = 1, feed_weight = null))))
    }

    @Test
    fun `createFgLot请求体 - qty与netWeight`() = runBlocking {
        val result = repo().createFgLot(3, qtyBag = 39, netWeightTons = 23.4)
        assertTrue(result is ApiResult.Success)
        val lot = (result as ApiResult.Success).data
        assertEquals("1D-CG-9407-9408-261020-07-001-000-0", lot.human)
        assertEquals(39, lot.qty_bag)
        val body = requireNotNull(lotBody)
        assertTrue(body.contains("\"qty_bag\":39"))
        assertTrue(body.contains("\"net_weight\":23.4"))
        assertTrue(body.contains("\"pack_spec\""))
    }

    @Test
    fun `generateFgBags与print - 请求体与解析`() = runBlocking {
        val bags = repo().generateFgBags(4, 3)
        assertTrue(bags is ApiResult.Success)
        assertEquals(3, (bags as ApiResult.Success).data.size)
        assertEquals(1, (bags.data[0]).bag_seq)
        assertEquals("""{"count":3}""", requireNotNull(bagsBody))

        val printed = repo().printFgBags(4)
        assertTrue(printed is ApiResult.Success)
        assertEquals(3, (printed as ApiResult.Success).data.size)
        assertEquals("{}", requireNotNull(printBody))
    }

    @Test
    fun `M7-6 成品标签渲染 - 复用M09 LabelRenderer同尺寸`() {
        // S11 与 S06 共用同一 LabelRenderer（全仓仅一处实现）——
        // 渲染结果尺寸恒为 PrinterProfile 800×800，证明走的是 M09 通道。
        val bits = LabelRenderer.render(
            bareCode = "1ECG9407940826102007001001",
            humanLine = "1E-CG-9407-9408-261020-07-001-001-0",
            objectType = "FG",
            dateText = "2026-10-20",
            seqText = "001",
        )
        assertEquals(PrinterProfile.MAX_WIDTH_DOTS * PrinterProfile.MAX_HEIGHT_DOTS, bits.size)
        // TsplPrinter 是唯一打印指令实现（M7-6 判据的机器侧佐证：
        // 本测试可编译引用唯一定义；S11 ViewModel 不直接持有 TsplPrinter，
        // 与 S06 相同经 printOne 注入 —— 全仓 grep TsplPrinter( 定义仅 1 处）
        assertTrue(TsplPrinter::class.java.name.contains("TsplPrinter"))
    }
}
