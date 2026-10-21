package cn.hunanjiangxi.jxlabtrace.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import cn.hunanjiangxi.jxlabtrace.auth.Me
import cn.hunanjiangxi.jxlabtrace.net.ApiClient
import cn.hunanjiangxi.jxlabtrace.net.ApiResult
import cn.hunanjiangxi.jxlabtrace.ui.components.DomainBlock
import cn.hunanjiangxi.jxlabtrace.ui.components.EmptyState
import cn.hunanjiangxi.jxlabtrace.ui.components.FieldCard
import cn.hunanjiangxi.jxlabtrace.ui.components.JxPreviewTheme
import cn.hunanjiangxi.jxlabtrace.ui.icons.JxIcons
import cn.hunanjiangxi.jxlabtrace.ui.login.roleLabel
import cn.hunanjiangxi.jxlabtrace.ui.theme.Tokens
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/** S02 首页入口 id（单测 N-2 按此集合断言，与 SCOPE 矩阵口径一致）。 */
enum class HomeEntryId { RECV, BAG, LOAD, TRACE, SAMPLE, BATCH, FEED, OPERATION, FG_PRINT, REPORT }

// ===== GET /api/app/home 调用骨架（批次 A · docs/BACKEND-REQ-06.md）=====
// 字段级契约后端未定，以下为 APP 侧提案模型（语义对齐批次 A：进行中单据摘要 /
// 待收车·待打码·待装车计数 / 本人最近 N 条）；后端未实现前 404 等任何失败 ⇒
// HomeOverviewState.Unavailable（空态占位，不报错、不阻断入口）。

/** 进行中作业摘要（各域进行中的单据）。 */
@Serializable
data class HomeOngoingItem(
    val domain: String = "",
    val title: String = "",
    val status: String = "",
)

/** 最近作业（本人最近 N 条）。 */
@Serializable
data class HomeRecentItem(
    val time: String = "",
    val action: String = "",
    val ref: String = "",
)

/** 首页聚合（GET /api/app/home 提案载荷）。 */
@Serializable
data class HomeOverview(
    val ongoing: List<HomeOngoingItem> = emptyList(),
    val todo_recv: Int = 0,
    val todo_bag: Int = 0,
    val todo_load: Int = 0,
    val recent: List<HomeRecentItem> = emptyList(),
)

/** 数据位状态：加载中 / 就绪 / 不可用（失败与 404 统一走不可用——占位不报错）。 */
sealed interface HomeOverviewState {
    object Loading : HomeOverviewState
    data class Ready(val overview: HomeOverview) : HomeOverviewState
    object Unavailable : HomeOverviewState
}

/** 首页聚合仓储（骨架）：GET /api/app/home，任何失败/404 归一为 [ApiResult.Failure]。 */
class HomeRepository(
    private val api: ApiClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    suspend fun overview(): ApiResult<HomeOverview> =
        api.executeRead(HOME_PATH) { body -> parseOverview(body) }

    private fun parseOverview(body: String): HomeOverview {
        val root = json.parseToJsonElement(body) as? JsonObject ?: error("envelope 不是对象")
        // 容忍两种口径：裸对象 {…} 与 data 包裹 {data:{…}}（契约未定期的骨架宽容）
        val target = root["data"] as? JsonObject ?: root
        return json.decodeFromString(HomeOverview.serializer(), target.toString())
    }

    companion object {
        const val HOME_PATH = "/api/app/home"
    }
}

/** 底部 TabBar 规格（N-3：三项恒为「现场/追溯/我的」，全部角色一致，与角色无关）。 */
data class HomeTabItemSpec(val id: String, val label: String)

val HOME_TABS = listOf(
    HomeTabItemSpec("home", "现场"),
    HomeTabItemSpec("trace", "追溯"),
    HomeTabItemSpec("me", "我的"),
)

/**
 * 角色 → 入口 id 列表（MIMO-TASK-S02-NAV N-2）：
 * 多角色取并集（固定变体顺序 receiver → production → management/sales，LinkedHashSet 去重）；
 * 追溯为角色无关入口恒可见；未知/空角色（含系统管理员）仅剩角色无关入口。
 */
