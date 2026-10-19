package cn.hunanjiangxi.jxlabtrace.prod

import cn.hunanjiangxi.jxlabtrace.net.ApiClient
import cn.hunanjiangxi.jxlabtrace.net.ApiResult
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive

// ===== M07 生产线（S08–S11）数据层 =====
// 后端契约（internal/httpapi/prod.go + internal/store/production.go 实读）：
//   GET  /api/prod/batches?status=…      → {count, rows:[ProductionBatch…]}
//   POST /api/prod/batches               {customer_id, input_material_id,
//                                          planned_output_material_id, batch_date?, remark?}
//                                        → {created, row}
//   GET  /api/prod/batches/:id/feeds     → {count, rows:[FeedRecord…]}
//   POST /api/prod/batches/:id/feeds     {bag_code, feed_weight?, operator?, …} → {fed, row}
//   PATCH /api/prod/feeds/:id            {feed_weight?, remark?, reason} → {corrected, row}
//   GET  /api/prod/batches/:id/operations→ {count, rows:[BatchOperation…]}
//   POST /api/prod/batches/:id/operations{start_at, end_at, …} → {created, row}
//   POST /api/prod/batches/:id/fg-lots   {qty_bag?, net_weight?, pack_spec?, …} → {created, row}
//   POST /api/prod/fg-lots/:id/bags      {count} → {count, rows:[FgBag…]}
//   POST /api/prod/fg-lots/:id/print     {bag_seqs?, is_reprint?, reason?} → {count, rows}
//
// ★ 响应形状：全部为「裸 JSON 对象」（{created,row} / {fed,row} / {count,rows}）——
//   **无顶层 envelope code 键**，不会触发 ApiClient.bizCodeOf 误判（与 sample.take
//   的 `"code":"<对象码>"` 冲突不同）⇒ 走 executeRead/executeWrite 正常通道，
//   不计入「非 envelope 直连映射」例外（该例外当前仅 take 1 例）。

/** 生产批读模型（ProductionBatch）。 */
@Serializable
data class ProdBatch(
    val id: Long = 0,
    val code: String = "",
    val human: String = "",
    val customer_id: Long = 0,
    val input_material_id: Long = 0,
    val planned_output_material_id: Long = 0,
    val batch_date: String = "",
    val status: String = "",
    val remark: String = "",
    val created_by: String = "",
)

/** 投料记录读模型（FeedRecord）。 */
@Serializable
data class FeedRecord(
    val id: Long = 0,
    val batch_id: Long = 0,
    val bag_id: Long = 0,
    val bag_code: String = "",
    val bag_human: String = "",
    val truck_id: Long = 0,
    val truck_code: String = "",
    val feed_weight: Double? = null,
    val fed_at: String? = null,
    val operator: String = "",
    val remark: String = "",
    /** APP 侧版本链展示：初值 v1；PATCH 成功后置 v2（后端审计留痕，无 version 列）。 */
    val version: Int = 1,
)

/** 作业段读模型（BatchOperation）。 */
@Serializable
data class BatchOperation(
    val id: Long = 0,
    val batch_id: Long = 0,
    val seq: Int = 0,
    val operator: String = "",
    val start_at: String? = null,
    val end_at: String? = null,
    val output_weight: Double? = null,
    val remark: String = "",
)

/** 成品批读模型（FgLot）。 */
@Serializable
data class FgLot(
    val id: Long = 0,
    val code: String = "",
    val human: String = "",
    val batch_id: Long = 0,
    val pack_spec: String = "",
    val qty_bag: Int = 0,
    val net_weight: Double? = null,
    val status: String = "",
)

/** 成品袋读模型（FgBag）。 */
@Serializable
data class FgBag(
    val id: Long = 0,
    val code: String = "",
    val human: String = "",
    val fg_lot_id: Long = 0,
    val bag_seq: Int = 0,
    val weight_allocated: Double? = null,
    val status: String = "",
)

/** POST /api/prod/batches 请求体（M7-1：批号由后端生成，不在请求体）。 */
@Serializable
data class CreateBatchBody(
    val customer_id: Long,
    val input_material_id: Long,
    val planned_output_material_id: Long,
    val batch_date: String = "",
    val remark: String = "",
)

/** PATCH /api/prod/feeds/:id 请求体（M7-4 形状：feed_id/new_weight/reason/version 必含）。 */
@Serializable
data class FeedCorrectBody(
    val feed_id: Long,
    val new_weight: Double,
    val reason: String,
    val version: Int,
    /** 后端实际消费字段（FeedCorrectInput：feed_weight/remark/reason）；多余键后端忽略。 */
    val feed_weight: Double,
    val remark: String = "",
)

