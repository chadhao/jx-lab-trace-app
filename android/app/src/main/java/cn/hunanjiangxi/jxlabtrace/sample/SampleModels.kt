package cn.hunanjiangxi.jxlabtrace.sample

import cn.hunanjiangxi.jxlabtrace.net.ApiClient
import cn.hunanjiangxi.jxlabtrace.net.ApiResult
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

// ===== M06 取样（S07 · 生产班组）=====
// 后端契约（internal/httpapi/sample.go 实读）：
//   GET  /api/sample/targets?target_type=车次|生产批|成品批 → {count, rows:[SampleTarget…]}
//   POST /api/sample/take  —— 后端 TakeInput 现仅消费 {code}；
//        任务包 M6-2 要求请求体含 target_type/target_id/sample_weight/samples 且不含 sampled_by
//        ⇒ APP 按任务包口径组装（多余键后端 json.Decode 忽略），code 一并带上保证现网可用。

/** 取样目标行（SampleTarget 读模型）。 */
@Serializable
data class SampleTarget(
    val id: Long = 0,
    val code: String = "",
    val human: String = "",
    val status: String = "",
    val extra: String = "",
)

/** 对象三选（S07 seg3）。 */
enum class SampleTargetType(val label: String, val apiValue: String) {
    TRUCK("车次", "车次"),
    BATCH("生产批", "生产批"),
    FG_LOT("成品批", "成品批"),
}

/** 取样类型（份样默认）。 */
enum class SampleKind(val label: String) {
    INCREMENTAL("份样"),
    BULK("大样"),
    RETENTION("保留样"),
}

/** POST /api/sample/take 请求体（M6-2 单测断言形状）。 */
@Serializable
data class SampleTakeBody(
    val code: String,
    val target_type: String,
    val target_id: Long,
    val sample_weight: Double,
    val samples: Int,
    /** 刻意不含 sampled_by —— 由后端会话定（判据 M6-2）。 */
)

/**
 * 取样表单（判据 M6-1）：默认值填充后即有效（必填 ≤1 ⇒ 实际 0）。
 * 默认：类型=份样、取样量=500g、份样数=按约定 5。
 */
object SampleForm {

    const val DEFAULT_WEIGHT_G = 500.0
    const val DEFAULT_SAMPLES = 5

    /** 默认表单（未选目标前）。 */
    fun defaults(): SampleTakeBody = SampleTakeBody(
        code = "",
        target_type = SampleTargetType.TRUCK.apiValue,
        target_id = 0L,
        sample_weight = DEFAULT_WEIGHT_G,
        samples = DEFAULT_SAMPLES,
    )

    /** 表单有效：已定位目标（code 非空且 target_id>0）+ 取样量>0 + 份数≥1。 */
    fun isValid(body: SampleTakeBody): Boolean =
        body.code.isNotBlank() &&
            body.target_id > 0 &&
            body.sample_weight > 0 &&
            body.samples >= 1

    /** 组装提交体（不含 sampled_by）。 */
    fun build(
        code: String,
        targetType: SampleTargetType,
        targetId: Long,
        weightG: Double = DEFAULT_WEIGHT_G,
        samples: Int = DEFAULT_SAMPLES,
    ): SampleTakeBody = SampleTakeBody(
        code = code,
        target_type = targetType.apiValue,
        target_id = targetId,
        sample_weight = weightG,
        samples = samples,
    )

    /** 序列化为 JSON（提交与单测共用同一出口，防「测试形状 ≠ 线上形状」）。 */
    fun toJson(body: SampleTakeBody): String = buildString {
        append("{")
        append("\"code\":\"${body.code}\",")
        append("\"target_type\":\"${body.target_type}\",")
        append("\"target_id\":${body.target_id},")
        append("\"sample_weight\":${body.sample_weight},")
        append("\"samples\":${body.samples}")
        append("}")
    }
}

/** 取样仓储。 */
class SampleRepository(
    private val api: ApiClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {

    /** 对象列表（三选一拉取）。 */
    suspend fun targets(type: SampleTargetType): ApiResult<List<SampleTarget>> {
        val path = "/api/sample/targets?target_type=" +
            java.net.URLEncoder.encode(type.apiValue, "UTF-8")
        return api.executeRead(path) { body ->
            val obj = json.parseToJsonElement(body) as? JsonObject
                ?: error("envelope 不是对象")
            val arr = obj["rows"] as? JsonArray ?: emptyList()
            arr.mapNotNull { el ->
                val o = el as? JsonObject ?: return@mapNotNull null
                SampleTarget(
                    id = o.long("id"),
                    code = o.str("code"),
                    human = o.str("human"),
                    status = o.str("status"),
                    extra = o.str("extra"),
                )
            }
        }
    }

    /** 取样提交。 */
    suspend fun take(body: SampleTakeBody): ApiResult<Unit> =
        api.executeWrite("POST", "/api/sample/take", SampleForm.toJson(body)) {
            // 响应形状随后端版本变化；提交成功以 2xx envelope 为准
            Unit
        }

    private fun JsonObject.str(key: String): String =
        get(key)?.let { el -> runCatching { el.jsonPrimitive.content }.getOrNull() }.orEmpty()

    private fun JsonObject.long(key: String): Long = str(key).toLongOrNull() ?: 0L
}
