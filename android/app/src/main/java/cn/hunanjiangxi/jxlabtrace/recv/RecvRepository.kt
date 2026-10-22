package cn.hunanjiangxi.jxlabtrace.recv

import cn.hunanjiangxi.jxlabtrace.app.AppBag
import cn.hunanjiangxi.jxlabtrace.app.ArriveConfirmResult
import cn.hunanjiangxi.jxlabtrace.app.BagGenResult
import cn.hunanjiangxi.jxlabtrace.app.ReprintResult
import cn.hunanjiangxi.jxlabtrace.app.ReturnResult
import cn.hunanjiangxi.jxlabtrace.app.WeighResult
import cn.hunanjiangxi.jxlabtrace.net.ApiClient
import cn.hunanjiangxi.jxlabtrace.net.ApiResult
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

// ===== 收货域数据层（MIMO-TASK-AB-INTEGRATION · 批次 B）=====
// 契约唯一依据 docs/APP-API-A-B.md V1.0 §4（/api/app/*，Cookie jx_sid）：
//   GET  /api/app/recv/notices[?mode=]     → {count, notices[]}
//   POST /api/app/recv/arrive-confirm      {notice_id, remark?} → 扁平 ArriveConfirmResult
//   POST /api/app/recv/return              {notice_id, reason?} → 扁平 ReturnResult
//   POST /api/app/recv/weigh               {truck_lot_id, gross_weight, tare_weight?} → 扁平 WeighResult
//   POST /api/app/recv/bag-gen             {truck_lot_id, count} → {generated, truck_lot_id, bags[]}
//   POST /api/app/label/reprint            {bag_code, reason} → {count, labels[], page_url}
// 辅助只读（getTruck/listBags）不在本批契约：沿用 Web 端 /api/recv/trucks/:id[/bags]
// （同店同 Cookie，契约 §0 不耦合字段形状——APP 侧解析维持既有行模型）。
// 序号一律后端分配，APP 不计算不回传（§2）。

/** 预报行（S04 列表/拦截页本地过滤用；id＝契约 notice_id）。 */
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
    val legacy: Boolean = false,
)

/** 车次行（过磅/建袋上下文 · Web 只读行模型）。 */
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

/** 袋行（建袋结果/幂等恢复）。 */
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

/** 过磅入参（吨；tare 可空 ⇒ 省略字段让服务端取历史皮重，§4.4）。 */
@Serializable
data class WeighBody(val gross_weight: Double, val tare_weight: Double?)

