package cn.hunanjiangxi.jxlabtrace.net

import cn.hunanjiangxi.jxlabtrace.log.AppLogger
import cn.hunanjiangxi.jxlabtrace.log.LogLevel
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/** 日志注入点（单测可替换为内存收集器）。 */
fun interface JxLogger {
    fun log(level: LogLevel, tag: String, message: String)

    companion object {
        val Default: JxLogger = JxLogger { level, tag, message ->
            AppLogger.log(level, tag, message)
        }
    }
}

/**
 * 网络层骨架（M01）：
 * - 统一 OkHttp 客户端（自动携带/落盘 jx_sid）；
 * - **提交前置探测**：写请求前先 HEAD /healthz（2s 超时），失败直接返回 Offline，不进入真实请求；
 * - 401/403/5xx/业务码/解析失败 → 对应 [ApiError] 领域错误（含中文 userMessage）。
 */
class ApiClient(
    baseUrl: String,
    cookieJar: CookieJar,
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val logger: JxLogger = JxLogger.Default,
    /** 全局网络态（M12）：false ⇒ 直接 Offline，不发出任何请求（生产注入 NetworkMonitor.online）。 */
    private val online: () -> Boolean = { true },
) {

    val httpClient: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(CLIENT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(CLIENT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(CLIENT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    /** 前置探测专用：2s 硬超时，避免断网时进入真实请求的长等待。 */
    private val healthClient: OkHttpClient = httpClient.newBuilder()
        .connectTimeout(HEALTH_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .readTimeout(HEALTH_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .writeTimeout(HEALTH_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .callTimeout(HEALTH_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .build()

    private val baseUrl: HttpUrl = (baseUrl.trimEnd('/') + "/").toHttpUrl()

    /** HEAD /healthz 前置探测：可达且 2xx 返回 true。 */
    suspend fun preflight(): Boolean = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(baseUrl.newBuilder().addPathSegment(HEALTH_PATH.trimStart('/'))!!.build())
            .head()
            .build()
        try {
            healthClient.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (_: IOException) {
            false
        }
    }

    /** 全局网络态直读（M04 扫码解析等不走前置探测的读路径自行判离线）。 */
    fun currentlyOnline(): Boolean = online()

    /** 拼接站内绝对地址（供需自定义状态码映射的请求使用，如 /api/recv/scan）。 */
    fun urlOf(path: String): HttpUrl = resolve(path)

    /**
     * 读请求统一入口（M03 起）：无前置探测（读操作），但受全局网络态门禁——
     * NetworkMonitor=false ⇒ 直接 [ApiError.Offline]，不发出任何请求（M12）。
     */
    suspend fun <T> executeRead(
        path: String,
        parse: (String) -> T,
    ): ApiResult<T> = withContext(Dispatchers.IO) {
        if (!online()) {
            logger.log(LogLevel.W, TAG, "全局网络态为离线，跳过读请求 GET $path（返回 Offline）")
            return@withContext ApiResult.Failure(ApiError.Offline())
        }
        val request = Request.Builder().url(resolve(path)).get().build()
        try {
            httpClient.newCall(request).execute().use { response -> mapResponse(response, parse) }
        } catch (e: IOException) {
            logger.log(LogLevel.E, TAG, "读请求 IO 失败 GET $path：${e.javaClass.simpleName}")
            ApiResult.Failure(ApiError.Offline())
        }
    }

    /**
     * 写请求统一入口：全局网络态门禁 → 前置 [preflight]，断网直接返回 [ApiError.Offline]，**不发出真实请求**。
     * [parse] 把 2xx 响应体解析为领域对象；envelope 业务码非 0 映射为 [ApiError.Biz]。
     */
    suspend fun <T> executeWrite(
        method: String,
        path: String,
        body: String? = null,
        parse: (String) -> T,
    ): ApiResult<T> = withContext(Dispatchers.IO) {
        if (!online()) {
            logger.log(
                LogLevel.E, TAG,
                "全局网络态为离线，跳过写请求 $method $path（返回 Offline，无任何请求发出）",
            )
            return@withContext ApiResult.Failure(ApiError.Offline())
        }
        if (!preflight()) {
            logger.log(
                LogLevel.E, TAG,
                "前置探测拦截：$HEALTH_PATH 不可达，跳过真实写请求 $method $path（返回 Offline，无 OkHttp 超时等待）",
            )
            return@withContext ApiResult.Failure(ApiError.Offline())
        }

        val requestBuilder = Request.Builder().url(resolve(path))
        when (method.uppercase()) {
            "GET" -> requestBuilder.get()
            "HEAD" -> requestBuilder.head()
            "DELETE" -> requestBuilder.delete()
            else -> requestBuilder.method(
                method.uppercase(),
                body?.toRequestBody(JSON_MEDIA_TYPE),
            )
        }

        try {
            httpClient.newCall(requestBuilder.build()).execute().use { response ->
                mapResponse(response, parse)
            }
        } catch (e: IOException) {
            logger.log(LogLevel.E, TAG, "写请求 IO 失败 $method $path：${e.javaClass.simpleName}")
            ApiResult.Failure(ApiError.Offline())
        }
    }

    private fun <T> mapResponse(response: Response, parse: (String) -> T): ApiResult<T> {
        val bodyText = response.body?.string().orEmpty()
        return when {
            response.code == 401 -> {
                logger.log(LogLevel.W, TAG, "HTTP 401 → Unauthorized")
                ApiResult.Failure(ApiError.Unauthorized())
            }
            response.code == 403 -> {
                logger.log(LogLevel.W, TAG, "HTTP 403 → Forbidden")
                ApiResult.Failure(ApiError.Forbidden())
            }
            response.code == 409 -> {
                // M12：业务状态冲突（409）——后端 jsonErrorHandler 统一 `{"error":"中文"}`，
                // 无机器码（B2 裁决 3）⇒ 映射为 Biz(HTTP_409, 后端话术)，
                // 交 ErrorMessages 按已知 409 文案增强「下一步」指引；未识别的原样透传。
                val text = extractConflictMessage(bodyText)
                logger.log(LogLevel.W, TAG, "HTTP 409 → Biz(HTTP_409)：$text")
                ApiResult.Failure(ApiError.Biz("HTTP_409", text))
            }
            !response.isSuccessful -> {
                logger.log(LogLevel.W, TAG, "HTTP ${response.code} → Server")
                ApiResult.Failure(ApiError.Server(response.code))
            }
            else -> {
                // 口径：2xx 的 body 必须是合法 JSON envelope，否则一律 Parse——
                // 防止垃圾 body 被恒等 parse 静默吞成 Success（SCOPE-APP 错误五分类）。
                val element: JsonElement = try {
                    json.parseToJsonElement(bodyText)
                } catch (e: Exception) {
                    logger.log(LogLevel.E, TAG, "2xx 响应非合法 JSON → Parse：${e.javaClass.simpleName}")
                    return ApiResult.Failure(ApiError.Parse(e))
                }
                if (element !is JsonObject) {
                    // envelope 必须是 JSON 对象；非对象（含被宽容解析的垃圾串）一律 Parse
                    logger.log(LogLevel.E, TAG, "2xx 响应非 JSON 对象 envelope → Parse")
                    return ApiResult.Failure(ApiError.Parse(IllegalStateException("envelope 不是 JSON 对象")))
                }
                bizCodeOf(element)?.let { (code, message) ->
                    logger.log(LogLevel.W, TAG, "业务错误码 $code → Biz")
                    return ApiResult.Failure(ApiError.Biz(code, message))
                }
                try {
                    ApiResult.Success(parse(bodyText))
                } catch (e: Exception) {
                    logger.log(LogLevel.E, TAG, "响应解析失败：${e.javaClass.simpleName}")
                    ApiResult.Failure(ApiError.Parse(e))
                }
            }
        }
    }

    /** 解析统一 envelope 的业务码：`{"code": <非0>, "message": "…"}` → Pair(code, message)。 */
    private fun bizCodeOf(element: JsonElement): Pair<String, String>? {
        val map = element as? JsonObject ?: return null
        return run {
            val codeElement = map["code"] ?: return null
            val code = codeElement.jsonPrimitive.content
            val isZero = code == "0" || code == "0.0" || code.toLongOrNull() == 0L
            if (isZero) return null
            val message = map["message"]?.jsonPrimitive?.content.orEmpty()
            code to message
        }
    }

    /**
     * M12：409 冲突体取话术——后端统一 `{"error": "…"}`（jsonErrorHandler），
     * 兼容 `{"message": "…"}`；均缺失时回退通用文案。
     */
    private fun extractConflictMessage(bodyText: String): String {
        val element = try {
            json.parseToJsonElement(bodyText)
        } catch (_: Exception) {
            null
        }
        val map = element as? JsonObject
        val text = map?.get("error")?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }
            ?: map?.get("message")?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }
        return text?.takeIf { it.isNotBlank() }
            ?: "业务状态冲突，请稍后重试；若反复出现请联系综合运营部。"
    }

    private fun resolve(path: String): HttpUrl =
        if (path.startsWith("http://") || path.startsWith("https://")) {
            path.toHttpUrl()
        } else {
            baseUrl.newBuilder().addEncodedPathSegments(path.trimStart('/'))!!.build()
        }

    companion object {
        const val HEALTH_PATH = "/healthz"
        const val HEALTH_TIMEOUT_MS = 2_000L
        private const val CLIENT_TIMEOUT_SECONDS = 10L
        private const val TAG = "ApiClient"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

