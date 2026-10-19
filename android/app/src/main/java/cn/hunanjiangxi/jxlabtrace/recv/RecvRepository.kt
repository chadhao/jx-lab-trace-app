package cn.hunanjiangxi.jxlabtrace.recv

import cn.hunanjiangxi.jxlabtrace.net.ApiClient
import cn.hunanjiangxi.jxlabtrace.net.ApiResult
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// ===== M05 收货线（S04/S05/S06）数据层 =====
// 后端契约（internal/httpapi/recv.go 实读）：
//   GET  /api/recv/notices               → {count, rows:[ArrivalNotice…]}
//   POST /api/recv/arrivals              {notice_id} → {confirmed, row:TruckLot}
//   POST /api/recv/trucks/:id/weigh      {gross_weight, tare_weight} → {weighed, row}
//   POST /api/recv/trucks/:id/bags       {count} → {generated, rows:[Bag…]}
//   POST /api/recv/labels/print          {codes[]} → {count, labels, page_url}
//   POST /api/recv/labels/reprint        {codes[], reason} → 同上
//   POST /api/recv/trucks/:id/return     {reason?} → {returned, row}
//   GET  /api/recv/trucks/:id            → {row}
//   GET  /api/recv/trucks/:id/bags       → {count, rows}

/** 预报行（GET /api/recv/notices · ArrivalNotice 读模型）。 */
@Serializable
data class RecvNotice(
    val id: Long = 0,
    val notice_no: String = "",
    val batch_id: Long = 0,
    val our_batch_no: String = "",
    val customer_batch_no: String = "",
    val customer_name: String = "",
    val material_name: String = "",
    val plate_no: String = "",
    val driver: String = "",
    val phone: String = "",
    val seq_no: Int = 0,
    val arrive_date: String = "",
    val status: String = "",
    val created_at: String? = null,
)

/** 车次行（TruckLot 读模型 · 过磅上下文）。 */
@Serializable
data class RecvTruck(
    val id: Long = 0,
    val code: String = "",
    val human: String = "",
    val notice_no: String = "",
    val customer_name: String = "",
    val material_name: String = "",
    val plate_no: String = "",
    val seq_no: Int = 0,
    val arrive_date: String = "",
    val gross_weight: Double? = null,
    val tare_weight: Double? = null,
    val net_weight: Double? = null,
    val bag_count: Int = 0,
    val status: String = "",
)

/** 袋行（Bag 读模型）。 */
@Serializable
data class RecvBag(
    val id: Long = 0,
    val code: String = "",
    val human: String = "",
    val truck_lot_id: Long = 0,
    val bag_seq: Int = 0,
    val weight_allocated: Double? = null,
    val status: String = "",
)

/** 过磅入参（吨）。 */
@Serializable
data class WeighBody(val gross_weight: Double, val tare_weight: Double)

