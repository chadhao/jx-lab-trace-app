package cn.hunanjiangxi.jxlabtrace.trace

import cn.hunanjiangxi.jxlabtrace.net.ApiClient
import cn.hunanjiangxi.jxlabtrace.net.ApiResult
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive

// ===== M11 追溯（S13）数据层 =====
// 后端契约（internal/httpapi/trace.go + internal/store/trace.go 实读）：
//   GET /api/trace/forward?truck_lot_id=|bag_code= → TraceForwardResult 裸对象
//   GET /api/trace/backward?fg_lot_id=|fg_code=    → TraceBackwardResult 裸对象
//   GET /api/trace/batch/:batchId                  → BatchArchive 裸对象
//   GET /api/prod/genealogy/bags/:code             → {found, row:GenealogyBag}
// ★ 响应形状：trace 三接口为**裸 JSON 对象**（顶层无 code 键：has_flow/source/batches…），
//   genealogy 为 {found,row}——均不触发 bizCodeOf 误判 ⇒ executeRead 正常通道
//   （「非 envelope 直连映射」例外计数仍仅 take 1 例）。
// ★ 全部只读（后端零写业务表）；未命中 ≠ 报错：forward 无流向 ⇒ 200 + has_flow:false。

/** 正向追溯入口对象（车次）。 */
@Serializable
data class TraceSource(
    val truck_lot_id: Long = 0,
    val truck_code: String = "",
    val customer: String = "",
    val material: String = "",
    val arrive_date: String = "",
    val status: String = "",
)

/** 链上生产批。 */
@Serializable
data class TraceBatchRef(
    val batch_id: Long = 0,
    val code: String = "",
    val human: String = "",
    val batch_date: String = "",
    val status: String = "",
)

/** 链上成品批。 */
@Serializable
data class TraceFgLotRef(
    val fg_lot_id: Long = 0,
    val code: String = "",
    val human: String = "",
    val output_material: String = "",
    val net_weight: Double? = null,
    val status: String = "",
)

/** 链上出货单。 */
@Serializable
data class TraceShipmentRef(
    val shipment_id: Long = 0,
    val shipment_no: String = "",
    val status: String = "",
    val ship_at: String? = null,
    val plate_no: String = "",
    val customer: String = "",
)

/** 正向追溯结果（车/吨袋 → 批 → 成品 → 出货）。 */
@Serializable
data class TraceForwardResult(
    val source: TraceSource = TraceSource(),
    val batches: List<TraceBatchRef> = emptyList(),
    val fg_lots: List<TraceFgLotRef> = emptyList(),
    val shipments: List<TraceShipmentRef> = emptyList(),
    val has_flow: Boolean = false,
)

/** 反向追溯的一条投料（含车次的现行检测结果——只读展示）。 */
@Serializable
data class TraceFeedRef(
    val bag_id: Long = 0,
    val bag_code: String = "",
    val bag_human: String = "",
    val truck_lot_id: Long = 0,
    val truck_code: String = "",
    val customer: String = "",
    val feed_weight: Double? = null,
    val fed_at: String? = null,
    /** 现行检测单号（PC 出；APP 只读展示）。 */
    val inspection_no: String = "",
    val is_current: Boolean = false,
    /** 检测结果行（item/value/judge——合格判定由各行 judge 汇总展示）。 */
    val results: List<TraceInspResultRow> = emptyList(),
)

/** 一条检测结果数值。 */
@Serializable
data class TraceInspResultRow(
    val item_name: String = "",
    val state: String = "",
    val value_num: Double? = null,
    val value_text: String = "",
    val unit: String = "",
    val judge: String = "",
)

/** 反向追溯结果（成品批 → 来源批 → 投料）。 */
@Serializable
data class TraceBackwardResult(
    val fg_lot: TraceFgLotRef = TraceFgLotRef(),
    val batches: List<TraceBatchRef> = emptyList(),
    val feeds: List<TraceFeedRef> = emptyList(),
    val has_flow: Boolean = false,
)

/** 批次档案（D6 一页汇总；APP 只读）。 */
@Serializable
data class BatchArchive(
    val batch: TraceBatchRef = TraceBatchRef(),
    val customer_name: String = "",
    val input_material: String = "",
    val planned_output_material: String = "",
    val remark: String = "",
    val feed_count: Int = 0,
    val operation_count: Int = 0,
    val fg_lot_count: Int = 0,
    val shipment_count: Int = 0,
)

/** 袋谱系（genealogy：袋 → 投料记录 → 去向批，可多对多）。 */
@Serializable
data class GenealogyBag(
    val bag_id: Long = 0,
    val bag_code: String = "",
    val bag_human: String = "",
    val status: String = "",
    val truck_code: String = "",
    val customer_id: Long = 0,
    val material_id: Long = 0,
    val feeds: List<GenealogyFeed> = emptyList(),
)

