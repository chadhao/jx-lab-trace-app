package cn.hunanjiangxi.jxlabtrace.scan

import cn.hunanjiangxi.jxlabtrace.net.ApiClient
import cn.hunanjiangxi.jxlabtrace.net.ApiError
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 扫码解析服务：`GET /api/recv/scan?code=…`（M04）。
 *
 * **解析是读操作，不走前置探测**（任务包 §2 明示），直接 GET；HTTP 状态映射到五类 Failure：
 * 400 NotOurs / 404 UnknownCode / 409 Rejected·Voided / 403 NoPermission；
 * 401/网络层错误走 [ScanOutcome.NetFailed]（401 由 ViewModel 经 withSessionRetry 重放）。
 */
class ScanService(
    private val api: ApiClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {

    sealed interface Outcome {
        data class Found(val info: ScanInfo) : Outcome
        data class Failed(val failure: ScanFailure) : Outcome
        data class NetFailed(val error: ApiError) : Outcome
    }

    suspend fun resolve(raw: String): Outcome = withContext(Dispatchers.IO) {
        if (!api.currentlyOnline()) {
            return@withContext Outcome.NetFailed(ApiError.Offline())
        }
        val code = CodeParser.normalize(raw)
        val url = api.urlOf(SCAN_PATH).newBuilder()
            .addQueryParameter("code", code)
            .build()
        val request = okhttp3.Request.Builder().url(url).get().build()
        try {
            api.httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                when {
                    response.code == 401 -> Outcome.NetFailed(ApiError.Unauthorized())
                    response.code == 403 -> Outcome.Failed(ScanFailure.NoPermission)
                    response.isSuccessful -> parseFound(body)
                    else -> {
                        val errorText = extractError(body)
                        scanFailureOf(response.code, errorText, body)
                            ?.let { Outcome.Failed(it) }
                            ?: Outcome.NetFailed(ApiError.Server(response.code))
                    }
                }
            }
        } catch (e: IOException) {
            Outcome.NetFailed(ApiError.Offline())
        } catch (e: Exception) {
            Outcome.NetFailed(ApiError.Parse(e))
        }
    }

    private fun parseFound(body: String): Outcome {
        val element = json.parseToJsonElement(body)
        val obj = element as? JsonObject
            ?: return Outcome.NetFailed(ApiError.Parse(IllegalStateException("envelope 不是 JSON 对象")))
        val result = obj["result"] as? JsonObject
            ?: return Outcome.NetFailed(ApiError.Parse(IllegalStateException("缺少 result 字段")))
        val info = scanInfoOf(result)
        return if (info.voided) Outcome.Failed(ScanFailure.Voided) else Outcome.Found(info)
    }

    private fun extractError(body: String): String? = runCatching {
        val obj = json.parseToJsonElement(body) as? JsonObject ?: return null
        obj["error"]?.jsonPrimitive?.content
            ?: obj["message"]?.jsonPrimitive?.content
    }.getOrNull()

    companion object {
        const val SCAN_PATH = "/api/recv/scan"
    }
}