/** 收货域仓储（全部走 executeRead/executeWrite ⇒ 自带离线门禁与前置探测）。 */
class RecvRepository(
    private val api: ApiClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {

    /** 预报列表（读）。调用方按 D23 本地过滤当日/车牌。 */
    suspend fun listNotices(): ApiResult<List<RecvNotice>> =
        api.executeRead("/api/recv/notices") { body -> parseRows(body, "rows", ::parseNotice) }

    /** 到货确认：选已有预报 ⇒ POST arrivals {notice_id}。 */
    suspend fun confirmArrival(noticeId: Long): ApiResult<RecvTruck> =
        api.executeWrite("POST", "/api/recv/arrivals", """{"notice_id":$noticeId}""") { body ->
            val row = json.parseToJsonElement(body).jsonObject["row"]!!.jsonObject
            parseTruck(row)
        }

    /** 过磅。 */
    suspend fun weigh(truckId: Long, gross: Double, tare: Double): ApiResult<RecvTruck> =
        api.executeWrite(
            "POST",
            "/api/recv/trucks/$truckId/weigh",
            """{"gross_weight":$gross,"tare_weight":$tare}""",
        ) { body ->
            parseTruck(json.parseToJsonElement(body).jsonObject["row"]!!.jsonObject)
        }

    /** 批量建袋（后端一次性生成；重试须幂等——先查已有袋，见 [ensureBags]）。 */
    suspend fun generateBags(truckId: Long, count: Int): ApiResult<List<RecvBag>> =
        api.executeWrite("POST", "/api/recv/trucks/$truckId/bags", """{"count":$count}""") { body ->
            parseRows(body, "rows", ::parseBag)
        }

    /** 已有袋列表（幂等恢复入口）。 */
    suspend fun listBags(truckId: Long): ApiResult<List<RecvBag>> =
        api.executeRead("/api/recv/trucks/$truckId/bags") { body -> parseRows(body, "rows", ::parseBag) }

    /** 单车次（过磅页上下文 / 历史皮重）。 */
    suspend fun getTruck(truckId: Long): ApiResult<RecvTruck> =
        api.executeRead("/api/recv/trucks/$truckId") { body ->
            parseTruck(json.parseToJsonElement(body).jsonObject["row"]!!.jsonObject)
        }

    /** 标签打印（首打）。 */
    suspend fun printLabels(codes: List<String>): ApiResult<LabelPrintResponse> =
        api.executeWrite("POST", "/api/recv/labels/print", codesBody(codes, null)) { body ->
            json.decodeFromString(LabelPrintResponse.serializer(), body)
        }

    /** 标签补打（reason 必填 —— 提交前由 ReprintForm 校验）。 */
    suspend fun reprintLabels(codes: List<String>, reason: String): ApiResult<LabelPrintResponse> =
        api.executeWrite("POST", "/api/recv/labels/reprint", codesBody(codes, reason)) { body ->
            json.decodeFromString(LabelPrintResponse.serializer(), body)
        }

    /** 退车（二次确认后调用；reason 可选）。 */
    suspend fun returnTruck(truckId: Long, reason: String? = null): ApiResult<RecvTruck> =
        api.executeWrite(
            "POST",
            "/api/recv/trucks/$truckId/return",
            """{"reason":${reason?.let { "\"${it.replace("\"", "\\\"")}\"" } ?: "null"}}""",
        ) { body ->
            parseTruck(json.parseToJsonElement(body).jsonObject["row"]!!.jsonObject)
        }

    private fun codesBody(codes: List<String>, reason: String?): String {
        val arr = codes.joinToString(",", "[", "]") { "\"$it\"" }
        return if (reason == null) {
            """{"codes":$arr}"""
        } else {
            """{"codes":$arr,"reason":"${reason.replace("\"", "\\\"")}"}"""
        }
    }

    // ---- 解析 ----

    private fun <T> parseRows(body: String, key: String, item: (JsonObject) -> T): List<T> {
        val obj = json.parseToJsonElement(body) as? JsonObject
            ?: error("envelope 不是对象")
        val arr = obj[key] as? JsonArray ?: return emptyList()
        return arr.mapNotNull { el -> (el as? JsonObject)?.let(item) }
    }

    private fun parseNotice(obj: JsonObject): RecvNotice = RecvNotice(
        id = obj.long("id"),
        notice_no = obj.str("notice_no"),
        batch_id = obj.long("batch_id"),
        our_batch_no = obj.str("our_batch_no"),
        customer_batch_no = obj.str("customer_batch_no"),
        customer_name = obj.str("customer_name"),
        material_name = obj.str("material_name"),
        plate_no = obj.str("plate_no"),
        driver = obj.str("driver"),
        phone = obj.str("phone"),
        seq_no = obj.long("seq_no").toInt(),
        arrive_date = obj.str("arrive_date"),
        status = obj.str("status"),
        created_at = obj.str("created_at").ifBlank { null },
    )

    private fun parseTruck(obj: JsonObject): RecvTruck = RecvTruck(
        id = obj.long("id"),
        code = obj.str("code"),
        human = obj.str("human"),
        notice_no = obj.str("notice_no"),
        customer_name = obj.str("customer_name"),
        material_name = obj.str("material_name"),
        plate_no = obj.str("plate_no"),
        seq_no = obj.long("seq_no").toInt(),
        arrive_date = obj.str("arrive_date"),
        gross_weight = obj.num("gross_weight"),
        tare_weight = obj.num("tare_weight"),
        net_weight = obj.num("net_weight"),
        bag_count = obj.long("bag_count").toInt(),
        status = obj.str("status"),
    )

    private fun parseBag(obj: JsonObject): RecvBag = RecvBag(
        id = obj.long("id"),
        code = obj.str("code"),
        human = obj.str("human"),
        truck_lot_id = obj.long("truck_lot_id"),
        bag_seq = obj.long("bag_seq").toInt(),
        weight_allocated = obj.num("weight_allocated"),
        status = obj.str("status"),
    )

    private fun JsonObject.str(key: String): String =
        get(key)?.let { el -> runCatching { el.jsonPrimitive.content }.getOrNull() }.orEmpty()

    private fun JsonObject.long(key: String): Long =
        str(key).toLongOrNull() ?: 0L

    private fun JsonObject.num(key: String): Double? =
        get(key)?.let { el -> runCatching { el.jsonPrimitive.doubleOrNull }.getOrNull() }
}

/** 打印响应（{count, labels, page_url}）。 */
@Serializable
data class LabelPrintResponse(
    val count: Int = 0,
    val page_url: String = "",
)
