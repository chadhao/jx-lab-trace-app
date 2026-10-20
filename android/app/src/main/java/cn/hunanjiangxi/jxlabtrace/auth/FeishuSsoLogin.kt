package cn.hunanjiangxi.jxlabtrace.auth

import java.net.URLEncoder
import java.security.SecureRandom

/**
 * LarkSSO 集成的纯函数辅助（MIMO-TASK-LARKSSO；由上一轮 AuthApplink 重构而来，deep-link 专属物已删）：
 * - 登录会话 state 生成（内存持有，SDK 回 code 后随 exchange 原样回传）；
 * - 单次 percent-encode；
 * - SDK `onError` 错误码 → 登录信号分类（飞书错误处理表 -1…-6）。
 *
 * 本对象零 Android/SDK 依赖（单测入口）；SDK 交互全部在 LoginWebViewActivity。
 */
object FeishuSsoLogin {

    private val random = SecureRandom()

    /** 登录会话期随机 state（16 字节 hex），exchange 请求体原样回传。 */
    fun newState(): String {
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /** 单次 percent-encode（form 语义空格 '+' → '%20'），杜绝双重编码。 */
    fun encode(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    /**
     * SDK `onError(CallBackData.code)` 分类（官方错误处理表）：
     * - `-4` 跳转飞书失败（未装飞书 / 被系统拦）⇒ 返回 null，调用方回落 WebView 兜底；
     * - `-3` 用户取消授权 ⇒ [LoginSignal.SsoDenied]；
     * - `-1` state 校验失败（非当前 SDK 请求的响应）⇒ [LoginSignal.StateMismatch]；
     * - 其余（`-2` 无授权码 / `-5` 授权失败 / `-6` 参数错误 / 未知）⇒ [LoginSignal.AuthorizationRejected]。
     */
    fun signalForError(code: String?): LoginSignal? = when (code?.trim()) {
        WEB_FALLBACK_ERROR -> null
        USER_CANCEL_ERROR -> LoginSignal.SsoDenied
        STATE_MISMATCH_ERROR -> LoginSignal.StateMismatch
        else -> LoginSignal.AuthorizationRejected
    }

    private const val WEB_FALLBACK_ERROR = "-4"
    private const val USER_CANCEL_ERROR = "-3"
    private const val STATE_MISMATCH_ERROR = "-1"
}
