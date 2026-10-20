package cn.hunanjiangxi.jxlabtrace.ui.rpt

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import cn.hunanjiangxi.jxlabtrace.rpt.ReportColumn
import cn.hunanjiangxi.jxlabtrace.rpt.ReportResult
import cn.hunanjiangxi.jxlabtrace.rpt.ReportUiState
import cn.hunanjiangxi.jxlabtrace.rpt.ReportViewModel
import cn.hunanjiangxi.jxlabtrace.ui.components.BigButton
import cn.hunanjiangxi.jxlabtrace.ui.components.BigButtonStyle
import cn.hunanjiangxi.jxlabtrace.ui.components.EmptyState
import cn.hunanjiangxi.jxlabtrace.ui.components.FieldCard
import cn.hunanjiangxi.jxlabtrace.ui.components.JxPreviewTheme
import cn.hunanjiangxi.jxlabtrace.ui.components.Skeleton
import cn.hunanjiangxi.jxlabtrace.ui.components.TopBar
import cn.hunanjiangxi.jxlabtrace.ui.theme.Tokens

/**
 * S14 报表（M11-2 · 管理层只读）：4 Tab 经营看板，接 `GET /api/rpt/:name`。
 * 卡片只读、刷新即最新；**无导出按钮、无写入口**（导出在 PC）。
 */
@Composable
fun ReportScreen(
    viewModel: ReportViewModel,
    modifier: Modifier = Modifier,
) {
    val tabIndex by viewModel.tabIndex.collectAsState()
    val state by viewModel.state.collectAsState()

    Column(modifier = modifier.fillMaxSize()) {
        TopBar(title = "经营看板 · 只读")
        ScrollableTabRow(
            selectedTabIndex = tabIndex,
            edgePadding = Tokens.Space.space2,
        ) {
            viewModel.tabs.forEachIndexed { index, name ->
                Tab(
                    selected = tabIndex == index,
                    onClick = { viewModel.selectTab(index) },
                    text = { Text(name.title, style = MaterialTheme.typography.labelLarge) },
                )
            }
        }
        when (val s = state) {
            is ReportUiState.Loading -> Column(
                modifier = Modifier.padding(Tokens.Space.space4),
            ) { Skeleton(height = Tokens.Space.space8) }

            is ReportUiState.Error -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(Tokens.Space.space4),
                verticalArrangement = Arrangement.spacedBy(Tokens.Space.space4),
            ) {
                EmptyState(title = "报表加载失败", hint = s.message)
                BigButton(text = "重试", onClick = viewModel::refresh)
            }

            is ReportUiState.Loaded -> ReportTable(
                result = s.result,
                onRefresh = viewModel::refresh,
                modifier = Modifier.padding(Tokens.Space.space4),
            )
        }
    }
}

/** 只读报表：标题 + 空态两态文案 + 行列表（列动态渲染）。 */
@Composable
private fun ReportTable(
    result: ReportResult,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = result.title.ifBlank { result.name },
            color = Tokens.Color.Text.primary,
            style = MaterialTheme.typography.titleMedium,
        )
        val emptyHint = result.emptyHint
        if (emptyHint != null) {
            EmptyState(
                title = if (!result.hasData) "数据未接入" else "本条件下无记录",
                hint = emptyHint,
                modifier = Modifier.padding(top = Tokens.Space.space5),
            )
        } else {
            LazyColumn(
                modifier = Modifier.padding(top = Tokens.Space.space3),
                verticalArrangement = Arrangement.spacedBy(Tokens.Space.space2),
            ) {
                items(result.rows) { row ->
                    // 每行一张字段卡：首列作标题，其余列作明细拼接
                    val first = result.columns.firstOrNull()
                    val rest = result.columns.drop(1)
                    FieldCard(
                        label = row[first?.key.orEmpty()].orEmpty().ifBlank { result.title },
                        value = rest.joinToString(" · ") { col ->
                            "${col.label} ${row[col.key].orEmpty().ifBlank { "—" }}"
                        }.ifBlank { "—" },
                    )
                }
            }
        }
        Text(
            text = "导出与明细在 PC 端；本页只读，刷新即最新",
            color = Tokens.Color.Text.tertiary,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(top = Tokens.Space.space3),
        )
        BigButton(text = "刷新", onClick = onRefresh, style = BigButtonStyle.Quiet)
    }
}

@Preview(name = "w360", widthDp = 360, showBackground = true)
@Preview(name = "w390", widthDp = 390, showBackground = true)
@Preview(name = "w411", widthDp = 411, showBackground = true)
@Composable
private fun ReportTablePreview() {
    JxPreviewTheme {
        ReportTable(
            result = ReportResult(
                name = "quality-trend",
                title = "质量趋势",
                columns = listOf(
                    ReportColumn("period", "周期"),
                    ReportColumn("lots", "批次"),
                    ReportColumn("pass_rate", "合格率"),
                ),
                rows = listOf(
                    mapOf("period" to "2026-10", "lots" to "18", "pass_rate" to "97%"),
                ),
                hasData = true,
            ),
            onRefresh = {},
        )
    }
}
