package cn.hunanjiangxi.jxlabtrace.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.unit.dp
import cn.hunanjiangxi.jxlabtrace.app.HomeOverview
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
import kotlinx.serialization.json.Json

/** S02 首页入口 id（单测 N-2 按此集合断言，与 SCOPE 矩阵口径一致）。 */
enum class HomeEntryId { RECV, BAG, LOAD, TRACE, SAMPLE, BATCH, FEED, OPERATION, FG_PRINT, REPORT }

// ===== GET /api/app/home（批次 A · docs/APP-API-A-B.md §3 定稿契约）=====
// 字段级模型见 app/HomeOverview；任何失败 ⇒ HomeOverviewState.Unavailable
//（空态占位，不报错、不阻断入口）。401 由 SessionCoordinator 重授权路径处理。

/** 数据位状态：加载中 / 就绪 / 不可用（失败统一走不可用——占位不报错）。 */
sealed interface HomeOverviewState {
    object Loading : HomeOverviewState
    data class Ready(val overview: HomeOverview) : HomeOverviewState
    object Unavailable : HomeOverviewState
}

/** 首页聚合仓储：GET /api/app/home（§3）。 */
class HomeRepository(
    private val api: ApiClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    suspend fun overview(): ApiResult<HomeOverview> =
        api.executeRead(HOME_PATH) { body ->
            json.decodeFromString(HomeOverview.serializer(), body)
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
    // 系统管理员 = 全量入口（管理域角色可现场操作测试，2026-10-23 项目方确认）
    if ("sysadmin" in roles) {
        out += HomeEntryId.entries
        return out.toList()
    }
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
 * S02 现场首页：身份栏（首字头像 + 姓名 + 角色中文）+ 角色变体入口区 + 数据位三卡。
 * 入口点击经 [onEntry] 交给宿主导航（MIMO-TASK-S02-NAV）。
 * 数据位由 [overviewState] 驱动（/api/app/home §3 契约）：
 * 进行中卡=in_progress+count 角标；待办卡=todos；最近作业卡=recent。
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

/**
 * 进行中卡：in_progress 列表 + count 合计角标文案；最新一条 title/status 作主值。
 * 真机 Bug-1：后端 title 可能为「/」等纯符号空壳——须含字母/数字才算有效，
 * 否则回落域 label，再回落「无」；hint 拼接时空 label 跳过分隔符。
 */
internal fun inProgressCardValue(overview: HomeOverview): Pair<String, String> {
    val items = overview.in_progress
    if (items.isEmpty()) return "无" to "各域进行中单据"
    val total = items.sumOf { it.count }
    val latest = items.firstNotNullOfOrNull { it.latest }
    val value = latest?.title?.takeIf { t ->
        t.isNotBlank() && t.any { ch -> ch.isLetterOrDigit() }
    } ?: items.firstOrNull { it.label.isNotBlank() }?.label
        ?: "无"
    val hint = buildString {
        append("在办 $total 项")
        items.firstOrNull { it.count > 0 && it.label.isNotBlank() }
            ?.let { append(" · ${it.label} ${it.count}") }
        latest?.status?.takeIf { it.isNotBlank() }?.let { append(" · $it") }
    }
    return value to hint
}

/**
 * 待办卡逐项行（真机 Bug-2：不拼单行长串，Card 内 Column 每项一行「label：count」）。
 * 空列表 ⇒ 单行「无」。
 */
internal fun todosCardLines(overview: HomeOverview): List<String> {
    val todos = overview.todos
    if (todos.isEmpty()) return listOf("无")
    return todos.filter { it.label.isNotBlank() }.map { "${it.label}：${it.count}" }
        .ifEmpty { listOf("无") }
}

/** 真机 Bug-3：action 原始码 → 人话（映射表可扩展；未知码原样显示）。 */
internal val RECENT_ACTION_LABELS: Map<String, String> = mapOf(
    "feishu_login" to "飞书登录",
    "login" to "登录",
    "create" to "新建",
    "arrive" to "到货确认",
    "weigh" to "过磅",
    "bag_gen" to "建袋打码",
    "return" to "退车",
    "reprint" to "标签补打",
)

/**
 * 真机 Bug-3：at 格式化——RFC3339 / `YYYY-MM-DD HH:MM:SS` 均归一 `MM-dd HH:mm`；
 * 解析失败原样兜底（不吞时间信息）。
 */
internal fun formatRecentAt(at: String): String {
    if (at.isBlank()) return ""
    // RFC3339（含时区，如 2026-10-22T15:39:10.886+08:00）
    runCatching {
        val t = java.time.OffsetDateTime.parse(at)
        return t.format(java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm"))
    }
    // 契约 §2 at 类时间戳 YYYY-MM-DD HH:MM:SS
    runCatching {
        val t = java.time.LocalDateTime.parse(
            at,
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
        )
        return t.format(java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm"))
    }
    return at
}

/** 最近作业卡：recent 首条 action 人话 + 格式化 at（§3.1 最多 10 条按 id 倒序）。 */
internal fun recentCardValue(overview: HomeOverview): Pair<String, String> {
    val recent = overview.recent.firstOrNull() ?: return "无" to "本人最近作业"
    val actionLabel = RECENT_ACTION_LABELS[recent.action]
        ?: recent.action.ifBlank { recent.entity }
    val atText = formatRecentAt(recent.at)
    return actionLabel to (if (atText.isBlank()) "共 ${overview.recent.size} 条" else "$atText · 共 ${overview.recent.size} 条")
}

/** 数据位三态渲染：加载中「--」/ 就绪三卡真值 / 失败统一空态占位（不报错）。 */
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
            val (inProgressValue, inProgressHint) = inProgressCardValue(overview)
            val (recentValue, recentHint) = recentCardValue(overview)
            FieldCard(label = "进行中作业", value = inProgressValue, hint = inProgressHint)
            // 待办卡逐项渲染（Bug-2：不拼单行长串，Card 内 Column 每项一行）
            TodosCard(lines = todosCardLines(overview))
            FieldCard(label = "最近作业", value = recentValue, hint = recentHint)
        }
    }
}

/**
 * 今日待办卡：Card 内 Column 逐项渲染（真机 Bug-2——多项拼一行会溢出挤压）。
 * 每项一行「label：count」，风格对齐 FieldCard（同 surface/label 体系）。
 */
@Composable
private fun TodosCard(lines: List<String>, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Tokens.Radius.md))
            .background(Tokens.Color.Surface.card)
            .border(1.dp, Tokens.Color.Surface.border, RoundedCornerShape(Tokens.Radius.md))
            .padding(Tokens.Space.space4),
        verticalArrangement = Arrangement.spacedBy(Tokens.Space.space1),
    ) {
        Text(
            text = "今日待办",
            color = Tokens.Color.Text.tertiary,
            style = MaterialTheme.typography.labelMedium,
        )
        lines.forEach { line ->
            Text(
                text = line,
                color = Tokens.Color.Text.primary,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        Text(
            text = "今日待办 · 服务端聚合",
            color = Tokens.Color.Text.tertiary,
            style = MaterialTheme.typography.labelSmall,
        )
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
            overviewState = HomeOverviewState.Ready(
                HomeOverview(
                    in_progress = listOf(
                        cn.hunanjiangxi.jxlabtrace.app.HomeInProgressItem(
                            key = "notice_open",
                            label = "预报中车次",
                            domain = "recv",
                            count = 2,
                            latest = cn.hunanjiangxi.jxlabtrace.app.HomeLatest(
                                id = 12,
                                code = "N-20261021-003",
                                title = "湘F·12345 / 李师傅",
                                status = "预报",
                                at = "2026-10-21 09:12:00",
                            ),
                        ),
                    ),
                    todos = listOf(
                        cn.hunanjiangxi.jxlabtrace.app.HomeTodoItem(
                            key = "await_receive",
                            label = "待收",
                            domain = "recv",
                            count = 1,
                        ),
                    ),
                    recent = listOf(
                        cn.hunanjiangxi.jxlabtrace.app.HomeRecentItem(
                            id = 88,
                            at = "2026-10-21 09:20:00",
                            action = "create",
                            actor = "张三",
                        ),
                    ),
                ),
            ),
        )
    }
}
