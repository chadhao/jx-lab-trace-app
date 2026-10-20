package cn.hunanjiangxi.jxlabtrace.ship

import cn.hunanjiangxi.jxlabtrace.net.ApiClient
import cn.hunanjiangxi.jxlabtrace.net.ApiResult
import cn.hunanjiangxi.jxlabtrace.scan.CodeParser
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

// ===== M10 出货线（S12）数据层 =====
// 后端契约（internal/httpapi/ship.go + internal/store/shipment.go 实读）：
//   POST /api/ship/shipments               {customer_id?, bag_codes[≥1], remark?} → {created, row:Shipment}
//   POST /api/ship/shipments/:id/items     {bag_code} → {added, row:Shipment}（★ row 是整单不是明细行）
//   GET  /api/ship/shipments/:id           → {row, items:[ShipmentItem…]}
//   GET  /api/ship/shipments?status=&customer_id= → {count, rows}
//   POST /api/ship/shipments/:id/depart    {vehicle_id?, plate_no, driver, ship_at?, operator?}
//                                        → {departed, row}（plate_no/driver 必填非空）
// ★ 响应形状：全部为「裸 JSON 对象」（{created,row} / {added,row} / {departed,row} / {row,items}）——
//   **无顶层 envelope code 键**，不会触发 ApiClient.bizCodeOf 误判 ⇒ 走 executeRead/executeWrite
//   正常通道（自带离线门禁+前置探测），不计入「非 envelope 直连映射」例外（该例外当前仍仅 take 1 例）。
// ★ 装车只收成品袋码（T='E'，normalizeFgBagCode）；重复归集（跨单/单内）后端 409：
//   「该成品袋已被出货单归集」。APP 侧另有本地集合先拦（M10-1）。
// ★ 净重：后端 ship 接口不返回单袋重量/整单净重（ShipmentItem 无重量列）——
//   汇总净重靠调用方注入单袋重量累加；无重量源时只展示袋数（见设计疑问）。

/** 出货单读模型（Shipment）。出场登记与否由 ship_at 是否为 null 表达。 */
@Serializable
data class Shipment(
    val id: Long = 0,
    val shipment_no: String = "",
    val customer_id: Long = 0,
    val customer_name: String = "",
    val vehicle_id: Long? = null,
    val plate_no: String = "",
    val driver: String = "",
    val ship_at: String? = null,
    val operator: String = "",
    /** 中文字面量：已出厂（建单即置）/ 已撤销。 */
    val status: String = "",
    val remark: String = "",
    val created_at: String? = null,
    val created_by: String = "",
) {
    /** 是否已登记出场（ship_at 已写）。 */
    val departed: Boolean get() = !ship_at.isNullOrBlank()
}

/** 出货明细行（ShipmentItem · 后端不含重量列）。 */
@Serializable
data class ShipmentItem(
    val id: Long = 0,
    val shipment_id: Long = 0,
    val fg_bag_id: Long = 0,
    val bag_code: String = "",
    val bag_human: String = "",
    val bag_status: String = "",
    val fg_lot_id: Long = 0,
    val fg_lot_code: String = "",
    val customer_id: Long = 0,
    val created_at: String? = null,
    val created_by: String = "",
)

/** POST /api/ship/shipments 请求体（customer_id 缺省 0 ⇒ 后端由首个袋推定）。 */
@Serializable
data class CreateShipmentBody(
    val customer_id: Long = 0,
    val bag_codes: List<String>,
    val remark: String = "",
)

/** POST /api/ship/shipments/:id/items 请求体。 */
@Serializable
data class AddItemBody(val bag_code: String)

/** POST /api/ship/shipments/:id/depart 请求体（plate_no/driver 后端必填非空）。 */
@Serializable
data class DepartBody(
    val vehicle_id: Long? = null,
    val plate_no: String,
    val driver: String,
    /** 空 ⇒ 服务端当前时刻（APP 不传手机时间——防改表）。 */
    val ship_at: String = "",
    val operator: String = "",
)

/**
 * 出货汇总（M10-2：归集成功后袋数/净重**本地**即增，无需刷新）。
 * [netWeightTons] 为 null 表示无重量源（后端明细无重量列），只展示袋数。
 */
data class ShipSummary(
    val bagCount: Int = 0,
    val netWeightTons: Double? = null,
    val collectedCodes: Set<String> = emptySet(),
) {
    /** 追加一袋：袋数 +1、净重累加（有重量源时）、码入本地集合。 */
    fun add(bagCode: String, weightTons: Double? = null): ShipSummary = ShipSummary(
        bagCount = bagCount + 1,
        netWeightTons = when {
            netWeightTons == null && weightTons == null -> null
            else -> (netWeightTons ?: 0.0) + (weightTons ?: 0.0)
        },
        collectedCodes = collectedCodes + CodeParser.normalize(bagCode),
    )
}

/**
 * 本地重复袋拦截集合（M10-1）：同码第二次扫描在 APP 侧即拦，
 * **不发出任何请求**（服务端计数保持 1）；服务端 409 作二次校验兜底跨单重复。
 */
class CollectedBagSet {
    private val codes = LinkedHashSet<String>()

