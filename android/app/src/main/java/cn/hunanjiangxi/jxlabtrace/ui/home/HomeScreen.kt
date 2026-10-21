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
import cn.hunanjiangxi.jxlabtrace.ui.components.DomainBlock
import cn.hunanjiangxi.jxlabtrace.ui.components.FieldCard
import cn.hunanjiangxi.jxlabtrace.ui.components.JxPreviewTheme
import cn.hunanjiangxi.jxlabtrace.ui.icons.JxIcons
import cn.hunanjiangxi.jxlabtrace.ui.login.roleLabel
import cn.hunanjiangxi.jxlabtrace.ui.theme.Tokens

/** S02 首页入口 id（单测 N-2 按此集合断言，与 SCOPE 矩阵口径一致）。 */
enum class HomeEntryId { RECV, BAG, LOAD, TRACE, SAMPLE, BATCH, FEED, OPERATION, FG_PRINT, REPORT }

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
 * S02 现场首页：身份栏（首字头像 + 姓名 + 角色中文）+ 角色变体入口区 + 数据位占位。
 * 入口点击经 [onEntry] 交给宿主导航（MIMO-TASK-S02-NAV）。
 * 数据位（进行中/待办/最近作业）本期为结构占位，不造假数据。
 */
@Composable
fun HomeScreen(
    me: Me,
    versionName: String,
    onEntry: (HomeEntryId) -> Unit,
    modifier: Modifier = Modifier,
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
                        // TODO(S02 数据位): 今日待办角标 —— 数据接口后续批次接入，本期不出假数
                        badgeCount = null,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        // TODO(S02 数据位): 进行中 / 待办 / 最近作业 —— 结构占位（"--"），数据接口后续批次接入
        FieldCard(label = "进行中作业", value = "--", hint = "数据接口后续批次接入")
        FieldCard(label = "今日待办", value = "--", hint = "数据接口后续批次接入")
        FieldCard(label = "最近作业", value = "--", hint = "数据接口后续批次接入")
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