/** 生产域仓储（全部走 executeRead/executeWrite ⇒ 自带离线门禁与前置探测）。 */
class ProdRepository(
    private val api: ApiClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {

    /** 生产批列表。[status] 空 ⇒ 全部；中文状态字面量（进行中/已完成/已作废）。 */
    suspend fun listBatches(status: String? = null): ApiResult<List<ProdBatch>> {
        val path = if (status.isNullOrBlank()) {
            "/api/prod/batches"
        } else {
            "/api/prod/batches?status=" + java.net.URLEncoder.encode(status, "UTF-8")
        }
        return api.executeRead(path) { body -> parseRowsGeneric(body, ::parseBatch) }
    }

    /** 建生产批（批号由后端取号生成）。 */
    suspend fun createBatch(body: CreateBatchBody): ApiResult<ProdBatch> =
        api.executeWrite(
            "POST",
            "/api/prod/batches",
            """{"customer_id":${body.customer_id},"input_material_id":${body.input_material_id},""" +
                """"planned_output_material_id":${body.planned_output_material_id},""" +
                (if (body.batch_date.isBlank()) {
                    ""
                } else {
                    """"batch_date":"${body.batch_date}","""
                }) +
                """"remark":"${body.remark.escape()}"}""",
        ) { respBody -> parseRowGeneric(respBody, ::parseBatch) }

    /** 某批投料明细。 */
    suspend fun listFeeds(batchId: Long): ApiResult<List<FeedRecord>> =
        api.executeRead("/api/prod/batches/$batchId/feeds") { body ->
            parseRowsGeneric(body) { obj -> parseFeed(obj).copy(version = 1) }
        }

    /** 投料扫码：一袋一 POST（谱系承重墙）。 */
    suspend fun feedScan(batchId: Long, bagCode: String, weightTons: Double?, operator: String): ApiResult<FeedRecord> =
        api.executeWrite(
            "POST",
            "/api/prod/batches/$batchId/feeds",
            buildString {
                append("{")
                append("\"bag_code\":\"${bagCode.escape()}\"")
                if (weightTons != null) append(",\"feed_weight\":$weightTons")
                if (operator.isNotBlank()) append(",\"operator\":\"${operator.escape()}\"")
                append("}")
            },
        ) { body -> parseRowGeneric(body, ::parseFeed) }

    /** 投料更正（限本班次内由 UI 门禁；后端要求 reason 非空）。 */
    suspend fun correctFeed(batchId: Long, body: FeedCorrectBody): ApiResult<FeedRecord> =
        api.executeWrite(
            "PATCH",
            "/api/prod/feeds/${body.feed_id}",
            """{"feed_id":${body.feed_id},"new_weight":${body.new_weight},""" +
                """"reason":"${body.reason.escape()}","version":${body.version},""" +
                """"feed_weight":${body.feed_weight},"remark":"${body.remark.escape()}"}""",
        ) { respBody ->
            parseRowGeneric(respBody) { obj -> parseFeed(obj).copy(version = body.version) }
        }

    /** 某批作业段列表。 */
    suspend fun listOperations(batchId: Long): ApiResult<List<BatchOperation>> =
        api.executeRead("/api/prod/batches/$batchId/operations") { body ->
            parseRowsGeneric(body, ::parseOperation)
        }

    /** 记一段作业（开始/结束两点在 APP 侧合成一条 start_at+end_at 提交）。 */
    suspend fun addOperation(
        batchId: Long,
        startAt: String,
        endAt: String,
        operator: String,
    ): ApiResult<BatchOperation> =
        api.executeWrite(
            "POST",
            "/api/prod/batches/$batchId/operations",
            """{"start_at":"${startAt.escape()}","end_at":"${endAt.escape()}",""" +
                (if (operator.isBlank()) {
                    ""
                } else {
                    "\"operator\":\"${operator.escape()}\","
                }) + "\"remark\":\"\"}",
        ) { body -> parseRowGeneric(body, ::parseOperation) }

    /** 生成成品批（批号后端自动）。 */
    suspend fun createFgLot(
        batchId: Long,
        qtyBag: Int?,
        netWeightTons: Double?,
        packSpec: String = "吨袋",
    ): ApiResult<FgLot> =
        api.executeWrite(
            "POST",
            "/api/prod/batches/$batchId/fg-lots",
            buildString {
                append("{")
                append("\"pack_spec\":\"${packSpec.escape()}\"")
                if (qtyBag != null) append(",\"qty_bag\":$qtyBag")
                if (netWeightTons != null) append(",\"net_weight\":$netWeightTons")
                append("}")
            },
        ) { body -> parseRowGeneric(body, ::parseFgLot) }

    /** 批量生成成品袋码。 */
    suspend fun generateFgBags(fgLotId: Long, count: Int): ApiResult<List<FgBag>> =
        api.executeWrite(
            "POST",
            "/api/prod/fg-lots/$fgLotId/bags",
            """{"count":$count}""",
        ) { body -> parseRowsGeneric(body, ::parseFgBag) }

    /** 成品袋打印留痕（全部首打；补打走 PC —— S11 一期无补打入）。 */
    suspend fun printFgBags(fgLotId: Long): ApiResult<List<FgBag>> =
        api.executeWrite(
            "POST",
            "/api/prod/fg-lots/$fgLotId/print",
            "{}",
        ) { body -> parseRowsGeneric(body, ::parseFgBag) }

    // ---- 解析 ----

    private fun <T> parseRowsGeneric(body: String, item: (JsonObject) -> T): List<T> {
        val obj = json.parseToJsonElement(body) as? JsonObject
            ?: error("响应不是 JSON 对象")
        val arr = obj["rows"] as? JsonArray ?: return emptyList()
        return arr.mapNotNull { el -> (el as? JsonObject)?.let(item) }
    }

    private fun <T> parseRowGeneric(body: String, item: (JsonObject) -> T): T {
        val obj = json.parseToJsonElement(body) as? JsonObject
            ?: error("响应不是 JSON 对象")
        val row = obj["row"] as? JsonObject ?: error("响应缺少 row")
        return item(row)
    }

    private fun parseBatch(obj: JsonObject): ProdBatch = ProdBatch(
        id = obj.long("id"),
        code = obj.str("code"),
        human = obj.str("human"),
        customer_id = obj.long("customer_id"),
        input_material_id = obj.long("input_material_id"),
        planned_output_material_id = obj.long("planned_output_material_id"),
        batch_date = obj.str("batch_date"),
        status = obj.str("status"),
        remark = obj.str("remark"),
        created_by = obj.str("created_by"),
    )

    private fun parseFeed(obj: JsonObject): FeedRecord = FeedRecord(
        id = obj.long("id"),
        batch_id = obj.long("batch_id"),
        bag_id = obj.long("bag_id"),
        bag_code = obj.str("bag_code"),
        bag_human = obj.str("bag_human"),
        truck_id = obj.long("truck_id"),
        truck_code = obj.str("truck_code"),
        feed_weight = obj.num("feed_weight"),
        fed_at = obj.str("fed_at").ifBlank { null },
        operator = obj.str("operator"),
        remark = obj.str("remark"),
    )

    private fun parseOperation(obj: JsonObject): BatchOperation = BatchOperation(
        id = obj.long("id"),
        batch_id = obj.long("batch_id"),
        seq = obj.long("seq").toInt(),
        operator = obj.str("operator"),
        start_at = obj.str("start_at").ifBlank { null },
        end_at = obj.str("end_at").ifBlank { null },
        output_weight = obj.num("output_weight"),
        remark = obj.str("remark"),
    )

    private fun parseFgLot(obj: JsonObject): FgLot = FgLot(
        id = obj.long("id"),
        code = obj.str("code"),
        human = obj.str("human"),
        batch_id = obj.long("batch_id"),
        pack_spec = obj.str("pack_spec"),
        qty_bag = obj.long("qty_bag").toInt(),
        net_weight = obj.num("net_weight"),
        status = obj.str("status"),
    )

    private fun parseFgBag(obj: JsonObject): FgBag = FgBag(
        id = obj.long("id"),
        code = obj.str("code"),
        human = obj.str("human"),
        fg_lot_id = obj.long("fg_lot_id"),
        bag_seq = obj.long("bag_seq").toInt(),
        weight_allocated = obj.num("weight_allocated"),
        status = obj.str("status"),
    )

    private fun JsonObject.str(key: String): String =
        get(key)?.let { el -> runCatching { el.jsonPrimitive.content }.getOrNull() }.orEmpty()

    private fun JsonObject.long(key: String): Long = str(key).toLongOrNull() ?: 0L

    private fun JsonObject.num(key: String): Double? =
        get(key)?.let { el -> runCatching { el.jsonPrimitive.doubleOrNull }.getOrNull() }

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
