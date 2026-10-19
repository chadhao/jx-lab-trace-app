package cn.hunanjiangxi.jxlabtrace.scan

import cn.hunanjiangxi.jxlabtrace.net.ApiError
import cn.hunanjiangxi.jxlabtrace.net.ApiResult
import cn.hunanjiangxi.jxlabtrace.net.SessionCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 识别成功反馈（震动 + 蜂鸣，S03 ①三通道之一；视觉绿边+回显由 UI 吃 [echo]）。
 */
fun interface ScanFeedback {
    fun onRecognized(tail6: String)

    companion object {
        /** 单测/预览空实现。 */
        val NoOp = ScanFeedback { }
    }
}

/**
 * S03 扫码状态机（M04）：
 * `Idle → Scanning → Confirm(info) → Submitting → Done / Failure(reason)`。
 *
 * **必须由宿主业务构造**：[confirmLabel]（确认按钮文案随业务变）与 [onConfirm]
 * 为必传参数——全仓不存在无归属扫码入口（M4-4）。
 */
class ScanViewModel(
    val confirmLabel: String,
    private val onConfirm: (ScanInfo) -> Unit,
    private val service: ScanService,
    private val session: SessionCoordinator,
    private val feedback: ScanFeedback = ScanFeedback.NoOp,
    scope: CoroutineScope? = null,
) {

    private val scope: CoroutineScope = scope
        ?: CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow<ScanUiState>(ScanUiState.Idle)
    val state: StateFlow<ScanUiState> = _state.asStateFlow()

    private val _echo = MutableStateFlow<ScanEcho?>(null)
    val echo: StateFlow<ScanEcho?> = _echo.asStateFlow()

    private val _hint = MutableStateFlow<ScanHint?>(null)
    val hint: StateFlow<ScanHint?> = _hint.asStateFlow()

    /** 登录过期等会话级提示（与 M12 话术同源）。 */
    private val _notice = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val notice: SharedFlow<String> = _notice.asSharedFlow()

    /** Idle → Scanning（业务页进入即扫）。 */
    fun start() {
        if (_state.value is ScanUiState.Idle) _state.value = ScanUiState.Scanning
    }

    /** 相机/手输/HID 归一后的 27 位码进入解析。 */
    fun onCode(raw: String) {
        if (_state.value !is ScanUiState.Scanning) return // Confirm/Submitting 期间忽略连扫
        _hint.value = null
        resolveAndConfirm(raw)
    }

    /** HID 通道拒绝（红边 + 原因，不静默丢弃）。 */
    fun onHidRejected(reason: String) {
        _hint.value = ScanHint(reason)
    }

    /** 确认卡 → 提交（识别 ≠ 提交；真正的业务写由宿主 [onConfirm] 执行）。 */
    fun confirm() {
        val current = _state.value as? ScanUiState.Confirm ?: return
        _state.value = ScanUiState.Submitting
        onConfirm(current.info)
    }

    /**
     * 宿主业务写完成回调：
     * @param failure 成功传 null；失败传业务侧失败原因（如 403 越权 / 409 拒绝）。
     */
    fun submitFinished(failure: ScanFailure? = null) {
        if (_state.value !is ScanUiState.Submitting) return
        _state.value = if (failure == null) ScanUiState.Done else ScanUiState.Failure(failure)
    }

    /** 重扫/返回取景（清失败与回显，回 Scanning）。 */
    fun rescan() {
        _echo.value = null
        _hint.value = null
        _state.value = ScanUiState.Scanning
    }

    /** 回显展示窗口（≥800ms）结束后由 UI 清除绿边与大字。 */
    fun clearEcho() {
        _echo.value = null
    }

    private fun resolveAndConfirm(raw: String) {
        scope.launch {
            // 401 ⇒ SessionExpired ⇒ 原位重授权 ⇒ 重放原请求（M03 withSessionRetry）。
            val outcome = when (
                val wrapped = session.withSessionRetry {
                    when (val o = service.resolve(raw)) {
                        is ScanService.Outcome.NetFailed -> ApiResult.Failure(o.error)
                        else -> ApiResult.Success(o)
                    }
                }
            ) {
                is ApiResult.Failure -> ScanService.Outcome.NetFailed(wrapped.error)
                is ApiResult.Success -> wrapped.data
            }

            when (outcome) {
                is ScanService.Outcome.Found -> {
                    val info = outcome.info
                    val tail6 = info.code.takeLast(6)
                    _echo.value = ScanEcho(tail6)
                    feedback.onRecognized(tail6)
                    _state.value = ScanUiState.Confirm(info)
                }
                is ScanService.Outcome.Failed -> _state.value = ScanUiState.Failure(outcome.failure)
                is ScanService.Outcome.NetFailed -> when (val error = outcome.error) {
                    is ApiError.Offline -> {
                        _notice.emit(error.userMessage)
                        _state.value = ScanUiState.Scanning
                    }
                    is ApiError.Unauthorized -> {
                        _notice.emit("登录已过期，已重新验证，请重扫")
                        _state.value = ScanUiState.Scanning
                    }
                    else -> _notice.emit(error.userMessage)
                }
            }
        }
    }
}