/** 袋谱系的一条投料（去向）。 */
@Serializable
data class GenealogyFeed(
    val id: Long = 0,
    val batch_id: Long = 0,
    val bag_code: String = "",
    val truck_code: String = "",
    val feed_weight: Double? = null,
    val fed_at: String? = null,
    val operator: String = "",
)

/** 追溯域仓储（全部只读 ⇒ executeRead，自带离线门禁）。 */
class TraceRepository(
    private val api: ApiClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {

    /** 正向：车次 id 或原料吨袋码（B）二选一。 */
    suspend fun forward(
        truckLotId: Long? = null,
        bagCode: String? = null,
    ): ApiResult<TraceForwardResult> {
        val query = buildList {
            truckLotId?.let { add("truck_lot_id=$it") }
            bagCode?.let { add("bag_code=" + java.net.URLEncoder.encode(it, "UTF-8")) }
        }.joinToString("&")
        require(query.isNotBlank()) { "truck_lot_id / bag_code 至少一个" }
        return api.executeRead("/api/trace/forward?$query") { body ->
            parseForward(json.parseToJsonElement(body) as? JsonObject ?: error("响应不是对象"))
        }
    }

    /** 反向：成品批 id 或成品批码（D）二选一。 */
    suspend fun backward(
        fgLotId: Long? = null,
        fgCode: String? = null,
    ): ApiResult<TraceBackwardResult> {
        val query = buildList {
            fgLotId?.let { add("fg_lot_id=$it") }
            fgCode?.let { add("fg_code=" + java.net.URLEncoder.encode(it, "UTF-8")) }
        }.joinToString("&")
        require(query.isNotBlank()) { "fg_lot_id / fg_code 至少一个" }
        return api.executeRead("/api/trace/backward?$query") { body ->
            parseBackward(json.parseToJsonElement(body) as? JsonObject ?: error("响应不是对象"))
        }
    }

    /** 批次档案（数字 id）。 */
    suspend fun batchArchive(batchId: Long): ApiResult<BatchArchive> =
        api.executeRead("/api/trace/batch/$batchId") { body ->
            parseArchive(json.parseToJsonElement(body) as? JsonObject ?: error("响应不是对象"))
        }

    /** 袋谱系（原料吨袋码 B）。 */
    suspend fun genealogy(bagCode: String): ApiResult<GenealogyBag> {
        val encoded = java.net.URLEncoder.encode(bagCode, "UTF-8")
        return api.executeRead("/api/prod/genealogy/bags/$encoded") { body ->
            val obj = json.parseToJsonElement(body) as? JsonObject ?: error("响应不是对象")
            val row = obj["row"] as? JsonObject ?: error("响应缺少 row")
            parseGenealogy(row)
        }
    }

    // ---- 解析（裸对象直读；inspection 嵌套收敛为顶层字段）----

    private fun parseForward(obj: JsonObject): TraceForwardResult = TraceForwardResult(
        source = (obj["source"] as? JsonObject)?.let(::parseSource) ?: TraceSource(),
        batches = (obj["batches"] as? JsonArray).orEmpty().mapNotNull { el ->
            (el as? JsonObject)?.let(::parseBatchRef)
        },
        fg_lots = (obj["fg_lots"] as? JsonArray).orEmpty().mapNotNull { el ->
            (el as? JsonObject)?.let(::parseFgLotRef)
        },
        shipments = (obj["shipments"] as? JsonArray).orEmpty().mapNotNull { el ->
            (el as? JsonObject)?.let(::parseShipmentRef)
        },
        has_flow = obj.bool("has_flow"),
    )

    private fun parseBackward(obj: JsonObject): TraceBackwardResult = TraceBackwardResult(
        fg_lot = (obj["fg_lot"] as? JsonObject)?.let(::parseFgLotRef) ?: TraceFgLotRef(),
        batches = (obj["batches"] as? JsonArray).orEmpty().mapNotNull { el ->
            (el as? JsonObject)?.let(::parseBatchRef)
        },
        feeds = (obj["feeds"] as? JsonArray).orEmpty().mapNotNull { el ->
            (el as? JsonObject)?.let(::parseFeedRef)
        },
        has_flow = obj.bool("has_flow"),
    )

    private fun parseArchive(obj: JsonObject): BatchArchive {
        val feeds = (obj["feeds"] as? JsonArray)?.size ?: 0
        val ops = (obj["operations"] as? JsonArray)?.size ?: 0
        val lots = (obj["fg_lots"] as? JsonArray)?.size ?: 0
        val ships = (obj["shipments"] as? JsonArray)?.size ?: 0
        return BatchArchive(
            batch = (obj["batch"] as? JsonObject)?.let(::parseBatchRef) ?: TraceBatchRef(),
            customer_name = obj.str("customer_name"),
            input_material = obj.str("input_material"),
            planned_output_material = obj.str("planned_output_material"),
            remark = obj.str("remark"),
            feed_count = feeds,
            operation_count = ops,
            fg_lot_count = lots,
            shipment_count = ships,
        )
    }

    private fun parseGenealogy(obj: JsonObject): GenealogyBag = GenealogyBag(
        bag_id = obj.long("bag_id"),
        bag_code = obj.str("bag_code"),
        bag_human = obj.str("bag_human"),
        status = obj.str("status"),
        truck_code = obj.str("truck_code"),
        customer_id = obj.long("customer_id"),
        material_id = obj.long("material_id"),
        feeds = (obj["feeds"] as? JsonArray).orEmpty().mapNotNull { el ->
            (el as? JsonObject)?.let { f ->
                GenealogyFeed(
                    id = f.long("id"),
                    batch_id = f.long("batch_id"),
                    bag_code = f.str("bag_code"),
                    truck_code = f.str("truck_code"),
                    feed_weight = f.num("feed_weight"),
                    fed_at = f.str("fed_at").ifBlank { null },
                    operator = f.str("operator"),
                )
            }
        },
    )

    private fun parseSource(obj: JsonObject): TraceSource = TraceSource(
        truck_lot_id = obj.long("truck_lot_id"),
        truck_code = obj.str("truck_code"),
        customer = obj.str("customer"),
        material = obj.str("material"),
        arrive_date = obj.str("arrive_date"),
        status = obj.str("status"),
    )

    private fun parseBatchRef(obj: JsonObject): TraceBatchRef = TraceBatchRef(
        batch_id = obj.long("batch_id"),
        code = obj.str("code"),
        human = obj.str("human"),
        batch_date = obj.str("batch_date"),
        status = obj.str("status"),
    )

    private fun parseFgLotRef(obj: JsonObject): TraceFgLotRef = TraceFgLotRef(
        fg_lot_id = obj.long("fg_lot_id"),
        code = obj.str("code"),
        human = obj.str("human"),
        output_material = obj.str("output_material"),
        net_weight = obj.num("net_weight"),
        status = obj.str("status"),
    )

    private fun parseShipmentRef(obj: JsonObject): TraceShipmentRef = TraceShipmentRef(
        shipment_id = obj.long("shipment_id"),
        shipment_no = obj.str("shipment_no"),
        status = obj.str("status"),
        ship_at = obj.str("ship_at").ifBlank { null },
        plate_no = obj.str("plate_no"),
        customer = obj.str("customer"),
    )

    private fun parseFeedRef(obj: JsonObject): TraceFeedRef {
        val inspection = obj["inspection"] as? JsonObject
        return TraceFeedRef(
            bag_id = obj.long("bag_id"),
            bag_code = obj.str("bag_code"),
            bag_human = obj.str("bag_human"),
            truck_lot_id = obj.long("truck_lot_id"),
            truck_code = obj.str("truck_code"),
            customer = obj.str("customer"),
            feed_weight = obj.num("feed_weight"),
            fed_at = obj.str("fed_at").ifBlank { null },
            // ★ 不读 inspection 顶层结论键（白名单禁词）：合格与否由 results[].judge 各行展示
            inspection_no = inspection?.str("inspection_no").orEmpty(),
            is_current = inspection?.bool("is_current") ?: false,
            results = (inspection?.get("results") as? JsonArray).orEmpty().mapNotNull { el ->
                (el as? JsonObject)?.let { r ->
                    TraceInspResultRow(
                        item_name = r.str("item_name"),
                        state = r.str("state"),
                        value_num = r.num("value_num"),
                        value_text = r.str("value_text"),
                        unit = r.str("unit"),
                        judge = r.str("judge"),
                    )
                }
            },
        )
    }

    private fun JsonObject.str(key: String): String =
        get(key)
            ?.takeIf { it != kotlinx.serialization.json.JsonNull }
            ?.let { el -> runCatching { el.jsonPrimitive.content }.getOrNull() }
            .orEmpty()

    private fun JsonObject.long(key: String): Long = str(key).toLongOrNull() ?: 0L

    private fun JsonObject.bool(key: String): Boolean =
        str(key).equals("true", ignoreCase = true)

    private fun JsonObject.num(key: String): Double? =
        get(key)?.let { el -> runCatching { el.jsonPrimitive.doubleOrNull }.getOrNull() }
}
