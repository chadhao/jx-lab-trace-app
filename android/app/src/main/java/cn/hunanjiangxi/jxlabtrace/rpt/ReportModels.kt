package cn.hunanjiangxi.jxlabtrace.rpt

import cn.hunanjiangxi.jxlabtrace.net.ApiClient
import cn.hunanjiangxi.jxlabtrace.net.ApiResult
import cn.hunanjiangxi.jxlabtrace.net.SessionCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive

// ===== M11 报表（S14 · 管理层只读）数据层 =====
// 后端契约（internal/httpapi/rpt.go + internal/store/rpt.go 实读）：
//   GET /api/rpt/:name?from=&to=&customer_id=&bucket=
//     → {name, title, columns:[{key,label}], rows:[{…}], has_data, note?}
// ★ 一期 5 张：quality-trend / customer-recon / output-yield / sample-expiry / nonconform-stat
// ★ 空数据两态（A15）：has_data=false ⇒ note=「数据未接入」；has_data=true 且 rows=[]
//   ⇒ note=「本条件下无记录」——两态文案不同，禁止用 0/空数组冒充结果为 0。
// ★ 只读铁律：APP 无导出按钮、无任何写入口（导出接口属 PC，M11-2 grep 零命中）。
// ★ 响应裸对象（顶层无 code 键）⇒ executeRead 正常通道。

/** 报表名（后端一期 5 张；S14 管理层首页 4 Tab 取前 4 张，sample-expiry 入 Tab 4）。 */
enum class ReportName(val apiValue: String, val title: String) {
    QUALITY_TREND("quality-trend", "质量趋势"),
    CUSTOMER_RECON("customer-recon", "客户对账"),
    OUTPUT_YIELD("output-yield", "产出得率"),
    SAMPLE_EXPIRY("sample-expiry", "留样效期"),
    NONCONFORM_STAT("nonconform-stat", "不合格统计"),
}

/** 一列定义。 */
@Serializable
data class ReportColumn(val key: String = "", val label: String = "")

/** 报表结果（只读卡片/表格共用）。 */
data class ReportResult(
    val name: String = "",
    val title: String = "",
    val columns: List<ReportColumn> = emptyList(),
    val rows: List<Map<String, String>> = emptyList(),
    val hasData: Boolean = false,
    val note: String = "",
) {
    /** 空态文案（两态区分；非空数据时为 null）。 */
    val emptyHint: String?
        get() = when {
            rows.isNotEmpty() -> null
            !hasData -> note.ifBlank { EMPTY_NO_SOURCE }
            else -> note.ifBlank { EMPTY_NO_MATCH }
        }

    companion object {
        const val EMPTY_NO_SOURCE = "数据未接入"
        const val EMPTY_NO_MATCH = "本条件下无记录"
    }
}

/** 通用筛选（与后端 RptFilters 对齐；空串=不限）。 */
data class ReportFilters(
    val from: String = "",
    val to: String = "",
    val customerId: Long? = null,
    val bucket: String = "",
)

/** 报表域仓储（只读；零写入口——M11-2）。 */
class ReportRepository(
    private val api: ApiClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {

    /** 执行一张报表。 */
    suspend fun run(
        name: ReportName,
        filters: ReportFilters = ReportFilters(),
    ): ApiResult<ReportResult> {
        val query = buildList {
            if (filters.from.isNotBlank()) add("from=" + java.net.URLEncoder.encode(filters.from, "UTF-8"))
            if (filters.to.isNotBlank()) add("to=" + java.net.URLEncoder.encode(filters.to, "UTF-8"))
            filters.customerId?.let { add("customer_id=$it") }
            if (filters.bucket.isNotBlank()) add("bucket=" + java.net.URLEncoder.encode(filters.bucket, "UTF-8"))
        }.joinToString("&")
        val path = "/api/rpt/${name.apiValue}" + if (query.isBlank()) "" else "?$query"
        return api.executeRead(path) { body ->
            val obj = json.parseToJsonElement(body) as? JsonObject
                ?: error("响应不是 JSON 对象")
            val columns = (obj["columns"] as? JsonArray).orEmpty().mapNotNull { el ->
                (el as? JsonObject)?.let { c ->
                    ReportColumn(key = c.str("key"), label = c.str("label"))
                }
            }
            val rows = (obj["rows"] as? JsonArray).orEmpty().mapNotNull { el ->
                (el as? JsonObject)?.let { row -> row.toValueMap() }
            }
            ReportResult(
                name = obj.str("name"),
                title = obj.str("title"),
                columns = columns,
                rows = rows,
                hasData = obj.bool("has_data"),
                note = obj.str("note"),
            )
        }
    }

    private fun JsonObject.toValueMap(): Map<String, String> =
        entries.associate { (key, value) -> key to cellText(value) }

    private fun cellText(value: JsonElement): String = runCatching {
        value.jsonPrimitive.content
    }.getOrDefault(
        runCatching { value.jsonPrimitive.doubleOrNull?.let { d ->
            if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()
        } }.getOrNull().orEmpty(),
    )

    private fun JsonObject.str(key: String): String =
        get(key)
            ?.takeIf { it != kotlinx.serialization.json.JsonNull }
            ?.let { el -> runCatching { el.jsonPrimitive.content }.getOrNull() }
            .orEmpty()

    private fun JsonObject.bool(key: String): Boolean =
        str(key).equals("true", ignoreCase = true)
}

// ===== S14 经营看板 ViewModel（4 Tab · 只读刷新）=====

sealed interface ReportUiState {
    object Loading : ReportUiState
    data class Loaded(val result: ReportResult) : ReportUiState
    data class Error(val message: String) : ReportUiState
}

/**
 * S14 报表（M11-2）：管理层首页变体 4 Tab，接 `GET /api/rpt/:name`；
 * 卡片只读、下拉刷新即最新；**无导出、无写入口**。
 * Tab 顺序 = [ReportName] 前 4 张（质量趋势/客户对账/产出得率/留样效期）。
 */
class ReportViewModel(
    private val repo: ReportRepository,
    private val session: SessionCoordinator,
    val tabs: List<ReportName> = DEFAULT_TABS,
    scope: CoroutineScope? = null,
) {
    private val scope = scope ?: CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _tabIndex = MutableStateFlow(0)
    val tabIndex: StateFlow<Int> = _tabIndex.asStateFlow()

    private val _state = MutableStateFlow<ReportUiState>(ReportUiState.Loading)
    val state: StateFlow<ReportUiState> = _state.asStateFlow()

    fun selectTab(index: Int) {
        if (index !in tabs.indices || index == _tabIndex.value) return
        _tabIndex.value = index
        refresh()
    }

    fun refresh() {
        val name = tabs[_tabIndex.value]
        scope.launch {
            _state.value = ReportUiState.Loading
            when (val result = session.withSessionRetry { repo.run(name) }) {
                is ApiResult.Success -> _state.value = ReportUiState.Loaded(result.data)
                is ApiResult.Failure -> _state.value =
                    ReportUiState.Error(cn.hunanjiangxi.jxlabtrace.net.ErrorMessages.of(result.error))
            }
        }
    }

    companion object {
        /** 管理层 4 Tab（S14 稿：经营看板）。 */
        val DEFAULT_TABS = listOf(
            ReportName.QUALITY_TREND,
            ReportName.CUSTOMER_RECON,
            ReportName.OUTPUT_YIELD,
            ReportName.SAMPLE_EXPIRY,
        )
    }
}
