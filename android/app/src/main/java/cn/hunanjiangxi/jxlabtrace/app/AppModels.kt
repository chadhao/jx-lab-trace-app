package cn.hunanjiangxi.jxlabtrace.app

import cn.hunanjiangxi.jxlabtrace.net.ApiError
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

// ===== 批次 A+B 契约模型（docs/APP-API-A-B.md V1.0，逐字段）=====
// 结构由 mimo 定（MIMO-TASK-AB-INTEGRATION 白名单「新建 app/ 数据层」）。

// ---- §3 GET /api/app/home ----

@Serializable
data class HomeMe(
    val open_id: String = "",
    val name: String = "",
    val roles: List<String> = emptyList(),
)

@Serializable
data class HomeLatest(
    val id: Long = 0,
    val code: String = "",
    val title: String = "",
    val status: String = "",
    /** `YYYY-MM-DD HH:MM:SS`（契约 §2）。 */
    val at: String = "",
)

@Serializable
data class HomeInProgressItem(
    val key: String = "",
    val label: String = "",
    val domain: String = "",
    val count: Int = 0,
    val latest: HomeLatest? = null,
)

@Serializable
data class HomeTodoItem(
    val key: String = "",
    val label: String = "",
    val domain: String = "",
    val count: Int = 0,
)

@Serializable
data class HomeRecentItem(
    val id: Long = 0,
    val at: String = "",
    val entity: String = "",
    val entity_id: Long = 0,
    val action: String = "",
    val field: String = "",
    val new_value: String = "",
    val reason: String = "",
    val actor_open_id: String = "",
    val actor: String = "",
)

/** GET /api/app/home 响应（§3.1）。 */
@Serializable
data class HomeOverview(
    val me: HomeMe = HomeMe(),
    val domains: List<String> = emptyList(),
    val in_progress: List<HomeInProgressItem> = emptyList(),
    val todos: List<HomeTodoItem> = emptyList(),
    val recent: List<HomeRecentItem> = emptyList(),
    val recent_source: String = "",
    val generated_at: String = "",
)

// ---- §4.1 预报列表 ----

/** 预报行（§4.1；主键字段名 `notice_id`）。 */
@Serializable
data class AppNotice(
    val notice_id: Long = 0,
    val notice_no: String = "",
    val batch_id: Long = 0,
    val our_batch_no: String = "",
    val customer_batch_no: String = "",
    val customer_name: String = "",
    val material_name: String = "",
    val seq_no: Int = 0,
    val plate_no: String = "",
    val driver: String = "",
    val phone: String = "",
    val status: String = "",
    /** `YYYY-MM-DD`；未确认到货时为空串。 */
    val arrive_date: String = "",
    val legacy: Boolean = false,
)

// ---- §4.2 到货确认 ----

@Serializable
data class ArriveConfirmResult(
    val confirmed: Boolean = false,
    val truck_lot_id: Long = 0,
    val truck_code: String = "",
    val truck_human: String = "",
    val notice_id: Long = 0,
    val arrive_date: String = "",
    val seq_no: Int = 0,
    val plate_no: String = "",
    val status: String = "",
)

// ---- §4.3 退车 ----

@Serializable
data class ReturnResult(
    val returned: Boolean = false,
    val truck_lot_id: Long = 0,
    val truck_code: String = "",
    val notice_id: Long = 0,
    val status: String = "",
    val bag_count: Int = 0,
)

// ---- §4.4 过磅 ----

@Serializable
data class TareHistory(
    val truck_id: Long = 0,
    val tare_weight: Double = 0.0,
    val arrive_date: String = "",
)

@Serializable
data class WeighResult(
    val weighed: Boolean = false,
    val truck_lot_id: Long = 0,
    val truck_code: String = "",
    val gross_weight: Double = 0.0,
    val tare_weight: Double = 0.0,
    val net_weight: Double = 0.0,
    /** `input`＝入参给定；`history`＝取自历史皮重。 */
    val tare_source: String = "",
    val tare_history: TareHistory? = null,
    val status: String = "",
)

// ---- §4.5 建袋打码 ----

@Serializable
data class AppBag(
    val bag_seq: Int = 0,
    val code: String = "",
    val human: String = "",
    val weight_allocated: Double? = null,
    /** 恒为 1（摊算标记）。 */
    val weight_is_allocated: Int = 0,
    val status: String = "",
)

@Serializable
data class BagGenResult(
    val generated: Int = 0,
    val truck_lot_id: Long = 0,
    val bags: List<AppBag> = emptyList(),
)

// ---- §4.6 标签补打 ----

@Serializable
data class AppLabel(
    val label_id: Long = 0,
    val code: String = "",
    val human: String = "",
    val kind: String = "",
    val is_reprint: Int = 0,
    val reason: String = "",
    val printed_by: String = "",
)

@Serializable
data class ReprintResult(
    val count: Int = 0,
    val labels: List<AppLabel> = emptyList(),
    val page_url: String = "",
)

// ---- §2.1 结构化拦截四字段 ----

/**
 * /api/app/ 前缀 4xx 统一拦截体：`{error, reason, owner, contact}`。
 * 解析失败（缺字段）返回 null —— 调用方回退既有错误映射。
 */
@Serializable
data class AppInterceptBody(
    val error: String = "",
    val reason: String = "",
    val owner: String = "",
    val contact: String = "",
) {
    fun toApiError(httpCode: Int): ApiError.Intercept =
        ApiError.Intercept(httpCode, error, reason, owner, contact)

    companion object {
        /** 从 4xx 响应体解析四字段；任一关键字段缺失 ⇒ null（不吞掉旧结构）。 */
        fun parse(bodyText: String, json: Json): AppInterceptBody? = runCatching {
            val obj = json.parseToJsonElement(bodyText) as? JsonObject ?: return null
            fun str(key: String): String =
                runCatching { obj[key]?.jsonPrimitive?.content }.getOrNull().orEmpty()

            val error = str("error")
            val reason = str("reason")
            val owner = str("owner")
            val contact = str("contact")
            // 四字段齐备才算结构化拦截（§2.1）；500 仅 {"error":"内部错误"} 不算。
            if (error.isBlank() || reason.isBlank() || owner.isBlank() || contact.isBlank()) {
                return null
            }
            AppInterceptBody(error = error, reason = reason, owner = owner, contact = contact)
        }.getOrNull()
    }
}
