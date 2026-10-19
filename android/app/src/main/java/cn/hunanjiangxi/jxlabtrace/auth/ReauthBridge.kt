package cn.hunanjiangxi.jxlabtrace.auth

import android.content.Context
import android.content.Intent
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred

/**
 * 会话过期原位重授权桥（M03）：
 * [cn.hunanjiangxi.jxlabtrace.net.SessionCoordinator] 触发 → 全屏 LoginWebViewActivity
 * （EXTRA_REAUTH=true）→ 授权结束后回填结果，原请求重放。
 */
object ReauthBridge {

    private val pending = AtomicReference<CompletableDeferred<Boolean>?>(null)

    /** 挂起等待一次全屏重授权；结果 true=授权成功可重放原请求。 */
    suspend fun awaitReauth(context: Context): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        pending.set(deferred)
        val intent = Intent(context, LoginWebViewActivity::class.java).apply {
            putExtra(LoginWebViewActivity.EXTRA_REAUTH, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return deferred.await()
    }

    /** 由 LoginWebViewActivity 回填结果（成功/取消均回填，避免调用方悬挂）。 */
    fun complete(success: Boolean) {
        pending.getAndSet(null)?.complete(success)
    }
}