fun homeEntryIds(allRoles: List<String>): List<HomeEntryId> {
    val roles = allRoles.toSet()
    val out = LinkedHashSet<HomeEntryId>()
    if ("receiver" in roles) {
        out += listOf(HomeEntryId.RECV, HomeEntryId.BAG, HomeEntryId.LOAD, HomeEntryId.TRACE)
    }
    if ("production" in roles) {
        out += listOf(
            HomeEntryId.SAMPLE, HomeEntryId.BATCH, HomeEntryId.FEED,
            HomeEntryId.OPERATION, HomeEntryId.FG_PRINT, HomeEntryId.TRACE,
        )
    }
    if ("management" in roles || "sales" in roles) {
        out += listOf(HomeEntryId.TRACE, HomeEntryId.REPORT)
    }
    if (out.isEmpty()) out += HomeEntryId.TRACE
    return out.toList()
}

/** 角色中文（身份栏）：系统角色 sysadmin → 系统管理员，业务角色复用 S01 的 roleLabel 口径。 */
fun homeRoleLabel(code: String): String = when (code) {
    "sysadmin" -> "系统管理员"
    else -> roleLabel(code)
}

/** 入口域色（tokens 管线唯一色源，无手写 hex）。 */
private fun entryVisual(id: HomeEntryId): Pair<Color, Color> = when (id) {
    HomeEntryId.RECV, HomeEntryId.BAG ->
        Tokens.Color.Domain.Receiving.base to Tokens.Color.Domain.Receiving.on

    HomeEntryId.LOAD ->
        Tokens.Color.Domain.Shipment.base to Tokens.Color.Domain.Shipment.on

    HomeEntryId.TRACE ->
        Tokens.Color.Domain.Trace.base to Tokens.Color.Domain.Trace.on

    HomeEntryId.SAMPLE ->
        Tokens.Color.Domain.Qc.base to Tokens.Color.Domain.Qc.on

    HomeEntryId.BATCH, HomeEntryId.FEED, HomeEntryId.OPERATION, HomeEntryId.FG_PRINT ->
        Tokens.Color.Domain.Production.base to Tokens.Color.Domain.Production.on

    HomeEntryId.REPORT ->
        Tokens.Color.Brand.primarySoft to Tokens.Color.Brand.primary
}

/** 入口标题/副题（口径 = 任务包入口区文案）。 */
private fun entryText(id: HomeEntryId): Triple<String, String, androidx.compose.ui.graphics.vector.ImageVector> =
    when (id) {
        HomeEntryId.RECV -> Triple("收货", "预报 · 过磅 · 打码", JxIcons.RecvTruck)
        HomeEntryId.BAG -> Triple("建袋打码", "吨袋生成 · 标签", JxIcons.Bag)
        HomeEntryId.LOAD -> Triple("装车出场", "归集 · 出场", JxIcons.ShipOut)
        HomeEntryId.TRACE -> Triple("追溯", "扫码看全程", JxIcons.TraceForward)
        HomeEntryId.SAMPLE -> Triple("取样", "现场取样提交", JxIcons.Sample)
        HomeEntryId.BATCH -> Triple("建批", "新建生产批", JxIcons.Batch)
        HomeEntryId.FEED -> Triple("投料", "吨袋投料记录", JxIcons.Feed)
        HomeEntryId.OPERATION -> Triple("作业段", "作业打点计时", JxIcons.OpLog)
        HomeEntryId.FG_PRINT -> Triple("成品打码", "成品袋标签", JxIcons.FgBag)
        HomeEntryId.REPORT -> Triple("报表", "经营看板 · 只读", JxIcons.Report)
    }

/**
 * S02 现场首页：身份栏（首字头像 + 姓名 + 角色中文）+ 角色变体入口区 + 数据位。
 * 入口点击经 [onEntry] 交给宿主导航（MIMO-TASK-S02-NAV）。
 * 数据位由 [overviewState] 驱动（/api/app/home 骨架）：就绪显真值、失败/404 空态占位、
 * 加载中「--」——不造假数据。
 */
