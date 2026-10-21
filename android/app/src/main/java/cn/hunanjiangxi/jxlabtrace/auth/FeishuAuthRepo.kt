package cn.hunanjiangxi.jxlabtrace.auth

import cn.hunanjiangxi.jxlabtrace.log.LogLevel
import cn.hunanjiangxi.jxlabtrace.net.ApiClient
import cn.hunanjiangxi.jxlabtrace.net.JxLogger
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * REQ-05：exchange 请求体 source 标识——后端收到 `"source":"sdk"` 才走
 * passport.feishu.cn/suite/passport/oauth/token 移动端契约（与网页端 v2 端点可区分，
 * 错误格式 `code=0 The code is not found`）。常量集中此处，序列化随 ExchangeBody 发出。
 */
private const val EXCHANGE_SOURCE_SDK = "sdk"

/**
 * exchange 请求体（后端 REQ-03 口径 {code,state} + REQ-05 source=sdk）。
 * [source] 不设默认值：kotlinx `encodeDefaults=false` 下带默认值的字段不会被序列化。
 */
@Serializable
private data class ExchangeBody(
    val code: String,
    val state: String,
    val source: String,
)

/**
 * Applink 授权的两个网络动作（MIMO-TASK-APPLINK）：
 * - `GET /api/auth/feishu/start`（followRedirects=false）→ 302 Location 作 authorize URL
 *   （client_id/scope 由后端统一维护，APP 不硬编码）；
 * - `POST /api/auth/feishu/exchange` {code,state} → 2xx 即成功
 *   （Set-Cookie `jx_sid` 由共享 JxCookieJar 自动落盘）；401 透出后端 `{"error":…}` 话术。
 *
 * 认证路径不做 healthz 前置探测（登录前探测无意义；IO 异常按离线处理）。
 */
class FeishuAuthRepo(
    private val api: ApiClient,
    private val logger: JxLogger = JxLogger.Default,
) {

    private val json = Json { ignoreUnknownKeys = true }

    /** 派生客户端：禁跟随重定向（用于取 302 Location）；CookieJar 随共享 httpClient 继承。 */
    private val noRedirectClient: OkHttpClient by lazy {
        api.httpClient.newBuilder().followRedirects(false).followSslRedirects(false).build()
    }

    sealed interface StartOutcome {
        /** 拿到 authorize URL（飞书 302 Location）。 */
        data class Authorized(val authorizeUrl: String) : StartOutcome

        /** 非 302 / 无 Location / IO 失败——调用方回落 WebView 流程。 */
        object Failed : StartOutcome
    }

    /** 取授权 URL：GET start，仅接受 3xx Location。 */
    suspend fun authorizeUrl(): StartOutcome = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(api.urlOf(START_PATH)).get().build()
        try {
            noRedirectClient.newCall(request).execute().use { response ->
                val location = response.header("Location")
                if (response.code in 300..399 && !location.isNullOrBlank()) {
                    logger.log(LogLevel.I, TAG, "start ${response.code} → authorize URL 已取得")
                    StartOutcome.Authorized(location)
                } else {
                    logger.log(LogLevel.W, TAG, "start 未给 3xx Location：HTTP ${response.code}")
                    StartOutcome.Failed
                }
            }
        } catch (e: IOException) {
            logger.log(LogLevel.E, TAG, "start 请求失败：${e.javaClass.simpleName}")
            StartOutcome.Failed
        }
    }

    sealed interface ExchangeOutcome {
        object Ok : ExchangeOutcome

        /** 401 等拒绝：[message] 为后端 error 话术（拒绝卡原样展示）。 */
        data class Rejected(val message: String) : ExchangeOutcome

        /** 离线（全局网络态 / IO 失败）。 */
        object Offline : ExchangeOutcome
    }

    /** 授权码直换会话：2xx ⇒ Ok（sid 已入 CookieJar）；401 ⇒ Rejected(后端 error 文案)。 */
    suspend fun exchange(code: String, state: String): ExchangeOutcome = withContext(Dispatchers.IO) {
        if (!api.currentlyOnline()) return@withContext ExchangeOutcome.Offline
        val body = json.encodeToString(
            ExchangeBody.serializer(),
            ExchangeBody(code, state, EXCHANGE_SOURCE_SDK),
        ).toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder().url(api.urlOf(EXCHANGE_PATH)).post(body).build()
        try {
            api.httpClient.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                when {
                    response.isSuccessful -> {
                        logger.log(LogLevel.I, TAG, "exchange 2xx → 会话已换取")
                        ExchangeOutcome.Ok
                    }
                    response.code == 401 -> {
                        val message = extractError(text) ?: DEFAULT_DENIED_TEXT
                        logger.log(LogLevel.W, TAG, "exchange 401：$message")
                        ExchangeOutcome.Rejected(message)
                    }
                    else -> {
                        val message = extractError(text) ?: "授权未完成（HTTP ${response.code}），请重新发起登录"
                        logger.log(LogLevel.W, TAG, "exchange HTTP ${response.code}：$message")
                        ExchangeOutcome.Rejected(message)
                    }
                }
            }
        } catch (e: IOException) {
            logger.log(LogLevel.E, TAG, "exchange IO 失败：${e.javaClass.simpleName}")
            ExchangeOutcome.Offline
        }
    }

    /** 取后端统一错误体 `{"error":"…"}` 话术；缺失/非 JSON 返回 null。 */
    private fun extractError(bodyText: String): String? = runCatching {
        val element = json.parseToJsonElement(bodyText)
        (element as? JsonObject)?.get("error")?.jsonPrimitive?.content
    }.getOrNull()?.takeIf { it.isNotBlank() }

    private companion object {
        const val TAG = "FeishuAuth"
        const val START_PATH = "/api/auth/feishu/start"
        const val EXCHANGE_PATH = "/api/auth/feishu/exchange"
        const val DEFAULT_DENIED_TEXT = "授权码无效或已过期，请重新发起登录"
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
