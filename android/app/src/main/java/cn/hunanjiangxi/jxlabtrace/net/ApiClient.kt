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

    /**
     * 写请求统一入口：先 [preflight]，断网直接返回 [ApiError.Offline]，**不发出真实请求**。
     * [parse] 把 2xx 响应体解析为领域对象；envelope 业务码非 0 映射为 [ApiError.Biz]。
     */
    suspend fun <T> executeWrite(
        method: String,
        path: String,
        body: String? = null,
        parse: (String) -> T,
    ): ApiResult<T> = withContext(Dispatchers.IO) {
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
            !response.isSuccessful -> {
                logger.log(LogLevel.W, TAG, "HTTP ${response.code} → Server")
                ApiResult.Failure(ApiError.Server(response.code))
            }
            else -> {
                bizCodeOf(bodyText)?.let { (code, message) ->
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
    private fun bizCodeOf(bodyText: String): Pair<String, String>? {
        if (bodyText.isBlank()) return null
        return try {
            val element: JsonElement = json.parseToJsonElement(bodyText)
            val map = element as? JsonObject ?: return null
            val codeElement = map["code"] ?: return null
            val code = codeElement.jsonPrimitive.content
            val isZero = code == "0" || code == "0.0" || code.toLongOrNull() == 0L
            if (isZero) return null
            val message = map["message"]?.jsonPrimitive?.content.orEmpty()
            code to message
        } catch (_: Exception) {
            null
        }
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