@Composable
fun HomeScreen(
    me: Me,
    versionName: String,
    onEntry: (HomeEntryId) -> Unit,
    modifier: Modifier = Modifier,
    overviewState: HomeOverviewState = HomeOverviewState.Loading,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Tokens.Space.space4),
        verticalArrangement = Arrangement.spacedBy(Tokens.Space.space4),
    ) {
        IdentityCard(me = me, versionName = versionName)

        val entries = homeEntryIds(me.allRoles)
        entries.chunked(2).forEach { pair ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(Tokens.Space.space3),
                modifier = Modifier.fillMaxWidth(),
            ) {
                pair.forEach { id ->
                    val (title, subtitle, icon) = entryText(id)
                    val (container, content) = entryVisual(id)
                    DomainBlock(
                        title = title,
                        subtitle = subtitle,
                        icon = icon,
                        containerColor = container,
                        contentColor = content,
                        onClick = { onEntry(id) },
                        // TODO(S02 数据位): 今日待办角标 —— 随 /api/app/home 就绪后接计数
                        badgeCount = null,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        OverviewSection(state = overviewState)
    }
}

/** 数据位三态渲染：加载中「--」/ 就绪真值 / 失败与 404 统一空态占位（不报错）。 */
@Composable
private fun OverviewSection(state: HomeOverviewState) {
    when (state) {
        HomeOverviewState.Loading -> {
            FieldCard(label = "进行中作业", value = "--", hint = "加载中")
            FieldCard(label = "今日待办", value = "--", hint = "加载中")
            FieldCard(label = "最近作业", value = "--", hint = "加载中")
        }

        HomeOverviewState.Unavailable -> EmptyState(
            title = "首页数据暂不可用",
            hint = "聚合接口未就绪或网络异常；入口功能不受影响，数据随后续批次接入。",
        )

        is HomeOverviewState.Ready -> {
            val overview = state.overview
            val ongoing = overview.ongoing
            val recent = overview.recent
            FieldCard(
                label = "进行中作业",
                value = if (ongoing.isEmpty()) "无" else ongoing.first().title,
                hint = if (ongoing.isEmpty()) {
                    "各域进行中单据"
                } else {
                    "共 ${ongoing.size} 项 · ${ongoing.first().status}"
                },
            )
            FieldCard(
                label = "今日待办",
                value = "收${overview.todo_recv} · 打码${overview.todo_bag} · 装车${overview.todo_load}",
                hint = "待收车 / 待打码 / 待装车",
            )
            FieldCard(
                label = "最近作业",
                value = if (recent.isEmpty()) "无" else recent.first().action,
                hint = if (recent.isEmpty()) {
                    "本人最近作业"
                } else {
                    "${recent.first().time} · 共 ${recent.size} 条"
                },
            )
        }
    }
}

/** 身份栏：姓名首字圆形头像 + me.name + 角色中文（多角色 · 分隔）。 */
@Composable
private fun IdentityCard(me: Me, versionName: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Tokens.Radius.lg))
            .background(Tokens.Color.Surface.card)
            .padding(Tokens.Space.space4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Tokens.Space.space4),
    ) {
        Box(
            modifier = Modifier
                .size(Tokens.Size.touchMin)
                .clip(CircleShape)
                .background(Tokens.Color.Brand.primary),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = me.name.trim().take(1).ifBlank { "?" },
                color = Tokens.Color.Brand.onPrimary,
                style = MaterialTheme.typography.titleLarge,
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.space1),
        ) {
            Text(
                text = me.name.ifBlank { "未登录用户" },
                color = Tokens.Color.Text.primary,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
            )
            Text(
                text = me.allRoles.joinToString(" · ") { homeRoleLabel(it) }
                    .ifBlank { "未分配角色" },
                color = Tokens.Color.Text.secondary,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 2,
            )
            Text(
                text = "v$versionName",
                color = Tokens.Color.Text.tertiary,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Preview(name = "w360", widthDp = 360, showBackground = true)
@Preview(name = "w390", widthDp = 390, showBackground = true)
@Preview(name = "w411", widthDp = 411, showBackground = true)
@Composable
private fun HomeScreenPreview() {
    JxPreviewTheme {
        HomeScreen(
            me = Me(name = "刘川", roles = listOf("receiver")),
            versionName = "local00",
            onEntry = {},
        )
    }
}
