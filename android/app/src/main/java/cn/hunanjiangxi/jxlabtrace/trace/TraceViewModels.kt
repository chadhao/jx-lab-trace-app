package cn.hunanjiangxi.jxlabtrace.trace

import cn.hunanjiangxi.jxlabtrace.net.ApiResult
import cn.hunanjiangxi.jxlabtrace.net.ErrorMessages
import cn.hunanjiangxi.jxlabtrace.net.SessionCoordinator
import cn.hunanjiangxi.jxlabtrace.scan.CodeParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ===== S13 追溯 ViewModel =====

/** 时间线节点（S13 稿：单屏纵向时间线，一屏看完谁的料→哪批→测了什么）。 */
data class TraceTimelineNode(
    val title: String,
    val subtitle: String,
    /** 下钻目标：批档案 id（>0 可点开）。 */
    val batchId: Long = 0,
    /** 下钻目标：袋谱系码（非空可点开）。 */
    val genealogyBagCode: String? = null,
    /** 携带检测结果行（点开展示只读详情）。 */
    val feedDetail: TraceFeedRef? = null,
) {
    /** 可点开（有下钻目标或检测详情）。 */
    val clickable: Boolean get() = batchId > 0 || genealogyBagCode != null || feedDetail != null
}

/** 追溯结果视图（正/反向统一为时间线）。 */
data class TraceView(
    val headerCode: String,
    val headerType: String,
    val headerMeta: String,
    val nodes: List<TraceTimelineNode>,
    val hasFlow: Boolean,
)

sealed interface TraceUiState {
    /** 待扫码。 */
    object Idle : TraceUiState
    object Loading : TraceUiState
    data class Loaded(val view: TraceView) : TraceUiState
    /** 查无流向（200 + has_flow:false）——非错误，给引导。 */
    data class NoFlow(val hint: String) : TraceUiState
    data class Error(val message: String) : TraceUiState
}

/**
 * S13 追溯（M11）：ScanHero 扫码 → [CodeParser] 本地判型 → 路由四接口：
 * - A 车次 / B 原料吨袋 ⇒ `GET /api/trace/forward`（车→批→成品→出货）
 * - D 成品批 ⇒ `GET /api/trace/backward`（成品批→来源批→投料）
 * - B 原料吨袋另可下钻 `GET /api/prod/genealogy/bags/:code`（袋→投料→去向批）
 * - C 生产批 ⇒ `GET /api/trace/batch/:batchId`（需数字 id——码内无 id，扫 C 码时提示改用 forward/backward 入口，见设计疑问）
 * 全程只读，无任何写入口。
 */