/** 收货域仓储（六动作走 executeRead/executeWrite ⇒ 离线门禁+前置探测+结构化拦截）。 */
class RecvRepository(
    private val api: ApiClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {

    /** §4.1 预报列表（读）。mode 缺省=全部；调用方按 D23 本地过滤当日/车牌。 */
    suspend fun listNotices(mode: String? = null): ApiResult<List<RecvNotice>> =
        api.executeRead(
            if (mode.isNullOrBlank()) "/api/app/recv/notices" else "/api/app/recv/notices?mode=$mode",
        ) { body -> parseNotices(body) }

    /** §4.2 到货确认（写）。notice_id 必填；返回车次摘要（truck_lot_id 供后续动作）。 */
    suspend fun confirmArrival(noticeId: Long, remark: String? = null): ApiResult<RecvTruck> =
        api.executeWrite(
            "POST",
            "/api/app/recv/arrive-confirm",
            buildJsonObject {
                put("notice_id", noticeId)
                remark?.takeIf { it.isNotBlank() }?.let { put("remark", it) }
            }.toString(),
        ) { body ->
            val r = json.decodeFromString(ArriveConfirmResult.serializer(), body)
            RecvTruck(
                id = r.truck_lot_id,
                code = r.truck_code,
                human = r.truck_human,
                plate_no = r.plate_no,
                seq_no = r.seq_no,
                arrive_date = r.arrive_date,
                status = r.status,
            )
        }

    /** §4.3 退车（写）。按 notice_id 定位车次（APP 侧不做无预报直达到货）。 */
    suspend fun returnTruck(noticeId: Long, reason: String? = null): ApiResult<ReturnResult> =
        api.executeWrite(
            "POST",
            "/api/app/recv/return",
            buildJsonObject {
                put("notice_id", noticeId)
                reason?.takeIf { it.isNotBlank() }?.let { put("reason", it) }
            }.toString(),
        ) { body -> json.decodeFromString(ReturnResult.serializer(), body) }

    /** §4.4 过磅（写）。tare=null ⇒ 省略字段（服务端取历史皮重或 400 要求人工录入）。 */
    suspend fun weigh(
        truckLotId: Long,
        gross: Double,
        tare: Double? = null,
    ): ApiResult<RecvTruck> =
        api.executeWrite(
            "POST",
            "/api/app/recv/weigh",
            buildJsonObject {
                put("truck_lot_id", truckLotId)
                put("gross_weight", gross)
                tare?.let { put("tare_weight", it) }
            }.toString(),
        ) { body ->
            val r = json.decodeFromString(WeighResult.serializer(), body)
            RecvTruck(
                id = r.truck_lot_id,
                code = r.truck_code,
                gross_weight = r.gross_weight,
                tare_weight = r.tare_weight,
                net_weight = r.net_weight,
                status = r.status,
            )
        }

    /** §4.5 建袋打码（写）。袋序后端分配；返回本车全量袋（按 bag_seq 升序）。 */
    suspend fun generateBags(truckLotId: Long, count: Int): ApiResult<List<RecvBag>> =
        api.executeWrite(
            "POST",
            "/api/app/recv/bag-gen",
            """{"truck_lot_id":$truckLotId,"count":$count}""",
        ) { body ->
            json.decodeFromString(BagGenResult.serializer(), body).bags.map { it.toRecv(truckLotId) }
        }

    /** 已有袋列表（幂等恢复入口 · Web 只读，非本批契约端点）。 */
    suspend fun listBags(truckId: Long): ApiResult<List<RecvBag>> =
        api.executeRead("/api/recv/trucks/$truckId/bags") { body ->
            parseRows(body, "rows") { obj ->
                RecvBag(
                    id = obj.long("id"),
                    code = obj.str("code"),
                    human = obj.str("human"),
                    truck_lot_id = obj.long("truck_lot_id"),
                    bag_seq = obj.long("bag_seq").toInt(),
                    weight_allocated = obj.num("weight_allocated"),
                    status = obj.str("status"),
                )
            }
        }

    /** 单车次上下文（过磅/建袋页 load · Web 只读，非本批契约端点）。 */
    suspend fun getTruck(truckId: Long): ApiResult<RecvTruck> =
        api.executeRead("/api/recv/trucks/$truckId") { body ->
            val row = json.parseToJsonElement(body).jsonObject["row"]!!.jsonObject
            RecvTruck(
                id = row.long("id"),
                code = row.str("code"),
                human = row.str("human"),
                notice_no = row.str("notice_no"),
                customer_name = row.str("customer_name"),
                material_name = row.str("material_name"),
                plate_no = row.str("plate_no"),
                seq_no = row.long("seq_no").toInt(),
                arrive_date = row.str("arrive_date"),
                gross_weight = row.num("gross_weight"),
                tare_weight = row.num("tare_weight"),
                net_weight = row.num("net_weight"),
                bag_count = row.long("bag_count").toInt(),
                status = row.str("status"),
            )
        }

    /** §4.6 标签补打（写）。单袋 bag_code + 必填 reason（store 校验留痕防一物两码）。 */
    suspend fun reprintLabel(bagCode: String, reason: String): ApiResult<ReprintResult> =
        api.executeWrite(
            "POST",
            "/api/app/label/reprint",
            """{"bag_code":"${bagCode.replace("\"", "\\\"")}","reason":"${reason.replace("\"", "\\\"")}"}""",
        ) { body -> json.decodeFromString(ReprintResult.serializer(), body) }

    // ---- 解析 ----

    private fun parseNotices(body: String): List<RecvNotice> {
        val obj = json.parseToJsonElement(body) as? JsonObject ?: error("envelope 不是对象")
        val arr = obj["notices"] as? JsonArray ?: return emptyList()
        return arr.mapNotNull { el -> (el as? JsonObject)?.let { parseAppNotice(it) } }
    }

    private fun parseAppNotice(obj: JsonObject): RecvNotice = RecvNotice(
        id = obj.long("notice_id"),
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
        legacy = obj.bool("legacy"),
    )

    private fun <T> parseRows(body: String, key: String, item: (JsonObject) -> T): List<T> {
        val obj = json.parseToJsonElement(body) as? JsonObject ?: error("envelope 不是对象")
        val arr = obj[key] as? JsonArray ?: return emptyList()
        return arr.mapNotNull { el -> (el as? JsonObject)?.let(item) }
    }

    private fun AppBag.toRecv(truckLotId: Long): RecvBag = RecvBag(
        // 契约袋行无独立主键：以 bag_seq 兼作 UI key（车次内唯一）
        id = bag_seq.toLong(),
        code = code,
        human = human,
        truck_lot_id = truckLotId,
        bag_seq = bag_seq,
        weight_allocated = weight_allocated,
        status = status,
    )

    private fun JsonObject.str(key: String): String =
        get(key)?.let { el -> runCatching { el.jsonPrimitive.content }.getOrNull() }.orEmpty()

    private fun JsonObject.long(key: String): Long =
        str(key).toLongOrNull() ?: 0L

    private fun JsonObject.bool(key: String): Boolean =
        str(key).equals("true", ignoreCase = true)

    private fun JsonObject.num(key: String): Double? =
        get(key)?.let { el -> runCatching { el.jsonPrimitive.doubleOrNull }.getOrNull() }
}

/** 打印响应（Web 端旧形状；app/label/reprint 用 [cn.hunanjiangxi.jxlabtrace.app.ReprintResult]）。 */
@Serializable
data class LabelPrintResponse(
    val count: Int = 0,
    val page_url: String = "",
)
