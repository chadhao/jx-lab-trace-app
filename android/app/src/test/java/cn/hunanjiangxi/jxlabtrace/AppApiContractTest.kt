package cn.hunanjiangxi.jxlabtrace

import cn.hunanjiangxi.jxlabtrace.app.HomeOverview
import cn.hunanjiangxi.jxlabtrace.net.ApiClient
import cn.hunanjiangxi.jxlabtrace.net.ApiError
import cn.hunanjiangxi.jxlabtrace.net.ApiResult
import cn.hunanjiangxi.jxlabtrace.net.InMemorySidStore
import cn.hunanjiangxi.jxlabtrace.net.JxCookieJar
import cn.hunanjiangxi.jxlabtrace.net.JxLogger
import cn.hunanjiangxi.jxlabtrace.recv.RecvRepository
import cn.hunanjiangxi.jxlabtrace.ui.home.HomeOverviewState
import cn.hunanjiangxi.jxlabtrace.ui.home.HomeRepository
import cn.hunanjiangxi.jxlabtrace.ui.home.inProgressCardValue
import cn.hunanjiangxi.jxlabtrace.ui.home.recentCardValue
import cn.hunanjiangxi.jxlabtrace.ui.home.todosCardLines
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * MIMO-TASK-AB-INTEGRATION 契约单测（docs/APP-API-A-B.md V1.0）：
 * 每端点 ≥1 条按契约响应形状的正例 + ≥1 条结构化拦截四字段反例。
 * 判据 I-2（S02 三卡契约形状渲染映射）与 I-3（四字段 → Intercept 映射）在此覆盖。
 */
class AppApiContractTest {

    private lateinit var server: MockWebServer

