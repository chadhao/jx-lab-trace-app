package cn.hunanjiangxi.jxlabtrace.recv

import cn.hunanjiangxi.jxlabtrace.net.ApiResult
import cn.hunanjiangxi.jxlabtrace.net.ErrorMessages
import cn.hunanjiangxi.jxlabtrace.net.SessionCoordinator
import cn.hunanjiangxi.jxlabtrace.print.LabelRenderer
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ===== S04 到货确认 ViewModel =====

sealed interface NoticesUiState {
    object Loading : NoticesUiState
    data class Loaded(val pending: List<RecvNotice>, val arrived: List<RecvNotice>) : NoticesUiState
    data class Error(val message: String) : NoticesUiState
}

/**
 * S04：拉预报 → 本地过滤当日（D23）→ 点选确认。
 * 无预报拦截是独立屏（[NoNoticeModel]），不经过本 VM 的写路径（M5-1）。
 */
class NoticesViewModel(
    private val repo: RecvRepository,
    private val session: SessionCoordinator,
    private val todayProvider: () -> LocalDate = LocalDate::now,
    scope: CoroutineScope? = null,
) {
    private val scope = scope ?: CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow<NoticesUiState>(NoticesUiState.Loading)
    val state: StateFlow<NoticesUiState> = _state.asStateFlow()

    private val _confirming = MutableStateFlow<RecvNotice?>(null)
    val confirming: StateFlow<RecvNotice?> = _confirming.asStateFlow()

    private val _confirmed = MutableStateFlow<RecvTruck?>(null)
    val confirmed: StateFlow<RecvTruck?> = _confirmed.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** 全量缓存（拦截页「最近 3 条」也从这里本地过滤）。 */
    @Volatile
    var allNotices: List<RecvNotice> = emptyList()
        private set

    fun refresh() {
        scope.launch {
            _state.value = NoticesUiState.Loading
            val result = session.withSessionRetry { repo.listNotices() }
            when (result) {
                is ApiResult.Success -> {
                    allNotices = result.data
                    val today = todayProvider()
                    _state.value = NoticesUiState.Loaded(
                        pending = NoticesFilter.pendingToday(result.data, today),
                        arrived = NoticesFilter.arrivedToday(result.data, today),
                    )
                }
                is ApiResult.Failure -> _state.value =
                    NoticesUiState.Error(ErrorMessages.of(result.error))
            }
        }
    }

    fun askConfirm(notice: RecvNotice) {
        _confirming.value = notice
    }

    fun cancelConfirm() {
        _confirming.value = null
    }

    fun confirmArrival() {
        val notice = _confirming.value ?: return
        _confirming.value = null
        scope.launch {
            when (val result = session.withSessionRetry { repo.confirmArrival(notice.id) }) {
                is ApiResult.Success -> {
                    _confirmed.value = result.data
                    refresh()
                }
                is ApiResult.Failure -> _message.value = ErrorMessages.of(result.error)
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    fun clearConfirmed() {
        _confirmed.value = null
    }
}

// ===== S04 无预报拦截（零写请求 · M5-1）=====

/**
 * 无预报拦截模型：只读列表本地过滤 + 拨号。
 * 判据 M5-1：本类**没有任何写方法** —— 结构上保证拦截页零 POST。
 * M12：[interceptKind] 区分「无预报 / 无车辆档案」文案变体（同一拦截页）。
 */
class NoNoticeModel(private val allNoticesProvider: () -> List<RecvNotice>) {

    fun recentNotices(plate: String): List<RecvNotice> =
        NoticesFilter.recentByPlate(allNoticesProvider(), plate)

    fun dialIntent(): DialIntent = DialActions.operationsDial()

    /** M12 拦截文案变体（无记录 ⇒ 档案缺失；有记录但今日无预报 ⇒ 无预报）。 */
    fun interceptKind(plate: String): ArrivalInterceptKind =
        arrivalInterceptKind(allNoticesProvider(), plate)
}

// ===== S05 过磅 ViewModel =====

sealed interface WeighUiState {
    data class Editing(val truck: RecvTruck) : WeighUiState
    object Submitting : WeighUiState
    data class Done(val truck: RecvTruck) : WeighUiState
    data class Error(val message: String) : WeighUiState
}

class WeighViewModel(
    private val repo: RecvRepository,
    private val session: SessionCoordinator,
    private val truckId: Long,
    scope: CoroutineScope? = null,
) {
    private val scope = scope ?: CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow<WeighUiState>(WeighUiState.Editing(RecvTruck(id = truckId)))
    val state: StateFlow<WeighUiState> = _state.asStateFlow()

    private val _gross = MutableStateFlow("")
    val gross: StateFlow<String> = _gross.asStateFlow()

    private val _tare = MutableStateFlow("")
    val tare: StateFlow<String> = _tare.asStateFlow()

    val net: Double? get() = WeighLogic.netTons(_gross.value, _tare.value)
    val canSubmit: Boolean get() = WeighLogic.canSubmitWeigh(_gross.value, _tare.value)

    fun load() {
        scope.launch {
            when (val result = session.withSessionRetry { repo.getTruck(truckId) }) {
                is ApiResult.Success -> {
                    val truck = result.data
                    _state.value = WeighUiState.Editing(truck)
                    // 皮重历史默认：该车已有皮重则带出（可改）
                    truck.tare_weight?.let { _tare.value = formatTons(it) }
                }
                is ApiResult.Failure -> _state.value =
                    WeighUiState.Error(ErrorMessages.of(result.error))
            }
        }
    }

    fun onGrossDigit(d: String) {
        _gross.value = appendDigit(_gross.value, d, allowDecimal = true)
    }

    fun onTareDigit(d: String) {
        _tare.value = appendDigit(_tare.value, d, allowDecimal = true)
    }

    fun onGrossBackspace() {
        _gross.value = _gross.value.dropLast(1)
    }

    fun onTareBackspace() {
        _tare.value = _tare.value.dropLast(1)
    }

    fun submit() {
        val g = _gross.value.toDoubleOrNull() ?: return
        val t = _tare.value.toDoubleOrNull() ?: return
        _state.value = WeighUiState.Submitting
        scope.launch {
            when (val result = session.withSessionRetry { repo.weigh(truckId, g, t) }) {
                is ApiResult.Success -> _state.value = WeighUiState.Done(result.data)
                is ApiResult.Failure -> _state.value =
                    WeighUiState.Error(ErrorMessages.of(result.error))
            }
        }
    }

    companion object {
        /** NumPad 输入拼接：防多小数点、防前导零膨胀。 */
        fun appendDigit(current: String, digit: String, allowDecimal: Boolean): String {
            if (!allowDecimal && digit == ".") return current
            if (digit == ".") {
                if (current.contains(".")) return current
                return if (current.isEmpty()) "0." else current + "."
            }
            if (current == "0" && digit != ".") return digit
            return current + digit
        }

        fun formatTons(v: Double): String =
            if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()
    }
}

// ===== S06 建袋打码 =====

sealed interface BagUiState {
    /** 未建袋：显示袋数确认。 */
    data class Ready(val truck: RecvTruck, val bagCount: Int) : BagUiState

    /** 已建袋，连打队列空闲。 */
    data class Built(val truck: RecvTruck, val bags: List<RecvBag>) : BagUiState

    data class Printing(val done: Int, val total: Int, val currentCode: String) : BagUiState

    data class PrintDone(val printed: Int, val failures: List<PrintFailure>) : BagUiState

    data class Error(val message: String) : BagUiState
}

data class PrintFailure(val bagSeq: Int, val code: String, val reason: String)

/**
 * S06：袋数折算 → 建袋（幂等）→ 连打队列。
 * 幂等：重试前先 [BagIdempotency.planRetry]，已有袋不重复 POST bags（M5-2）。
 */
class BagPrintViewModel(
    private val repo: RecvRepository,
    private val session: SessionCoordinator,
    private val truckId: Long,
    private val printOne: suspend (String) -> ApiResult<Unit>,
    scope: CoroutineScope? = null,
) {
    private val scope = scope ?: CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow<BagUiState>(
        BagUiState.Ready(RecvTruck(id = truckId), bagCount = 0),
    )
    val state: StateFlow<BagUiState> = _state.asStateFlow()

    private val _bagCountInput = MutableStateFlow("")
    val bagCountInput: StateFlow<String> = _bagCountInput.asStateFlow()

    private var truck: RecvTruck = RecvTruck(id = truckId)
    private var bags: List<RecvBag> = emptyList()

    fun load(defaultNet: Double?) {
        scope.launch {
            when (val result = session.withSessionRetry { repo.getTruck(truckId) }) {
                is ApiResult.Success -> {
                    truck = result.data
                    val net = result.data.net_weight ?: defaultNet
                    val count = WeighLogic.defaultBagCount(net)
                    _bagCountInput.value = count.toString()
                    // 已有袋 ⇒ 直接进入 Built（幂等恢复）
                    when (val bagsResult = session.withSessionRetry { repo.listBags(truckId) }) {
                        is ApiResult.Success -> {
                            bags = bagsResult.data
                            _state.value = if (bags.isEmpty()) {
                                BagUiState.Ready(truck, count)
                            } else {
                                BagUiState.Built(truck, bags)
                            }
                        }
                        is ApiResult.Failure -> _state.value =
                            BagUiState.Error(ErrorMessages.of(bagsResult.error))
                    }
                }
                is ApiResult.Failure -> _state.value =
                    BagUiState.Error(ErrorMessages.of(result.error))
            }
        }
    }

    fun onCountDigit(d: String) {
        _bagCountInput.value = (_bagCountInput.value + d).filter { it.isDigit() }.take(3)
    }

    fun onCountBackspace() {
        _bagCountInput.value = _bagCountInput.value.dropLast(1)
    }

    val desiredCount: Int get() = _bagCountInput.value.toIntOrNull() ?: 0

    /** 建袋（幂等入口）。 */
    fun generateBags() {
        val expected = desiredCount
        if (expected <= 0) return
        scope.launch {
            // 重试前先查已有（M5-2 幂等）
            val existing = when (val r = session.withSessionRetry { repo.listBags(truckId) }) {
                is ApiResult.Success -> r.data
                is ApiResult.Failure -> {
                    _state.value = BagUiState.Error(ErrorMessages.of(r.error))
                    return@launch
                }
            }
            when (val plan = BagIdempotency.planRetry(existing, expected)) {
                is BagIdempotency.RetryPlan.AlreadyDone -> {
                    bags = plan.bags
                    _state.value = BagUiState.Built(truck, bags)
                }
                BagIdempotency.RetryPlan.GenerateAll -> {
                    when (val result = session.withSessionRetry {
                        repo.generateBags(truckId, expected)
                    }) {
                        is ApiResult.Success -> {
                            bags = BagIdempotency.merge(existing, result.data)
                            _state.value = BagUiState.Built(truck, bags)
                        }
                        is ApiResult.Failure -> {
                            // 409/失败后再查一次：若已建成则幂等采用（并发/超时后重试）
                            val recheck = session.withSessionRetry { repo.listBags(truckId) }
                            if (recheck is ApiResult.Success &&
                                BagIdempotency.isContiguous(recheck.data, expected)
                            ) {
                                bags = recheck.data
                                _state.value = BagUiState.Built(truck, bags)
                            } else {
                                _state.value = BagUiState.Error(ErrorMessages.of(result.error))
                            }
                        }
                    }
                }
                is BagIdempotency.RetryPlan.Partial -> {
                    _state.value = BagUiState.Error(
                        "已存在 ${plan.existing} 袋（期望 ${plan.expected}），请到 PC 核查后重进本页",
                    )
                }
            }
        }
    }

    /** 连打队列：逐袋提交（进度实时；失败进异常列表）。 */
    fun startPrintQueue() {
        val queue = bags
        if (queue.isEmpty()) return
        scope.launch {
            val failures = mutableListOf<PrintFailure>()
            for ((index, bag) in queue.withIndex()) {
                _state.value = BagUiState.Printing(index, queue.size, bag.human.ifBlank { bag.code })
                when (val result = session.withSessionRetry { printOne(bag.code) }) {
                    is ApiResult.Success -> Unit
                    is ApiResult.Failure -> failures += PrintFailure(
                        bagSeq = bag.bag_seq,
                        code = bag.code,
                        reason = ErrorMessages.of(result.error),
                    )
                }
            }
            _state.value = BagUiState.PrintDone(queue.size - failures.size, failures)
        }
    }

    /** 补打（须有效 reason —— [ReprintForm.isValid] 门禁）。 */
    fun reprint(bagSeq: Int, reasonCategory: String?, detail: String, copies: Int = 1) {
        val bag = bags.find { it.bag_seq == bagSeq }
        if (bag == null || !ReprintForm.isValid(reasonCategory, detail)) return
        val reason = ReprintForm.reasonText(requireNotNull(reasonCategory), detail)
        scope.launch {
            var failures = 0
            repeat(copies.coerceIn(1, 10)) {
                when (val result = session.withSessionRetry {
                    repo.reprintLabels(listOf(bag.code), reason)
                }) {
                    is ApiResult.Success -> when (val send = printOne(bag.code)) {
                        is ApiResult.Success -> Unit
                        is ApiResult.Failure -> failures++
                    }
                    is ApiResult.Failure -> failures++
                }
            }
            if (failures == 0) {
                _state.value = BagUiState.Built(truck, bags)
            } else {
                _state.value = BagUiState.Error("补打失败 $failures 张，请检查打印机后重试")
            }
        }
    }

    /** 渲染单袋标签位图（供打印出口）。 */
    fun labelBitsFor(bag: RecvBag): BooleanArray = LabelRenderer.render(
        bareCode = bag.code,
        humanLine = bag.human.ifBlank { bag.code },
        objectType = "BAG",
        dateText = truck.arrive_date,
        seqText = bag.bag_seq.toString().padStart(3, '0'),
    )
}
