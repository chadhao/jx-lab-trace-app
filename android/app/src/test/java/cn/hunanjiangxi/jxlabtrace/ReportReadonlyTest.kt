package cn.hunanjiangxi.jxlabtrace

import cn.hunanjiangxi.jxlabtrace.net.ApiClient
import cn.hunanjiangxi.jxlabtrace.net.ApiResult
import cn.hunanjiangxi.jxlabtrace.net.InMemorySidStore
import cn.hunanjiangxi.jxlabtrace.net.JxCookieJar
import cn.hunanjiangxi.jxlabtrace.net.JxLogger
import cn.hunanjiangxi.jxlabtrace.net.SessionCoordinator
import cn.hunanjiangxi.jxlabtrace.rpt.ReportFilters
import cn.hunanjiangxi.jxlabtrace.rpt.ReportName
import cn.hunanjiangxi.jxlabtrace.rpt.ReportRepository
import cn.hunanjiangxi.jxlabtrace.rpt.ReportResult
import cn.hunanjiangxi.jxlabtrace.rpt.ReportUiState
import cn.hunanjiangxi.jxlabtrace.rpt.ReportViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
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
 * M11-2：报表只读 —— 无导出按钮、无写入口。
 * ① ReportRepository 全方法仅产生 GET（MockWebServer 断言 method=GET）；
 * ② 空数据两态文案不同（has_data=false「数据未接入」/ true+空 rows「本条件下无记录」）；
 * ③ 静态等价：本测试类所在模块源码 grep `rpt/export` 零命中由 check_scope.py 门禁覆盖。
 */
class ReportReadonlyTest {

    private lateinit var server: MockWebServer
    private val writeRequests = java.util.Collections.synchronizedList(ArrayList<String>())

    private val reportSample = """
        {
          "name": "quality-trend",
          "title": "质量趋势",
          "columns": [
            {"key": "period", "label": "月份"},
            {"key": "lots", "label": "批次数"},
            {"key": "pass_rate", "label": "合格率"}
          ],
          "rows": [
            {"period": "2026-10", "lots": 18, "pass_rate": "97%"},
            {"period": "2026-09", "lots": 22, "pass_rate": "95%"}
          ],
          "has_data": true
        }
    """.trimIndent()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.method != "GET" && request.method != "HEAD") {
                    writeRequests.add("${request.method} ${request.path}")
                }
                val path = request.path.orEmpty()
                return when {
                    path.startsWith("/healthz") -> MockResponse().setResponseCode(200)
                    path.startsWith("/api/rpt/quality-trend") ->
                        MockResponse().setResponseCode(200).setBody(reportSample)
                    path.startsWith("/api/rpt/") && path.contains("no-source") ->
                        MockResponse().setResponseCode(200).setBody(
                            """{"name":"x","title":"空表","columns":[],"rows":[],
                               "has_data":false,"note":"数据未接入"}""",
                        )
                    path.startsWith("/api/rpt/") ->
                        MockResponse().setResponseCode(200).setBody(
                            """{"name":"x","title":"空表","columns":[],"rows":[],
                               "has_data":true,"note":"本条件下无记录"}""",
                        )
                    else -> MockResponse().setResponseCode(404).setBody("{}")
                }
            }
        }
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun repo(): ReportRepository {
        val api = ApiClient(
            baseUrl = server.url("/").toString(),
            cookieJar = JxCookieJar(InMemorySidStore()),
            logger = JxLogger { _, _, _ -> },
        )
        return ReportRepository(api)
    }

    @Test
    fun `M11-2 报表查询 - 仅GET且解析列与行`() = runTest {
        val result = repo().run(ReportName.QUALITY_TREND)
        assertTrue("报表应成功，实际=$result", result is ApiResult.Success)
        val r = (result as ApiResult.Success).data
        assertEquals("quality-trend", r.name)
        assertEquals("质量趋势", r.title)
        assertEquals(3, r.columns.size)
        assertEquals("月份", r.columns[0].label)
        assertEquals(2, r.rows.size)
        assertEquals("2026-10", r.rows[0]["period"])
        assertEquals("18", r.rows[0]["lots"]) // 数字序列化为整数文本
        assertTrue(r.hasData)
        // 零写请求
        assertTrue("报表路径不得产生写请求：$writeRequests", writeRequests.isEmpty())
    }

    @Test
    fun `M11-2 筛选参数 - 拼入query`() = runTest {
        repo().run(ReportName.QUALITY_TREND, ReportFilters(from = "2026-10-01", to = "2026-10-20"))
        val request = server.takeRequest()
        assertTrue(request.path!!.contains("from=2026-10-01"))
        assertTrue(request.path!!.contains("to=2026-10-20"))
        assertEquals("GET", request.method)
    }

    @Test
    fun `M11-2 空数据两态 - 文案不同`() {
        // has_data=false ⇒ 数据未接入
        val noSource = ReportResult(
            name = "x", title = "空表", hasData = false, note = "数据未接入",
        )
        assertNotNull(noSource.emptyHint)
        assertEquals(ReportResult.EMPTY_NO_SOURCE, noSource.emptyHint)

        // has_data=true + 空 rows ⇒ 本条件下无记录
        val noMatch = ReportResult(
            name = "x", title = "空表", hasData = true, note = "本条件下无记录",
        )
        assertEquals(ReportResult.EMPTY_NO_MATCH, noMatch.emptyHint)

        // 两态文案必须不同（A15）
        assertFalse(noSource.emptyHint == noMatch.emptyHint)

        // 有数据 ⇒ 空态文案为 null
        val withData = ReportResult(
            name = "x", title = "t", hasData = true,
            rows = listOf(mapOf("a" to "1")),
        )
        assertEquals(null, withData.emptyHint)
    }

    @Test
    fun `M11-2 ViewModel 4Tab - 只读刷新无导出`() = runBlocking {
        val api = ApiClient(
            baseUrl = server.url("/").toString(),
            cookieJar = JxCookieJar(InMemorySidStore()),
            logger = JxLogger { _, _, _ -> },
        )
        val viewModel = ReportViewModel(
            repo = ReportRepository(api),
            session = SessionCoordinator { true },
            scope = kotlinx.coroutines.CoroutineScope(Dispatchers.Unconfined),
        )
        // 4 Tab（S14 稿：经营看板）
        assertEquals(4, viewModel.tabs.size)
        viewModel.refresh()
        val state = withTimeout(10_000) {
            viewModel.state.first { it is ReportUiState.Loaded }
        } as ReportUiState.Loaded
        assertEquals("质量趋势", state.result.title)

        // 切 Tab
        viewModel.selectTab(1)
        val switched = withTimeout(10_000) {
            viewModel.state.first { it is ReportUiState.Loaded }
        }
        assertTrue(switched is ReportUiState.Loaded)

        // 全程零写请求（无导出、无任何 POST/PUT/PATCH/DELETE）
        assertTrue("报表 VM 不得产生写请求：$writeRequests", writeRequests.isEmpty())
        // 所有业务请求均为 GET
        val request = server.takeRequest()
        assertEquals("GET", request.method)
    }

    @Test
    fun `M11-2 五张报表名 - 与后端目录一致`() {
        val names = ReportName.entries.map { it.apiValue }
        assertEquals(
            listOf(
                "quality-trend", "customer-recon", "output-yield",
                "sample-expiry", "nonconform-stat",
            ),
            names,
        )
    }
}