    /** 是否已在本次出货单中。 */
    fun contains(bagCode: String): Boolean = CodeParser.normalize(bagCode) in codes

    /** 登记（返回 false 表示重复——调用方应拦截而非提交）。 */
    fun add(bagCode: String): Boolean = codes.add(CodeParser.normalize(bagCode))

    val size: Int get() = codes.size
}

/** 出货域仓储（全部走 executeRead/executeWrite ⇒ 自带离线门禁与前置探测）。 */
class ShipRepository(
    private val api: ApiClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {

    /** 建出货单并归集首批袋（bag_codes 至少 1 个，后端硬约束）。 */
    suspend fun createShipment(body: CreateShipmentBody): ApiResult<Shipment> =
        api.executeWrite(
            "POST",
            "/api/ship/shipments",
            """{"customer_id":${body.customer_id},"bag_codes":[${body.bag_codes.joinToString(",") { "\"${it.escape()}\"" }}],""" +
                """"remark":"${body.escapeRemark()}"}""",
        ) { respBody -> parseRowGeneric(respBody, ::parseShipment) }

    /** 向已有单追加一袋。 */
    suspend fun addItem(shipmentId: Long, bagCode: String): ApiResult<Shipment> =
        api.executeWrite(
            "POST",
            "/api/ship/shipments/$shipmentId/items",
            """{"bag_code":"${bagCode.escape()}"}""",
        ) { respBody -> parseRowGeneric(respBody, ::parseShipment) }

    /** 出货单详情（单 + 明细）。 */
    suspend fun getShipment(shipmentId: Long): ApiResult<Pair<Shipment, List<ShipmentItem>>> =
        api.executeRead("/api/ship/shipments/$shipmentId") { body ->
            val obj = json.parseToJsonElement(body) as? JsonObject
                ?: error("响应不是 JSON 对象")
            val row = obj["row"] as? JsonObject ?: error("响应缺少 row")
            val items = (obj["items"] as? JsonArray).orEmpty().mapNotNull { el ->
                (el as? JsonObject)?.let(::parseShipmentItem)
            }
            parseShipment(row) to items
        }

    /** 出场登记（plate_no/driver 必填；ship_at 留空走服务端时间）。 */
    suspend fun depart(shipmentId: Long, body: DepartBody): ApiResult<Shipment> {
        val fields = buildList {
            body.vehicle_id?.let { add("\"vehicle_id\":$it") }
            add("\"plate_no\":\"${body.plate_no.escape()}\"")
            add("\"driver\":\"${body.driver.escape()}\"")
            if (body.ship_at.isNotBlank()) add("\"ship_at\":\"${body.ship_at.escape()}\"")
            if (body.operator.isNotBlank()) add("\"operator\":\"${body.operator.escape()}\"")
        }
        return api.executeWrite(
            "POST",
            "/api/ship/shipments/$shipmentId/depart",
            fields.joinToString(",", prefix = "{", postfix = "}"),
        ) { respBody -> parseRowGeneric(respBody, ::parseShipment) }
    }

    // ---- 解析 ----

    private fun <T> parseRowGeneric(body: String, item: (JsonObject) -> T): T {
        val obj = json.parseToJsonElement(body) as? JsonObject
            ?: error("响应不是 JSON 对象")
        val row = obj["row"] as? JsonObject ?: error("响应缺少 row")
        return item(row)
    }

    private fun parseShipment(obj: JsonObject): Shipment = Shipment(
        id = obj.long("id"),
        shipment_no = obj.str("shipment_no"),
        customer_id = obj.long("customer_id"),
        customer_name = obj.str("customer_name"),
        vehicle_id = obj.str("vehicle_id").toLongOrNull(),
        plate_no = obj.str("plate_no"),
        driver = obj.str("driver"),
        ship_at = obj.str("ship_at").ifBlank { null },
        operator = obj.str("operator"),
        status = obj.str("status"),
        remark = obj.str("remark"),
        created_at = obj.str("created_at").ifBlank { null },
        created_by = obj.str("created_by"),
    )

    private fun parseShipmentItem(obj: JsonObject): ShipmentItem = ShipmentItem(
        id = obj.long("id"),
        shipment_id = obj.long("shipment_id"),
        fg_bag_id = obj.long("fg_bag_id"),
        bag_code = obj.str("bag_code"),
        bag_human = obj.str("bag_human"),
        bag_status = obj.str("bag_status"),
        fg_lot_id = obj.long("fg_lot_id"),
        fg_lot_code = obj.str("fg_lot_code"),
        customer_id = obj.long("customer_id"),
        created_at = obj.str("created_at").ifBlank { null },
        created_by = obj.str("created_by"),
    )

    private fun JsonObject.str(key: String): String =
        get(key)
            ?.takeIf { it != kotlinx.serialization.json.JsonNull }
            ?.let { el -> runCatching { el.jsonPrimitive.content }.getOrNull() }
            .orEmpty()

    private fun JsonObject.long(key: String): Long = str(key).toLongOrNull() ?: 0L

    private fun CreateShipmentBody.escapeRemark(): String = remark.escape()

    private fun String.escape(): String = buildString {
        for (ch in this@escape) {
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(ch)
            }
        }
    }
}
