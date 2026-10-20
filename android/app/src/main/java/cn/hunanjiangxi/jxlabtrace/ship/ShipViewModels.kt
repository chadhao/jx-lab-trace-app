package cn.hunanjiangxi.jxlabtrace.ship

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

// ===== S12 装车归集 ViewModel =====

sealed interface LoadUiState {
    /** 建单就绪（未扫首袋）。 */
    data class Ready(val shipmentNo: String = "") : LoadUiState

    /** 归集中：单已建，逐袋扫码。 */
    data class Collecting(
        val shipment: Shipment,
        val items: List<ShipmentItem>,
        val summary: ShipSummary,
    ) : LoadUiState

    /** 出场登记就绪（展示汇总 + 车牌/司机录入）。 */
    data class DepartReady(val shipment: Shipment, val summary: ShipSummary) : LoadUiState

    /** 已出场。 */
    data class Departed(val shipment: Shipment) : LoadUiState

    data class Error(val message: String) : LoadUiState
}

/**
 * S12 装车归集（M10-1/M10-2）：
 * - 首袋 ⇒ `POST shipments`（建单+归集一体，bag_codes≥1 为后端硬约束）；
 * - 后续袋 ⇒ `POST shipments/:id/items`；
 * - **重复袋本地先拦**（[CollectedBagSet]，第二次同码零请求）+ 服务端 409 二次校验；
 * - 汇总（袋数/净重）**本地实时累加**，不依赖刷新；
 * - 出场：`POST depart`（plate_no/driver 必填）。
 *
 * 宿主把 [submitBag] 挂到 ScanViewModel(confirmLabel="归集此袋") 的 onConfirm。
 * [weightOf]：单袋重量源（后端明细无重量列；生产可注入 fg 袋重量查询，单测注入固定值）。
 */
class LoadViewModel(
    private val repo: ShipRepository,
    private val session: SessionCoordinator,
    private val weightOf: suspend (String) -> Double? = { null },
    scope: CoroutineScope? = null,
) {
    private val scope = scope ?: CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow<LoadUiState>(LoadUiState.Ready())
    val state: StateFlow<LoadUiState> = _state.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** 本地重复拦截集合（M10-1）。 */
    private val collected = CollectedBagSet()

    /** 已归集明细（本地缓存，避免整单刷新）。 */
    private var items: MutableList<ShipmentItem> = mutableListOf()

    /** 汇总（M10-2 本地累加）。 */
    private var summary = ShipSummary()

    /**
     * 扫码确认回调（ScanViewModel onConfirm）：
     * ① 本地集合判重 ⇒ 重复直接回调失败文案，**零请求**（服务端计数仍 1）；
     * ② 无单 ⇒ 建单（首袋）；有单 ⇒ 追加；
     * ③ 成功 ⇒ 本地 +1（袋数/净重即增）+ 新行插入列表头部。
     */
    fun submitBag(bagCode: String, onFinished: (failureMessage: String?) -> Unit) {
        val code = CodeParser.normalize(bagCode)
        // ① 本地重复拦截（M10-1 核心：不发第二次请求）
        if (collected.contains(code)) {
            onFinished(DUPLICATE_BAG_MESSAGE)
            return
        }
        scope.launch {
            val current = _state.value
            val result = when (current) {
                is LoadUiState.Ready -> session.withSessionRetry {
                    repo.createShipment(CreateShipmentBody(bag_codes = listOf(code)))
                }
                is LoadUiState.Collecting -> session.withSessionRetry {
                    repo.addItem(current.shipment.id, code)
                }
                else -> {
                    onFinished("出货单当前状态不可归集")
                    return@launch
                }
            }
            when (result) {
                is ApiResult.Success -> {
                    collected.add(code)
                    val weight = weightOf(code)
                    summary = summary.add(code, weight)
                    items = (listOf(localItem(code)) + items).toMutableList()
                    _state.value = LoadUiState.Collecting(
                        shipment = result.data,
                        items = items.toList(),
                        summary = summary,
                    )
                    onFinished(null)
                }
                is ApiResult.Failure -> onFinished(ErrorMessages.of(result.error))
            }
        }
    }

    /** 进入出场登记（展示汇总；车牌/司机由 UI 录入——后端必填）。 */
    fun goDepart() {
        val current = _state.value as? LoadUiState.Collecting ?: return
        if (summary.bagCount == 0) {
            _message.value = "出货单无明细，不得出场登记；请先扫码归集。"
            return
        }
        _state.value = LoadUiState.DepartReady(current.shipment, summary)
    }

    /** 确认出场（M10：POST depart；plate_no/driver 必填）。 */
    fun confirmDepart(plateNo: String, driver: String) {
        val current = _state.value as? LoadUiState.DepartReady ?: return
        if (plateNo.isBlank() || driver.isBlank()) {
            _message.value = "车牌与司机必填。"
            return
        }
        scope.launch {
            when (
                val result = session.withSessionRetry {
                    repo.depart(
                        current.shipment.id,
                        DepartBody(plate_no = plateNo.trim(), driver = driver.trim()),
                    )
                }
            ) {
                is ApiResult.Success -> _state.value = LoadUiState.Departed(result.data)
                is ApiResult.Failure -> _message.value = ErrorMessages.of(result.error)
            }
        }
    }

    fun backToCollecting() {
        val current = _state.value as? LoadUiState.DepartReady ?: return
        _state.value = LoadUiState.Collecting(current.shipment, items.toList(), summary)
    }

    fun clearMessage() {
        _message.value = null
    }

    /** 本地明细行（后端 items 响应不含本次行的完整回包场景下先行展示；重量列后端无）。 */
    private fun localItem(bagCode: String): ShipmentItem = ShipmentItem(
        bag_code = bagCode,
        bag_human = runCatching { CodeParser.toHuman(bagCode) }.getOrDefault(bagCode),
    )

    companion object {
        /** 重复袋话术（M10-1：「该袋已在本次出货单中」，不重复计数）。 */
        const val DUPLICATE_BAG_MESSAGE = "该袋已在本次出货单中，不重复计数。"
    }
}