    /** 按路径分发契约桩；[interceptPaths] 中的路径返回结构化拦截四字段。 */
    private fun stub(vararg interceptPaths: String) {
        val interceptSet = interceptPaths.toSet()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                val basePath = path.substringBefore('?')
                if (path.startsWith("/healthz")) return MockResponse().setResponseCode(200)
                if (basePath in interceptSet) {
                    return MockResponse().setResponseCode(403)
                        .setHeader("Content-Type", "application/json")
                        .setBody(
                            """{"error":"无权限","reason":"当前角色没有该操作的权限点。",
                               "owner":"系统管理员（权限配置）",
                               "contact":"飞书联系系统管理员，请其在 Web「后台 › 权限矩阵」按角色调整"}""",
                        )
                }
                return when (basePath) {
                    "/api/app/home" -> MockResponse().setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody(HOME_CONTRACT_JSON)

                    "/api/app/recv/notices" -> MockResponse().setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody(NOTICES_CONTRACT_JSON)

                    "/api/app/recv/arrive-confirm" -> MockResponse().setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody(
                            """{"confirmed":true,"truck_lot_id":41,"truck_code":"A2CODE",
                               "truck_human":"A2-XXXX","notice_id":12,"arrive_date":"2026-10-21",
                               "seq_no":3,"plate_no":"湘F·12345","status":"待检"}""",
                        )

                    "/api/app/recv/return" -> MockResponse().setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody(
                            """{"returned":true,"truck_lot_id":41,"truck_code":"A2CODE",
                               "notice_id":12,"status":"已退货","bag_count":0}""",
                        )

                    "/api/app/recv/weigh" -> MockResponse().setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody(
                            """{"weighed":true,"truck_lot_id":42,"truck_code":"A2CODE",
                               "gross_weight":12.0,"tare_weight":3.25,"net_weight":8.75,
                               "tare_source":"history",
                               "tare_history":{"truck_id":41,"tare_weight":3.25,"arrive_date":"2026-10-21"},
                               "status":"待检"}""",
                        )

                    "/api/app/recv/bag-gen" -> MockResponse().setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody(
                            """{"generated":2,"truck_lot_id":42,"bags":[
                               {"bag_seq":1,"code":"1BCG0001000226102003007001","human":"1B-1",
                                "weight_allocated":4.375,"weight_is_allocated":1,"status":"在库"},
                               {"bag_seq":2,"code":"1BCG0001000226102003007002","human":"1B-2",
                                "weight_allocated":4.375,"weight_is_allocated":1,"status":"在库"}]}""",
                        )

                    "/api/app/label/reprint" -> MockResponse().setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody(
                            """{"count":1,"labels":[{"label_id":9,"code":"1BCG0001000226102003007001",
                               "human":"1B-1","kind":"原料吨袋","is_reprint":1,
                               "reason":"标签破损 · 撕破重贴","printed_by":"ou_x"}],
                               "page_url":"/api/recv/labels/page?ids=9"}""",
                        )

                    else -> MockResponse().setResponseCode(404)
                        .setBody("""{"error":"未实现契约桩：$path"}""")
                }
            }
        }
    }

    @Before
    fun setUp() {
        server = MockWebServer()
        stub()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun api(): ApiClient = ApiClient(
        baseUrl = server.url("/").toString(),
        cookieJar = JxCookieJar(InMemorySidStore()),
        logger = JxLogger { _, _, _ -> },
    )

    /** 取下一条非 healthz 业务请求（写路径前置探测 HEAD /healthz 会先入队）。 */
    private fun takeBusinessRequest(): RecordedRequest {
        while (true) {
            val r = server.takeRequest()
            if (!r.path.orEmpty().startsWith("/healthz")) return r
        }
    }

    // ===== 批次 A · GET /api/app/home =====

    @Test
    fun `I-2 home契约形状 - 三卡映射正确渲染数据`() = runTest {
        val result = HomeRepository(api()).overview()
        assertTrue("home 应成功，实际=$result", result is ApiResult.Success)
        val data = (result as ApiResult.Success).data

        // §3.1 顶层字段
        assertEquals("ou_x", data.me.open_id)
        assertEquals("张三", data.me.name)
        assertEquals(listOf("receiver"), data.me.roles)
        assertEquals(listOf("insp", "recv", "ship"), data.domains)
        assertTrue(data.generated_at.startsWith("2026-10-21T"))

        // 进行中卡 = in_progress 列表 + count 角标
        assertEquals(2, data.in_progress.size)
        val noticeOpen = data.in_progress.first { it.key == "notice_open" }
        assertEquals(2, noticeOpen.count)
        assertEquals("湘F·12345 / 李师傅", noticeOpen.latest?.title)
        val (inProgressValue, inProgressHint) = inProgressCardValue(data)
        assertEquals("湘F·12345 / 李师傅", inProgressValue)
        assertTrue("hint 应含在办计数：$inProgressHint", inProgressHint.contains("在办"))
        assertTrue(inProgressHint.contains("预报中车次 2"))

        // count=0 项 latest=null 也保键（§3.3）
        val truckPending = data.in_progress.first { it.key == "truck_pending" }
        assertEquals(0, truckPending.count)
        assertEquals(null, truckPending.latest)

        // 待办卡 = todos 逐项行
        val todoLines = todosCardLines(data)
        assertTrue("待办卡应含待收行：$todoLines", todoLines.contains("待收车数：1"))
        assertTrue(todoLines.contains("待打码数：2"))

        // 最近作业卡 = recent（action 人话映射 + at 格式化）
        val (recentValue, recentHint) = recentCardValue(data)
        assertEquals("新建", recentValue) // create → 新建
        assertTrue(recentHint.contains("10-21 09:20")) // at 归一 MM-dd HH:mm

        // I-2 宿主状态映射
        val state = HomeOverviewState.Ready(data)
        assertTrue(state is HomeOverviewState.Ready)
    }

    @Test
    fun `I-3 home 403 - 结构化拦截四字段映射Intercept`() = runTest {
        stub("/api/app/home")
        val result = HomeRepository(api()).overview()
        assertTrue(result is ApiResult.Failure)
        val error = (result as ApiResult.Failure).error
        assertTrue("应为结构化拦截，实际=${error::class.simpleName}", error is ApiError.Intercept)
        error as ApiError.Intercept
        assertEquals(403, error.httpCode)
        assertEquals("无权限", error.error)
        assertTrue(error.reason.isNotBlank())
        assertEquals("系统管理员（权限配置）", error.owner)
        assertTrue(error.contact.contains("权限矩阵"))
    }

    // ===== §4.1 预报列表 =====

    @Test
    fun `S4-1 notices契约形状 - notice_id映射与mode查询参数`() = runTest {
        val result = RecvRepository(api()).listNotices(mode = "pending")
        assertTrue("notices 应成功，实际=$result", result is ApiResult.Success)
        val notices = (result as ApiResult.Success).data
        assertEquals(1, notices.size)
        val n = notices.first()
        assertEquals(12L, n.id) // notice_id → id
        assertEquals("YC-00012", n.notice_no)
        assertEquals(3L, n.batch_id)
        assertEquals("湘F·12345", n.plate_no)
        assertEquals("预报", n.status)
        assertEquals("", n.arrive_date)
        assertEquals(false, n.legacy)
        val recorded = takeBusinessRequest()
        assertTrue("应带 mode 查询参数：${recorded.path}", recorded.path!!.contains("mode=pending"))
    }

    @Test
    fun `S4-1 notices 403 - 结构化拦截`() = runTest {
        stub("/api/app/recv/notices")
        val result = RecvRepository(api()).listNotices()
        assertTrue(result is ApiResult.Failure)
        val error = (result as ApiResult.Failure).error as? ApiError.Intercept
        assertNotNull("应为结构化拦截，实际=$result", error)
        assertEquals("无权限", error!!.error)
    }

    // ===== §4.2 到货确认 =====

    @Test
    fun `S4-2 arrive-confirm契约形状 - 扁平响应映射车次`() = runTest {
        val result = RecvRepository(api()).confirmArrival(12, remark = "现场卸货完成")
        assertTrue("arrive-confirm 应成功，实际=$result", result is ApiResult.Success)
        val truck = (result as ApiResult.Success).data
        assertEquals(41L, truck.id) // truck_lot_id
        assertEquals("A2CODE", truck.code)
        assertEquals("湘F·12345", truck.plate_no)
        assertEquals("2026-10-21", truck.arrive_date)
        assertEquals("待检", truck.status)
        val request = takeBusinessRequest()
        val body = request.body.readUtf8()
        assertTrue("body 应含 notice_id：$body", body.contains("\"notice_id\":12"))
        assertTrue("body 应含 remark：$body", body.contains("现场卸货完成"))
    }

    @Test
    fun `S4-2 arrive-confirm 404无预报 - 结构化拦截四字段`() = runTest {
        server.shutdown()
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                if (request.path.orEmpty().startsWith("/healthz")) {
                    MockResponse().setResponseCode(200)
                } else {
                    MockResponse().setResponseCode(404)
                        .setHeader("Content-Type", "application/json")
                        .setBody(
                            """{"error":"无预报","reason":"预报单不存在或已取消，不能确认到货。",
                               "owner":"收货文员（预报登记）",
                               "contact":"飞书联系收货文员在 Web「收货 › 预报登记」补录预报后再操作"}""",
                        )
                }
        }
        server.start()
        val result = RecvRepository(api()).confirmArrival(999)
        assertTrue(result is ApiResult.Failure)
        val error = (result as ApiResult.Failure).error as? ApiError.Intercept
        assertNotNull("应为结构化拦截，实际=$result", error)
        assertEquals(404, error!!.httpCode)
        assertEquals("无预报", error.error)
        assertTrue(error.owner.contains("收货文员"))
        assertTrue(error.contact.contains("预报登记"))
    }

    // ===== §4.3 退车 =====

    @Test
    fun `S4-3 return契约形状 - notice_id入参与终态响应`() = runTest {
        val result = RecvRepository(api()).returnTruck(12, reason = "质检退货")
        assertTrue(result is ApiResult.Success)
        val data = (result as ApiResult.Success).data
        assertTrue(data.returned)
        assertEquals(41L, data.truck_lot_id)
        assertEquals("已退货", data.status)
        assertEquals(0, data.bag_count)
        val body = takeBusinessRequest().body.readUtf8()
        assertTrue(body.contains("\"notice_id\":12"))
        assertTrue(body.contains("质检退货"))
    }

    @Test
    fun `S4-3 return 403 - 结构化拦截`() = runTest {
        stub("/api/app/recv/return")
        val result = RecvRepository(api()).returnTruck(12)
        assertTrue(result is ApiResult.Failure)
        val error = (result as ApiResult.Failure).error as? ApiError.Intercept
        assertNotNull(error)
        assertEquals("无权限", error!!.error)
    }

    // ===== §4.4 过磅 =====

    @Test
    fun `S4-4 weigh契约形状 - 历史皮重与净重服务端算`() = runTest {
        // 省略皮重 ⇒ 请求体无 tare_weight 字段（§4.4 条件必填）
        val result = RecvRepository(api()).weigh(truckLotId = 42, gross = 12.0, tare = null)
        assertTrue(result is ApiResult.Success)
        val truck = (result as ApiResult.Success).data
        assertEquals(42L, truck.id)
        assertEquals(12.0, truck.gross_weight!!, 0.0001)
        assertEquals(3.25, truck.tare_weight!!, 0.0001)
        assertEquals(8.75, truck.net_weight!!, 0.0001) // 毛−皮，服务端算
        val body = takeBusinessRequest().body.readUtf8()
        assertTrue("省略皮重时 body 不得含 tare_weight：$body", !body.contains("tare_weight"))
        assertTrue(body.contains("\"gross_weight\":12.0"))
    }

    @Test
    fun `S4-4 weigh 400无历史皮重 - 结构化拦截要求人工录入`() = runTest {
        server.shutdown()
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                if (request.path.orEmpty().startsWith("/healthz")) {
                    MockResponse().setResponseCode(200)
                } else {
                    MockResponse().setResponseCode(400)
                        .setHeader("Content-Type", "application/json")
                        .setBody(
                            """{"error":"缺少皮重","reason":"该车无历史皮重，请人工录入皮重后重新提交。",
                               "owner":"收货文员（收货与打码）","contact":"飞书联系收货文员按页面提示处理"}""",
                        )
                }
        }
        server.start()
        val result = RecvRepository(api()).weigh(truckLotId = 42, gross = 12.0, tare = null)
        assertTrue(result is ApiResult.Failure)
        val error = (result as ApiResult.Failure).error as? ApiError.Intercept
        assertNotNull(error)
        assertEquals(400, error!!.httpCode)
        assertEquals("缺少皮重", error.error)
        assertTrue(error.reason.contains("人工录入"))
    }

    // ===== §4.5 建袋打码 =====

    @Test
    fun `S4-5 bag-gen契约形状 - 袋序后端分配不回传起始序号`() = runTest {
        val result = RecvRepository(api()).generateBags(truckLotId = 42, count = 2)
        assertTrue(result is ApiResult.Success)
        val bags = (result as ApiResult.Success).data
        assertEquals(2, bags.size)
        assertEquals(listOf(1, 2), bags.map { it.bag_seq }) // 后端分配从 1 起
        assertEquals(42L, bags.first().truck_lot_id)
        assertEquals(4.375, bags.first().weight_allocated!!, 0.0001)
        val body = takeBusinessRequest().body.readUtf8()
        assertTrue(body.contains("\"truck_lot_id\":42"))
        assertTrue(body.contains("\"count\":2"))
        assertTrue("APP 不得回传起始序号：$body", !body.contains("start_seq"))
        assertTrue(!body.contains("bag_seq"))
    }

    @Test
    fun `S4-5 bag-gen 409尚未过磅 - 结构化拦截`() = runTest {
        server.shutdown()
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                if (request.path.orEmpty().startsWith("/healthz")) {
                    MockResponse().setResponseCode(200)
                } else {
                    MockResponse().setResponseCode(409)
                        .setHeader("Content-Type", "application/json")
                        .setBody(
                            """{"error":"尚未过磅","reason":"该车还未过磅，不能生成袋码；请先完成过磅。",
                               "owner":"收货文员（收货与打码）","contact":"飞书联系收货文员按页面提示处理"}""",
                        )
                }
        }
        server.start()
        val result = RecvRepository(api()).generateBags(truckLotId = 42, count = 2)
        assertTrue(result is ApiResult.Failure)
        val error = (result as ApiResult.Failure).error as? ApiError.Intercept
        assertNotNull(error)
        assertEquals(409, error!!.httpCode)
        assertEquals("尚未过磅", error.error)
    }

    // ===== §4.6 标签补打 =====

    @Test
    fun `S4-6 reprint契约形状 - 单袋bag_code与留痕字段`() = runTest {
        val result = RecvRepository(api())
            .reprintLabel("1BCG0001000226102003007001", "标签破损 · 撕破重贴")
        assertTrue(result is ApiResult.Success)
        val data = (result as ApiResult.Success).data
        assertEquals(1, data.count)
        val label = data.labels.first()
        assertEquals(1, label.is_reprint)
        assertEquals("原料吨袋", label.kind)
        assertTrue(data.page_url.contains("/api/recv/labels/page"))
        val body = takeBusinessRequest().body.readUtf8()
        assertTrue(body.contains("\"bag_code\":\"1BCG0001000226102003007001\""))
        assertTrue(body.contains("标签破损"))
    }

    @Test
    fun `S4-6 reprint 400缺原因 - 结构化拦截`() = runTest {
        server.shutdown()
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                if (request.path.orEmpty().startsWith("/healthz")) {
                    MockResponse().setResponseCode(200)
                } else {
                    MockResponse().setResponseCode(400)
                        .setHeader("Content-Type", "application/json")
                        .setBody(
                            """{"error":"补打必须填写原因","reason":"补打未填写原因，无法留痕。",
                               "owner":"收货文员（收货与打码）","contact":"飞书联系收货文员按页面提示处理"}""",
                        )
                }
        }
        server.start()
        val result = RecvRepository(api()).reprintLabel("1BCG0001000226102003007001", "")
        assertTrue(result is ApiResult.Failure)
        val error = (result as ApiResult.Failure).error as? ApiError.Intercept
        assertNotNull(error)
        assertEquals(400, error!!.httpCode)
        assertTrue(error.error.contains("原因"))
    }

    companion object {
        /** §3.2 响应示例节选（契约原文字段级对齐）。 */
        private val HOME_CONTRACT_JSON = """
            {
              "me": {"open_id": "ou_x", "name": "张三", "roles": ["receiver"]},
              "domains": ["insp", "recv", "ship"],
              "in_progress": [
                {"key": "notice_open", "label": "预报中车次", "domain": "recv", "count": 2,
                 "latest": {"id": 12, "code": "N-20261021-003", "title": "湘F·12345 / 李师傅",
                            "status": "预报", "at": "2026-10-21 09:12:00"}},
                {"key": "truck_pending", "label": "待检车次", "domain": "insp", "count": 0, "latest": null}
              ],
              "todos": [
                {"key": "await_receive", "label": "待收车数", "domain": "recv", "count": 1},
                {"key": "await_label", "label": "待打码数", "domain": "recv", "count": 2},
                {"key": "await_load", "label": "待装车数", "domain": "ship", "count": 0}
              ],
              "recent": [
                {"id": 88, "at": "2026-10-21 09:20:00", "entity": "b_arrival_notice", "entity_id": 12,
                 "action": "create", "field": "", "new_value": "", "reason": "",
                 "actor_open_id": "ou_x", "actor": "张三"}
              ],
              "recent_source": "s_audit_log（actor=登录人，按 id 倒序取 10 条）",
              "generated_at": "2026-10-21T09:20:31+08:00"
            }
        """.trimIndent()

        /** §4.1 示例。 */
        private val NOTICES_CONTRACT_JSON = """
            {
              "count": 1,
              "notices": [
                {"notice_id": 12, "notice_no": "YC-00012", "batch_id": 3,
                 "our_batch_no": "F0001", "customer_batch_no": "C-88",
                 "customer_name": "某乳业", "material_name": "脱脂奶粉",
                 "seq_no": 3, "plate_no": "湘F·12345", "driver": "李师傅", "phone": "13800000000",
                 "status": "预报", "arrive_date": "", "legacy": false}
              ]
            }
        """.trimIndent()
    }
}
