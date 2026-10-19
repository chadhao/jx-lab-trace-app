package cn.hunanjiangxi.jxlabtrace

import cn.hunanjiangxi.jxlabtrace.net.ApiClient
import cn.hunanjiangxi.jxlabtrace.net.ApiResult
import cn.hunanjiangxi.jxlabtrace.net.InMemorySidStore
import cn.hunanjiangxi.jxlabtrace.net.JxCookieJar
import cn.hunanjiangxi.jxlabtrace.net.JxLogger
import cn.hunanjiangxi.jxlabtrace.net.SessionCoordinator
import cn.hunanjiangxi.jxlabtrace.prod.BatchCreateUiState
import cn.hunanjiangxi.jxlabtrace.prod.BatchCreateViewModel
import cn.hunanjiangxi.jxlabtrace.prod.BatchForm
import cn.hunanjiangxi.jxlabtrace.prod.ProdBatch
import cn.hunanjiangxi.jxlabtrace.prod.ProdRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
 * M7-1：建批页零输入 —— 默认值来自上批（MockWebServer 桩单测）；
 * 无上批 ⇒ 字段空 + 提交校验逐项提示。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProdBatchCreateTest {

    private lateinit var server: MockWebServer

    private val prevBatchJson =
        """{"id":7,"code":"1CCG9407940826102007000000","human":"1C-CG-9407-9408-261020-07-000-000-0",
            "customer_id":9407,"input_material_id":11,"planned_output_material_id":12,
            "batch_date":"2026-10-20","status":"已完成","remark":"","created_by":"ou_x"}"""

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun stubBatches(rowsJson: String) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.startsWith("/healthz") -> MockResponse().setResponseCode(200)
                    request.method == "GET" && path.startsWith("/api/prod/batches") ->
                        MockResponse().setResponseCode(200)
                            .setBody("""{"count":1,"rows":$rowsJson}""")
                    request.method == "POST" && path.startsWith("/api/prod/batches") ->
                        MockResponse().setResponseCode(200)
                            .setBody("""{"created":true,"row":$prevBatchJson}""")
                    else -> MockResponse().setResponseCode(404).setBody("""{"error":"no"}""")
                }
            }
        }
    }

    private fun repo(): ProdRepository {
        val api = ApiClient(
            baseUrl = server.url("/").toString(),
            cookieJar = JxCookieJar(InMemorySidStore()),
            logger = JxLogger { _, _, _ -> },
        )
        return ProdRepository(api)
    }

    // ---- 纯逻辑 ----

    @Test
    fun `M7-1 有上批 - 默认值沿用上批且表单有效`() {
        val prev = ProdBatch(
            id = 7,
            customer_id = 9407,
            input_material_id = 11,
            planned_output_material_id = 12,
            status = "已完成",
        )
        val form = BatchForm.defaultsFrom(prev)
        assertEquals(9407L, form.customer_id)
        assertEquals(11L, form.input_material_id)
        assertEquals(12L, form.planned_output_material_id)
        assertTrue("上批默认填充后应零输入可提交", BatchForm.isValid(form))
        assertTrue(BatchForm.validate(form).isEmpty())
    }

    @Test
    fun `M7-1 无上批 - 字段空且逐项校验提示`() {
        val form = BatchForm.defaultsFrom(null)
        assertEquals(0L, form.customer_id)
        assertEquals(0L, form.input_material_id)
        assertEquals(0L, form.planned_output_material_id)
        assertFalse(BatchForm.isValid(form))
        val errors = BatchForm.validate(form)
        assertEquals("无上批应逐项提示 3 条", 3, errors.size)
        assertTrue(errors.any { it.contains("客户") })
        assertTrue(errors.any { it.contains("投入物料") })
        assertTrue(errors.any { it.contains("产出") })
    }

    @Test
    fun `M7-1 无上批 - 仅缺产出时只提示一项`() {
        val form = BatchForm.empty().copy(customer_id = 1, input_material_id = 2)
        val errors = BatchForm.validate(form)
        assertEquals(1, errors.size)
        assertTrue(errors.first().contains("产出"))
    }

    // ---- ViewModel + MockWebServer ----

    @Test
    fun `M7-1 ViewModel有上批桩 - 装载后默认值来自上批`() = runBlocking {
        stubBatches("[$prevBatchJson]")
        val vm = BatchCreateViewModel(
            repo = repo(),
            session = SessionCoordinator { true },
            scope = kotlinx.coroutines.CoroutineScope(Dispatchers.Unconfined),
        )
        vm.load()
        val state = withTimeout(10_000) {
            vm.state.first { it is BatchCreateUiState.Editing }
        } as BatchCreateUiState.Editing
        assertEquals(9407L, state.form.customer_id)
        assertEquals(11L, state.form.input_material_id)
        assertEquals(12L, state.form.planned_output_material_id)
        assertTrue(BatchForm.isValid(state.form))
        assertTrue(state.previous?.id == 7L)
    }

    @Test
    fun `M7-1 ViewModel无上批桩 - 空表单加校验提示`() = runBlocking {
        stubBatches("[]")
        val vm = BatchCreateViewModel(
            repo = repo(),
            session = SessionCoordinator { true },
            scope = kotlinx.coroutines.CoroutineScope(Dispatchers.Unconfined),
        )
        vm.load()
        val state = withTimeout(10_000) {
            vm.state.first { it is BatchCreateUiState.Editing }
        } as BatchCreateUiState.Editing
        assertEquals(0L, state.form.customer_id)
        assertEquals(3, vm.validationErrors().size)
        // rebuild 清空回空表单（不发请求）
        vm.rebuild()
        val rebuilt = vm.state.value as BatchCreateUiState.Editing
        assertEquals(0L, rebuilt.form.customer_id)
    }

    @Test
    fun `M7-1 createBatch请求体 - 含三项主数据不含批号`() = runBlocking {
        var captured: String? = null
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.startsWith("/healthz") -> MockResponse().setResponseCode(200)
                    request.method == "POST" && path.startsWith("/api/prod/batches") -> {
                        captured = request.body.readUtf8()
                        MockResponse().setResponseCode(200)
                            .setBody("""{"created":true,"row":$prevBatchJson}""")
                    }
                    else -> MockResponse().setResponseCode(404).setBody("{}")
                }
            }
        }
        val result = repo().createBatch(
            BatchForm.defaultsFrom(
                ProdBatch(customer_id = 9407, input_material_id = 11, planned_output_material_id = 12),
            ),
        )
        assertTrue(result is ApiResult.Success)
        val body = requireNotNull(captured)
        assertTrue(body.contains("\"customer_id\":9407"))
        assertTrue(body.contains("\"input_material_id\":11"))
        assertTrue(body.contains("\"planned_output_material_id\":12"))
        assertFalse("批号由后端生成，请求体不得含 code", body.contains("\"code\""))
    }
}