class TraceViewModel(
    private val repo: TraceRepository,
    private val session: SessionCoordinator,
    scope: CoroutineScope? = null,
) {
    private val scope = scope ?: CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow<TraceUiState>(TraceUiState.Idle)
    val state: StateFlow<TraceUiState> = _state.asStateFlow()

    /** 扫码/手输回调（宿主挂 ScanHero → ScanViewModel(confirmLabel="追溯此码")）。 */
    fun submitCode(rawCode: String, onFinished: (failureMessage: String?) -> Unit) {
        val parsed = when (val r = CodeParser.parse(rawCode)) {
            is CodeParser.Result.Ok -> r.code
            is CodeParser.Result.Rejected -> {
                onFinished(r.reject.message)
                return
            }
        }
        scope.launch {
            _state.value = TraceUiState.Loading
            val result = when (parsed.objectType) {
                'A' -> loadForward(truckLotId = null, bagCode = null, parsed = parsed)
                'B' -> loadForward(truckLotId = null, bagCode = parsed.full, parsed = parsed)
                'D' -> loadBackward(fgCode = parsed.full, parsed = parsed)
                else -> {
                    _state.value = TraceUiState.Error(
                        "该对象类型（${parsed.typeLabel}）暂不支持直接追溯；" +
                            "请扫车次码、原料吨袋码或成品批码。",
                    )
                    onFinished("该对象类型暂不支持直接追溯")
                    return@launch
                }
            }
            onFinished(null)
            _state.value = result
        }
    }

    /** B 袋谱系下钻（袋 → 投料 → 去向批，多对多）。 */
    fun openGenealogy(bagCode: String) {
        scope.launch {
            _state.value = TraceUiState.Loading
            when (val result = session.withSessionRetry { repo.genealogy(bagCode) }) {
                is ApiResult.Success -> {
                    val g = result.data
                    _state.value = TraceUiState.Loaded(
                        TraceView(
                            headerCode = g.bag_human.ifBlank { g.bag_code },
                            headerType = "原料吨袋谱系",
                            headerMeta = buildString {
                                append("车次 ${g.truck_code.ifBlank { "—" }}")
                                append(" · 投料 ${g.feeds.size} 次")
                            },
                            nodes = g.feeds.map { feed ->
                                TraceTimelineNode(
                                    title = "投入生产批 #${feed.batch_id}",
                                    subtitle = listOfNotNull(
                                        feed.fed_at,
                                        feed.feed_weight?.let { "${it} t" },
                                        feed.operator.ifBlank { null },
                                    ).joinToString(" · "),
                                    batchId = feed.batch_id,
                                )
                            },
                            hasFlow = g.feeds.isNotEmpty(),
                        ),
                    )
                }
                is ApiResult.Failure -> _state.value =
                    TraceUiState.Error(ErrorMessages.of(result.error))
            }
        }
    }

    /** 批档案下钻（数字 id——来自 forward/backward 链上节点）。 */
    fun openBatchArchive(batchId: Long) {
        scope.launch {
            _state.value = TraceUiState.Loading
            when (val result = session.withSessionRetry { repo.batchArchive(batchId) }) {
                is ApiResult.Success -> {
                    val a = result.data
                    _state.value = TraceUiState.Loaded(
                        TraceView(
                            headerCode = a.batch.human.ifBlank { a.batch.code },
                            headerType = "批次档案",
                            headerMeta = listOfNotNull(
                                a.customer_name.ifBlank { null },
                                a.input_material.ifBlank { null },
                                "投料 ${a.feed_count} · 作业 ${a.operation_count} · " +
                                    "成品批 ${a.fg_lot_count} · 出货 ${a.shipment_count}",
                            ).joinToString(" · "),
                            nodes = listOf(
                                TraceTimelineNode("客户", a.customer_name.ifBlank { "—" }),
                                TraceTimelineNode("投入物料", a.input_material.ifBlank { "—" }),
                                TraceTimelineNode("计划产出", a.planned_output_material.ifBlank { "—" }),
                                TraceTimelineNode("投料次数", "${a.feed_count}"),
                                TraceTimelineNode("作业段", "${a.operation_count}"),
                                TraceTimelineNode("成品批", "${a.fg_lot_count}"),
                                TraceTimelineNode("出货单", "${a.shipment_count}"),
                            ),
                            hasFlow = true,
                        ),
                    )
                }
                is ApiResult.Failure -> _state.value =
                    TraceUiState.Error(ErrorMessages.of(result.error))
            }
        }
    }

    /** 回到扫码起点。 */
    fun reset() {
        _state.value = TraceUiState.Idle
    }

    // ---- 内部加载 ----

    private suspend fun loadForward(
        truckLotId: Long?,
        bagCode: String?,
        parsed: CodeParser.ParsedCode,
    ): TraceUiState {
        // 绑定 result 后用 is Success 逐支判断（勿改回多分支 when(val result = …)）：
        // Failure : ApiResult<Nothing> 会使该写法把 result.data 推断成 Nothing（CI 实证）。
        val raw: ApiResult<TraceForwardResult> = repo.forward(truckLotId, bagCode)
        val result = session.withSessionRetry { raw }
        if (result is ApiResult.Failure) {
            return TraceUiState.Error(ErrorMessages.of(result.error))
        }
        val f = (result as ApiResult.Success).data
        if (!f.has_flow) {
            return TraceUiState.NoFlow(
                "${parsed.typeLabel} ${parsed.human} 在系统中查无投料流向；" +
                    "可能尚未投料，请确认后重试。",
            )
        }
        return TraceUiState.Loaded(
            TraceView(
                headerCode = parsed.human,
                headerType = parsed.typeLabel,
                headerMeta = listOfNotNull(
                    f.source.customer.ifBlank { null },
                    f.source.material.ifBlank { null },
                    f.source.arrive_date.ifBlank { null },
                ).joinToString(" · "),
                nodes = buildList {
                    add(
                        TraceTimelineNode(
                            title = "原料车次 ${f.source.truck_code.ifBlank { "—" }}",
                            subtitle = listOfNotNull(
                                f.source.customer.ifBlank { null },
                                f.source.arrive_date.ifBlank { null },
                                f.source.status.ifBlank { null },
                            ).joinToString(" · "),
                        ),
                    )
                    f.batches.forEach { b ->
                        add(
                            TraceTimelineNode(
                                title = "生产批 ${b.human.ifBlank { b.code }}",
                                subtitle = listOfNotNull(
                                    b.batch_date.ifBlank { null },
                                    b.status.ifBlank { null },
                                ).joinToString(" · "),
                                batchId = b.batch_id,
                            ),
                        )
                    }
                    f.fg_lots.forEach { l ->
                        add(
                            TraceTimelineNode(
                                title = "成品批 ${l.human.ifBlank { l.code }}",
                                subtitle = listOfNotNull(
                                    l.output_material.ifBlank { null },
                                    l.net_weight?.let { "$it t" },
                                    l.status.ifBlank { null },
                                ).joinToString(" · "),
                            ),
                        )
                    }
                    f.shipments.forEach { s ->
                        add(
                            TraceTimelineNode(
                                title = "出货单 ${s.shipment_no}",
                                subtitle = listOfNotNull(
                                    s.customer.ifBlank { null },
                                    s.plate_no.ifBlank { null },
                                    s.ship_at,
                                    s.status.ifBlank { null },
                                ).joinToString(" · "),
                            ),
                        )
                    }
                },
                hasFlow = true,
            ),
        )
    }

    private suspend fun loadBackward(
        fgCode: String,
        parsed: CodeParser.ParsedCode,
    ): TraceUiState {
        // 绑定 result 后用 is Success 逐支判断 —— 同 loadForward（见上方注释）。
        val raw: ApiResult<TraceBackwardResult> = repo.backward(fgCode = fgCode)
        val result = session.withSessionRetry { raw }
        if (result is ApiResult.Failure) {
            return TraceUiState.Error(ErrorMessages.of(result.error))
        }
        val b = (result as ApiResult.Success).data
        if (!b.has_flow) {
            return TraceUiState.NoFlow(
                "成品批 ${parsed.human} 查无来源投料流向；请确认后重试。",
            )
        }
        return TraceUiState.Loaded(
            TraceView(
                headerCode = parsed.human,
                headerType = parsed.typeLabel,
                headerMeta = listOfNotNull(
                    b.fg_lot.output_material.ifBlank { null },
                    b.fg_lot.net_weight?.let { "$it t" },
                    b.fg_lot.status.ifBlank { null },
                ).joinToString(" · "),
                nodes = buildList {
                    b.batches.forEach { batch ->
                        add(
                            TraceTimelineNode(
                                title = "来源生产批 ${batch.human.ifBlank { batch.code }}",
                                subtitle = listOfNotNull(
                                    batch.batch_date.ifBlank { null },
                                    batch.status.ifBlank { null },
                                ).joinToString(" · "),
                                batchId = batch.batch_id,
                            ),
                        )
                    }
                    b.feeds.forEach { feed ->
                        val judgeLine = feed.results
                            .filter { it.judge.isNotBlank() }
                            .joinToString(" · ") { "${it.item_name} ${it.judge}" }
                        add(
                            TraceTimelineNode(
                                title = "原料吨袋 ${feed.bag_human.ifBlank { feed.bag_code }}",
                                subtitle = listOfNotNull(
                                    feed.truck_code.ifBlank { null },
                                    feed.customer.ifBlank { null },
                                    feed.fed_at,
                                    judgeLine.ifBlank { null },
                                ).joinToString(" · "),
                                genealogyBagCode = feed.bag_code.ifBlank { null },
                                feedDetail = feed.takeIf { it.results.isNotEmpty() },
                            ),
                        )
                    }
                },
                hasFlow = true,
            ),
        )
    }
}
