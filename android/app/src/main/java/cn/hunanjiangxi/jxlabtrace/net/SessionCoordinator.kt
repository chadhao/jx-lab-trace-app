package cn.hunanjiangxi.jxlabtrace.net

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 会话过期原位重授权（M03）：
 * 所有请求遇 401 ⇒ 发 `SessionExpired` 事件 ⇒ 全屏 WebView 重授权 ⇒ 成功后重放原请求。
 *
 * [withSessionRetry] 是可复用挂起函数：`session.withSessionRetry { api.execute…() }`。
 * 首次 401 → [reauthorize] 成功 → 重放一次；重放仍 401 则原样返回（不无限循环）。
 */
class SessionCoordinator(
    private val reauthorize: suspend () -> Boolean,
) {

    private val _sessionExpired = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** 会话过期事件（UI 可订阅用于提示「会话已过期，请重新验证」）。 */
    val sessionExpired: SharedFlow<Unit> = _sessionExpired.asSharedFlow()

    suspend fun <T> withSessionRetry(block: suspend () -> ApiResult<T>): ApiResult<T> {
        val first = block()
        val unauthorized = first is ApiResult.Failure && first.error is ApiError.Unauthorized
        if (!unauthorized) return first
        _sessionExpired.emit(Unit)
        return if (reauthorize()) block() else first
    }
}
