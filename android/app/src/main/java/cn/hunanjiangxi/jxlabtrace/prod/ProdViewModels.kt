package cn.hunanjiangxi.jxlabtrace.prod

import cn.hunanjiangxi.jxlabtrace.net.ApiResult
import cn.hunanjiangxi.jxlabtrace.net.ErrorMessages
import cn.hunanjiangxi.jxlabtrace.net.SessionCoordinator
import cn.hunanjiangxi.jxlabtrace.print.LabelRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ===== S08 建批 ViewModel =====

sealed interface BatchCreateUiState {
    object Loading : BatchCreateUiState
    data class Editing(val previous: ProdBatch?, val form: CreateBatchBody) : BatchCreateUiState
    data class Created(val batch: ProdBatch) : BatchCreateUiState
    data class Error(val message: String) : BatchCreateUiState
}

/**
 * S08 建批（M7-1）：三默认（上批配方/客户 + 登录班组展示）+ 批号只读（后端生成）。
 * 提交前可「重建」＝本地清空回空表单（后端无 PATCH batches 接口，已作废重建属 PC）。
 */
class BatchCreateViewModel(
    private val repo: ProdRepository,
    private val session: SessionCoordinator,
    scope: CoroutineScope? = null,
) {
    private val scope = scope ?: CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow<BatchCreateUiState>(BatchCreateUiState.Loading)
    val state: StateFlow<BatchCreateUiState> = _state.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun load() {
        scope.launch {
            _state.value = BatchCreateUiState.Loading
            // 「上一批」＝最近一条生产批（不限状态；列表按 id 倒序）
            when (val result = session.withSessionRetry { repo.listBatches() }) {
                is ApiResult.Success -> {
                    val previous = result.data.maxByOrNull { it.id }
                    _state.value = BatchCreateUiState.Editing(
                        previous = previous,
                        form = BatchForm.defaultsFrom(previous),
                    )
                }
                is ApiResult.Failure -> _state.value =
                    BatchCreateUiState.Error(ErrorMessages.of(result.error))
            }
        }
    }

    /** 显式修改某默认值（防手滑：默认不弹键盘，须点「修改」）。 */
    fun updateField(transform: (CreateBatchBody) -> CreateBatchBody) {
        val current = _state.value as? BatchCreateUiState.Editing ?: return
        _state.value = current.copy(form = transform(current.form))
    }

    /** 提交前重建：回到空表单（不发任何请求）。 */
    fun rebuild() {
        _state.value = BatchCreateUiState.Editing(previous = null, form = BatchForm.empty())
    }

    /** 逐项校验提示（M7-1：无上批 ⇒ 字段空 + 提交校验逐项提示）。 */
    fun validationErrors(): List<String> {
        val current = _state.value as? BatchCreateUiState.Editing ?: return listOf("页面未就绪")
        return BatchForm.validate(current.form)
    }

    fun submit() {
        val current = _state.value as? BatchCreateUiState.Editing ?: return
        val errors = BatchForm.validate(current.form)
        if (errors.isNotEmpty()) {
            _message.value = errors.first()
            return
        }
        scope.launch {
            when (val result = session.withSessionRetry { repo.createBatch(current.form) }) {
                is ApiResult.Success -> _state.value = BatchCreateUiState.Created(result.data)
                is ApiResult.Failure -> _message.value = ErrorMessages.of(result.error)
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}

// ===== S09 投料 ViewModel =====

sealed interface FeedUiState {
    data class Active(val batch: ProdBatch, val feeds: List<FeedRecord>, val fedCount: Int) : FeedUiState
    data class Error(val message: String) : FeedUiState
}

/** 更正页状态（列表行点「更正」进入）。 */
data class FeedCorrectSession(
    val feed: FeedRecord,
    /** 是否仍在本班次内（服务端时间判定；不可达按 false＝置灰处理）。 */
    val withinShift: Boolean,
)

/**
 * S09 投料（M7-2/M7-3/M7-4）：连续扫码确认后 POST feeds，
 * **本地即时 +1 不依赖刷新**；更正入口按服务端时间判班次。
 *
 * 宿主把 [submitBag] 挂到 ScanViewModel(confirmLabel="确认投料") 的 onConfirm。
 */
class FeedViewModel(
    private val repo: ProdRepository,
    private val session: SessionCoordinator,
    private val clock: ServerTimeSource,
    private val batchId: Long,
    scope: CoroutineScope? = null,
) {
    private val scope = scope ?: CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow<FeedUiState?>(null)
    val state: StateFlow<FeedUiState?> = _state.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _correcting = MutableStateFlow<FeedCorrectSession?>(null)
    val correcting: StateFlow<FeedCorrectSession?> = _correcting.asStateFlow()

    private val _correctDone = MutableStateFlow<FeedRecord?>(null)
    val correctDone: StateFlow<FeedRecord?> = _correctDone.asStateFlow()

    /** 本地累计（M7-2：成功回调即时 +1；load 时与服务端列表对齐）。 */
    private var fedCount: Int = 0

    fun load() {
        scope.launch {
            val batchResult = session.withSessionRetry {
                repo.listBatches().let { r ->
                    when (r) {
                        is ApiResult.Success ->
                            ApiResult.Success(r.data.find { it.id == batchId } ?: ProdBatch(id = batchId))
                        is ApiResult.Failure -> r
                    }
                }
            }
            when (val feedsResult = session.withSessionRetry { repo.listFeeds(batchId) }) {
                is ApiResult.Success -> {
                    fedCount = feedsResult.data.size
                    val batch = (batchResult as? ApiResult.Success)?.data ?: ProdBatch(id = batchId)
                    _state.value = FeedUiState.Active(batch, feedsResult.data, fedCount)
                }
                is ApiResult.Failure -> _state.value =
                    FeedUiState.Error(ErrorMessages.of(feedsResult.error))
            }
        }
    }

    /**
     * 扫码确认回调（ScanViewModel onConfirm）：POST feeds；
     * 成功 ⇒ 本地 +1 并把新记录插入列表头部（不触发整表刷新）。
     * [onFinished] 由宿主转发给 ScanViewModel.submitFinished 驱动扫码状态机。
     */
    fun submitBag(bagCode: String, onFinished: (failureMessage: String?) -> Unit) {
        val current = _state.value as? FeedUiState.Active ?: run {
            onFinished("投料页未就绪")
            return
        }
        scope.launch {
            when (val result = session.withSessionRetry {
                repo.feedScan(batchId, bagCode, weightTons = null, operator = "")
            }) {
                is ApiResult.Success -> {
                    fedCount = FeedProgress.afterConfirm(fedCount)
                    val feeds = listOf(result.data) + current.feeds
                    _state.value = current.copy(feeds = feeds, fedCount = fedCount)
                    onFinished(null)
                }
                is ApiResult.Failure -> onFinished(ErrorMessages.of(result.error))
            }
        }
    }

    /** 点「更正」：取服务端时间判班次（M7-3）。 */
    fun openCorrect(feed: FeedRecord) {
        scope.launch {
            val serverNow = clock.nowMillis()
            val fedAt = ShiftRules.parseFedAt(feed.fed_at)
            val within = if (serverNow == null || fedAt == null) {
                false
            } else {
                ShiftRules.withinSameShift(fedAt, serverNow)
            }
            _correcting.value = FeedCorrectSession(feed = feed, withinShift = within)
        }
    }

    fun cancelCorrect() {
        _correcting.value = null
    }

    /**
     * 提交更正（M7-4）：仅班次内可提交（置灰时 UI 不调用本方法 ⇒ 零请求）；
     * PATCH 成功后列表该行显示 v2。
     */
    fun submitCorrect(reasonCategory: String?, detail: String, newWeightTons: Double?) {
        val sessionState = _correcting.value ?: return
        if (!sessionState.withinShift) return
        val category = reasonCategory ?: return
        if (!FeedCorrectForm.isValid(category, detail, newWeightTons)) return
        val body = FeedCorrectForm.build(
            feed = sessionState.feed,
            newWeightTons = requireNotNull(newWeightTons),
            reasonCategory = category,
            detail = detail,
        )
        scope.launch {
            when (val result = session.withSessionRetry { repo.correctFeed(batchId, body) }) {
                is ApiResult.Success -> {
                    val updated = result.data
                    val current = _state.value as? FeedUiState.Active
                    if (current != null) {
                        val feeds = current.feeds.map { if (it.id == updated.id) updated else it }
                        _state.value = current.copy(feeds = feeds)
                    }
                    _correcting.value = null
                    _correctDone.value = updated
                }
                is ApiResult.Failure -> _message.value = ErrorMessages.of(result.error)
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    fun clearCorrectDone() {
        _correctDone.value = null
    }
}

// ===== S10 作业段 ViewModel =====

sealed interface OperationUiState {
    data class Ready(val operations: List<BatchOperation>, val phase: OperationTracker.Phase) : OperationUiState
    data class Submitting(val phase: OperationTracker.Phase) : OperationUiState
    data class Done(val operations: List<BatchOperation>) : OperationUiState
    data class Error(val message: String) : OperationUiState
}

/**
 * S10 作业段（M7-5）：开始/结束两点打点；本地计时仅展示；
 * 重复点「结束」幂等（第二次不发请求）；断网提交拦截但表单态保留（回 Running 可重试）。
 */
class OperationViewModel(
    private val repo: ProdRepository,
    private val session: SessionCoordinator,
    private val clock: ServerTimeSource,
    private val batchId: Long,
    private val operatorName: String = "",
    scope: CoroutineScope? = null,
) {
    private val scope = scope ?: CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val tracker = OperationTracker()

    private val _state = MutableStateFlow<OperationUiState>(
        OperationUiState.Ready(emptyList(), OperationTracker.Phase.Idle),
    )
    val state: StateFlow<OperationUiState> = _state.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _elapsedMillis = MutableStateFlow(0L)
    val elapsedMillis: StateFlow<Long> = _elapsedMillis.asStateFlow()

    /** 已记段缓存（Done 后开启下一段时保留展示）。 */
    private var knownOperations: List<BatchOperation> = emptyList()

    fun load() {
        scope.launch {
            when (val result = session.withSessionRetry { repo.listOperations(batchId) }) {
                is ApiResult.Success -> {
                    knownOperations = result.data
                    _state.value = OperationUiState.Ready(result.data, tracker.phase)
                }
                is ApiResult.Failure -> _state.value =
                    OperationUiState.Error(ErrorMessages.of(result.error))
            }
        }
    }

    /** 开始打点：取服务端时间作 start_at；本地计时仅展示。 */
    fun start() {
        scope.launch {
            val serverNow = clock.nowMillis()
            if (serverNow == null) {
                _message.value = "服务端时间不可达，请检查网络后重试"
                return@launch
            }
            if (tracker.onStart(serverNow)) {
                refreshReady()
                tickLoop()
            }
        }
    }

    /** 上一段结束后开启下一段（复位打点状态，保留已记列表）。 */
    fun startNext() {
        tracker.reset()
        _elapsedMillis.value = 0
        start()
    }

    /**
     * 结束打点（M7-5）：首次 POST operations（start_at+end_at）；
     * 重复调用因 [OperationTracker.onEnd] 返回 false 而**不发第二次请求**。
     */
    fun end() {
        if (!tracker.onEnd()) return
        val start = tracker.startMillis ?: run {
            tracker.onEndFailed()
            return
        }
        scope.launch {
            _state.value = OperationUiState.Submitting(OperationTracker.Phase.Ended)
            val serverNow = clock.nowMillis() ?: run {
                // 服务端时间取不到 ⇒ 回滚（表单态保留），可重试
                tracker.onEndFailed()
                refreshReady()
                _message.value = "服务端时间不可达，本段未提交；状态已保留，请重试"
                return@launch
            }
            val result = session.withSessionRetry {
                repo.addOperation(
                    batchId = batchId,
                    startAt = ServerClock.toBackendDateTime(start),
                    endAt = ServerClock.toBackendDateTime(serverNow),
                    operator = operatorName,
                )
            }
            when (result) {
                is ApiResult.Success -> {
                    knownOperations = knownOperations + result.data
                    _state.value = OperationUiState.Done(knownOperations)
                }
                is ApiResult.Failure -> {
                    // Offline/失败 ⇒ 表单态保留（回 Running），按钮可再点结束重试
                    tracker.onEndFailed()
                    refreshReady()
                    _message.value = ErrorMessages.of(result.error)
                }
            }
        }
    }

    private fun refreshReady() {
        _state.value = OperationUiState.Ready(knownOperations, tracker.phase)
    }

    private fun tickLoop() {
        scope.launch {
            while (tracker.phase == OperationTracker.Phase.Running) {
                val now = clock.nowMillis() ?: break
                _elapsedMillis.value = tracker.elapsedMillis(now)
                kotlinx.coroutines.delay(1_000)
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}

// ===== S11 成品打码 ViewModel =====

sealed interface FgUiState {
    /** 待建成品批：袋数折算默认可改。 */
    data class Preparing(val feeds: List<FeedRecord>, val defaultCount: Int, val batch: ProdBatch) : FgUiState

    /** 成品批已建，待生成袋。 */
    data class LotCreated(val lot: FgLot, val batch: ProdBatch) : FgUiState

    /** 袋已生成，连打就绪（复用 M09 队列）。 */
    data class BagsReady(val lot: FgLot, val bags: List<FgBag>, val batch: ProdBatch) : FgUiState

    data class Printing(val done: Int, val total: Int, val currentCode: String) : FgUiState

    data class PrintDone(val printed: Int, val failures: List<String>) : FgUiState

    data class Error(val message: String) : FgUiState
}

/**
 * S11 成品打码（M7-6）：POST fg-lots（批号自动）→ 袋数折算 → POST bags →
 * 连打**复用 M09 队列**（printOne 注入 + LabelRenderer，不出现第二套打印实现）；
 * 谱系挂接由后端完成。
 */
class FgPrintViewModel(
    private val repo: ProdRepository,
    private val session: SessionCoordinator,
    private val batchId: Long,
    private val printOne: suspend (String) -> ApiResult<Unit>,
    scope: CoroutineScope? = null,
) {
    private val scope = scope ?: CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow<FgUiState>(
        FgUiState.Preparing(emptyList(), 0, ProdBatch(id = batchId)),
    )
    val state: StateFlow<FgUiState> = _state.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private var batch: ProdBatch = ProdBatch(id = batchId)

    fun load() {
        scope.launch {
            val batchResult = session.withSessionRetry { repo.listBatches() }
            if (batchResult is ApiResult.Success) {
                batchResult.data.find { it.id == batchId }?.let { batch = it }
            }
            when (val feedsResult = session.withSessionRetry { repo.listFeeds(batchId) }) {
                is ApiResult.Success -> _state.value = FgUiState.Preparing(
                    feeds = feedsResult.data,
                    defaultCount = FgBagPlan.defaultBagCount(feedsResult.data),
                    batch = batch,
                )
                is ApiResult.Failure -> _state.value =
                    FgUiState.Error(ErrorMessages.of(feedsResult.error))
            }
        }
    }

    /** 建成品批（qtyBag/netWeight 来自折算默认 + 用户改定值）。 */
    fun createLot(qtyBag: Int, netWeightTons: Double?) {
        if (qtyBag <= 0) return
        scope.launch {
            _state.value = FgUiState.Preparing(
                feeds = (_state.value as? FgUiState.Preparing)?.feeds.orEmpty(),
                defaultCount = qtyBag,
                batch = batch,
            )
            when (val result = session.withSessionRetry {
                repo.createFgLot(batchId, qtyBag, netWeightTons)
            }) {
                is ApiResult.Success -> _state.value = FgUiState.LotCreated(result.data, batch)
                is ApiResult.Failure -> _message.value = ErrorMessages.of(result.error)
            }
        }
    }

    /** 生成成品袋 + 打印留痕 + 本地连打队列（复用 M09 printOne）。 */
    fun generateAndPrint(count: Int) {
        val lot = (_state.value as? FgUiState.LotCreated)?.lot ?: return
        if (count <= 0) return
        scope.launch {
            when (val bagsResult = session.withSessionRetry { repo.generateFgBags(lot.id, count) }) {
                is ApiResult.Failure -> {
                    _message.value = ErrorMessages.of(bagsResult.error)
                    return@launch
                }
                is ApiResult.Success -> {
                    val bags = bagsResult.data
                    // 服务端打印留痕（首打；补打走 PC）
                    when (val printRecord = session.withSessionRetry { repo.printFgBags(lot.id) }) {
                        is ApiResult.Failure -> {
                            _message.value = ErrorMessages.of(printRecord.error)
                            return@launch
                        }
                        is ApiResult.Success -> Unit
                    }
                    _state.value = FgUiState.BagsReady(lot, bags, batch)
                    runPrintQueue(bags)
                }
            }
        }
    }

    /** 已生成袋后重跑连打队列（不重复 POST bags/print）。 */
    fun startPrintQueue() {
        val s = _state.value as? FgUiState.BagsReady ?: return
        runPrintQueue(s.bags)
    }

    /** 连打队列（与 S06 同构：逐袋 printOne；进度实时）。 */
    private fun runPrintQueue(bags: List<FgBag>) {
        scope.launch {
            val failures = mutableListOf<String>()
            for ((index, bag) in bags.withIndex()) {
                _state.value = FgUiState.Printing(index, bags.size, bag.human.ifBlank { bag.code })
                when (val result = session.withSessionRetry { printOne(bag.code) }) {
                    is ApiResult.Success -> Unit
                    is ApiResult.Failure -> failures += "第 ${bag.bag_seq} 袋：${ErrorMessages.of(result.error)}"
                }
            }
            _state.value = FgUiState.PrintDone(bags.size - failures.size, failures)
        }
    }

    /** 渲染成品袋标签（复用 M09 LabelRenderer；类型位区分原料袋）。 */
    fun labelBitsFor(bag: FgBag): BooleanArray = LabelRenderer.render(
        bareCode = bag.code,
        humanLine = bag.human.ifBlank { bag.code },
        objectType = "FG",
        dateText = batch.batch_date,
        seqText = bag.bag_seq.toString().padStart(3, '0'),
    )
}
