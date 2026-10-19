package cn.hunanjiangxi.jxlabtrace.scan

import cn.hunanjiangxi.jxlabtrace.net.ApiError
import cn.hunanjiangxi.jxlabtrace.net.ErrorMessages
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive

/** 确认卡展示字段（S03 ② 四字段）。 */
data class ScanField(val label: String, val value: String)

/** `GET /api/recv/scan` 定位成功的对象。 */
data class ScanInfo(
    val kind: String, // truck / bag（后端 ScanResult.kind）
    val code: String,
    val human: String,
    val typeLabel: String,
    val fields: List<ScanField>,
    val voided: Boolean,
)

/**
 * S03 失败五类（M04 判据 M4-2）：
 * 400 码非法 → NotOurs / 404 对象不存在 → UnknownCode / 409 业务拒绝 → Rejected
 * / 403 → NoPermission / 已作废 → Voided。**五类文案各不相同，禁止合并成「扫码失败」**。
 */
sealed interface ScanFailure {
    data class NotOurs(val message: String) : ScanFailure
    data class UnknownCode(val message: String) : ScanFailure
    data class Rejected(val code: String?, val message: String) : ScanFailure
    object NoPermission : ScanFailure
    object Voided : ScanFailure
}

/** 失败展示口径：标题（短）+ 明细 + 供排查的小字错误码。 */
data class ScanFailureDisplay(val title: String, val detail: String, val codeLine: String)

fun ScanFailure.display(): ScanFailureDisplay = when (this) {
    is ScanFailure.NotOurs -> ScanFailureDisplay(
        title = "不是本系统的码",
        detail = message.ifBlank { "该码不归属本系统，请确认标签来源。" },
        codeLine = "HTTP 400 · NOT_OURS",
    )
    is ScanFailure.UnknownCode -> ScanFailureDisplay(
        title = "系统里没有这个码",
        detail = message.ifBlank { "格式正确但系统内不存在，请联系综合运营部核对。" },
        codeLine = "HTTP 404 · UNKNOWN_CODE",
    )
    is ScanFailure.Rejected -> ScanFailureDisplay(
        title = "业务规则拒绝",
        // 已知 409 业务码给专属话术，其余透传后端中文 message（与 M12 ErrorMessages 同源）
        detail = ErrorMessages.of(ApiError.Biz(code.orEmpty(), message)),
        codeLine = listOfNotNull("HTTP 409", code).joinToString(" · "),
    )
    ScanFailure.NoPermission -> ScanFailureDisplay(
        title = "无权限操作",
        detail = ErrorMessages.of(ApiError.Forbidden()),
        codeLine = "HTTP 403 · FORBIDDEN",
    )
    ScanFailure.Voided -> ScanFailureDisplay(
        title = "该对象已作废",
        detail = "该对象已作废，请到追溯页查看作废原因与去向。",
        codeLine = "HTTP 409 · ALREADY_VOID",
    )
}

/**
 * HTTP 状态 → 五类失败的纯映射（M4-2 单测入口）。
 * 非这四种状态返回 null，由调用方按网络层口径处理（5xx → Server 等）。
 */
fun scanFailureOf(httpCode: Int, errorText: String?, body: String? = null): ScanFailure? =
    when (httpCode) {
        400 -> ScanFailure.NotOurs(errorText.orEmpty())
        404 -> ScanFailure.UnknownCode(errorText.orEmpty())
        403 -> ScanFailure.NoPermission
        409 ->
            if (!errorText.isNullOrBlank() && errorText.contains("作废")) {
                ScanFailure.Voided
            } else {
                ScanFailure.Rejected(code = extractBizCode(body), message = errorText.orEmpty())
            }
        else -> null
    }

/** 409 body 若带机器码（`{"code":…}`）则取之；后端当前只有 `{"error": text}`，可为 null。 */
private fun extractBizCode(body: String?): String? {
    if (body.isNullOrBlank()) return null
    return runCatching {
        val element = Json.parseToJsonElement(body)
        (element as? JsonObject)?.get("code").asContentOrNull()
    }.getOrNull()
}

private fun kotlinx.serialization.json.JsonElement?.asContentOrNull(): String? =
    runCatching { this?.jsonPrimitive?.content }.getOrNull()

/** 扫码成功的识别回显（震动+蜂鸣+绿边+尾 6 位大字，S03 ①）。 */
data class ScanEcho(val tail6: String)

/** HID 通道的本地拒绝提示（红边 + 原因，不静默丢弃）。 */
data class ScanHint(val message: String)

/**
 * 扫码取景状态机（M04）：
 * `Idle → Scanning → Confirm(info) → Submitting → Done / Failure(reason)`。
 */
sealed interface ScanUiState {
    object Idle : ScanUiState
    object Scanning : ScanUiState
    data class Confirm(val info: ScanInfo) : ScanUiState
    object Submitting : ScanUiState
    object Done : ScanUiState
    data class Failure(val reason: ScanFailure) : ScanUiState
}

/** 相机权限门禁（M4-5）：拒绝 ⇒ 取景层给「去设置」而非白屏。 */
enum class CameraGate { Preview, Denied }

fun cameraGateOf(hasCameraPermission: Boolean): CameraGate =
    if (hasCameraPermission) CameraGate.Preview else CameraGate.Denied

/**
 * 从后端 `{"found":true,"result":{…}}` 解析 [ScanInfo]（200 且对象存在时）。
 * `status` 含「作废」⇒ [ScanInfo.voided]=true，由 ViewModel 转 Failure.Voided。
 */
fun scanInfoOf(result: JsonObject): ScanInfo {
    val kind = result["kind"].asContentOrNull().orEmpty()
    val code = result["code"].asContentOrNull().orEmpty()
    val human = result["human"].asContentOrNull()
        ?: runCatching { CodeParser.toHuman(code) }.getOrDefault(code)

    val obj = (if (kind == "truck") result["truck"] else result["bag"]) as? JsonObject
    val status = obj?.get("status").asContentOrNull().orEmpty()

    val fields = when (kind) {
        "truck" -> listOf(
            ScanField("车牌", obj.string("plate_no")),
            ScanField("物料", obj.string("material_name")),
            ScanField("车序", obj.int("seq_no").toString()),
            ScanField("状态", status),
        )
        else -> listOf(
            ScanField("袋序", obj.int("bag_seq").toString()),
            ScanField("分配重量", obj.weight("weight_allocated")),
            ScanField("状态", status),
            ScanField("车次", obj.int("truck_lot_id").toString()),
        )
    }

    return ScanInfo(
        kind = kind,
        code = code,
        human = human,
        typeLabel = if (kind == "truck") "车次" else "原料吨袋",
        fields = fields,
        voided = status.contains("作废"),
    )
}

private fun JsonObject?.string(key: String): String =
    this?.get(key).asContentOrNull().orEmpty()

private fun JsonObject?.int(key: String): Int =
    this?.get(key).asContentOrNull()?.toIntOrNull() ?: 0

private fun JsonObject?.weight(key: String): String {
    val element = this?.get(key) ?: return "—"
    val value = runCatching { element.jsonPrimitive.doubleOrNull }.getOrNull() ?: return "—"
    return if (value == value.toLong().toDouble()) "${value.toLong()} kg" else "$value kg"
}
