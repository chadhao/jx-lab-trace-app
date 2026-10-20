package cn.hunanjiangxi.jxlabtrace.ui.trace

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import cn.hunanjiangxi.jxlabtrace.trace.TraceTimelineNode
import cn.hunanjiangxi.jxlabtrace.trace.TraceUiState
import cn.hunanjiangxi.jxlabtrace.trace.TraceView
import cn.hunanjiangxi.jxlabtrace.trace.TraceViewModel
import cn.hunanjiangxi.jxlabtrace.ui.components.BigButton
import cn.hunanjiangxi.jxlabtrace.ui.components.BigButtonStyle
import cn.hunanjiangxi.jxlabtrace.ui.components.EmptyState
import cn.hunanjiangxi.jxlabtrace.ui.components.JxPreviewTheme
import cn.hunanjiangxi.jxlabtrace.ui.components.Skeleton
import cn.hunanjiangxi.jxlabtrace.ui.components.TopBar
import cn.hunanjiangxi.jxlabtrace.ui.theme.Tokens

/**
 * S13 追溯（M11）：ScanHero 大扫码入口 + 纵向时间线（车→批→样→出货，全只读）。
 * 任一节点可点开详情；无任何写入口（M11-1）。
 */
@Composable
fun TraceScreen(
    viewModel: TraceViewModel,
    onStartScan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()

    Column(modifier = modifier.fillMaxSize()) {
        TopBar(title = "追溯")
        when (val s = state) {
            is TraceUiState.Idle -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(Tokens.Space.space4),
                verticalArrangement = Arrangement.spacedBy(Tokens.Space.space4),
            ) {
                BigButton(text = "扫码追溯（车次 / 吨袋 / 成品批）", onClick = onStartScan)
                Text(
                    text = "扫袋码 / 车号 / 批号，一屏看完「谁的料 → 哪批 → 测了什么」；全程只读",
                    color = Tokens.Color.Text.secondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            is TraceUiState.Loading -> Column(
                modifier = Modifier.padding(Tokens.Space.space4),
            ) { Skeleton() }

            is TraceUiState.Error -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(Tokens.Space.space4),
                verticalArrangement = Arrangement.spacedBy(Tokens.Space.space4),
            ) {
                EmptyState(title = "追溯失败", hint = s.message)
                BigButton(text = "重新扫码", onClick = onStartScan, style = BigButtonStyle.Quiet)
            }

            is TraceUiState.NoFlow -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(Tokens.Space.space4),
                verticalArrangement = Arrangement.spacedBy(Tokens.Space.space4),
            ) {
                EmptyState(title = "查无流向", hint = s.hint)
                BigButton(text = "重新扫码", onClick = onStartScan)
            }

            is TraceUiState.Loaded -> TraceTimeline(
                view = s.view,
                onNodeClick = { node ->
                    when {
                        node.batchId > 0 -> viewModel.openBatchArchive(node.batchId)
                        node.genealogyBagCode != null ->
                            viewModel.openGenealogy(node.genealogyBagCode!!)
                        else -> Unit
                    }
                },
                modifier = Modifier.padding(Tokens.Space.space4),
            )
        }
    }
}

/** 纵向时间线（S13 稿：节点圆点 + 标题 + 副标题；可点开的节点带提示）。 */
@Composable
private fun TraceTimeline(
    view: TraceView,
    onNodeClick: (TraceTimelineNode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = view.headerType,
            color = Tokens.Color.Text.tertiary,
            style = MaterialTheme.typography.labelLarge,
        )
        Text(
            text = view.headerCode,
            color = Tokens.Color.Text.primary,
            style = MaterialTheme.typography.titleMedium,
        )
        if (view.headerMeta.isNotBlank()) {
            Text(
                text = view.headerMeta,
                color = Tokens.Color.Text.secondary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (!view.hasFlow) {
            Text(
                text = "查无流向",
                color = Tokens.Color.Text.tertiary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = Tokens.Space.space3),
            )
        }
        LazyColumn(
            modifier = Modifier.padding(top = Tokens.Space.space4),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.space3),
        ) {
            itemsIndexed(view.nodes) { _, node ->
                TimelineRow(
                    node = node,
                    onClick = if (node.clickable) {
                        { onNodeClick(node) }
                    } else {
                        null
                    },
                )
            }
        }
        Text(
            text = "任一节点可点开；全程只读，无写入口",
            color = Tokens.Color.Text.tertiary,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(top = Tokens.Space.space3),
        )
    }
}

@Composable
private fun TimelineRow(
    node: TraceTimelineNode,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(Tokens.Size.iconSm)
                .clip(CircleShape)
                .background(Tokens.Color.Brand.primary),
        )
        Column(
            modifier = Modifier
                .padding(start = Tokens.Space.space3)
                .weight(1f),
        ) {
            Text(
                text = node.title,
                color = Tokens.Color.Text.primary,
                style = MaterialTheme.typography.titleSmall,
            )
            if (node.subtitle.isNotBlank()) {
                Text(
                    text = node.subtitle,
                    color = Tokens.Color.Text.secondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        if (node.clickable) {
            Text(
                text = "详情",
                color = Tokens.Color.Brand.primary,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Preview(name = "w360", widthDp = 360, showBackground = true)
@Preview(name = "w390", widthDp = 390, showBackground = true)
@Preview(name = "w411", widthDp = 411, showBackground = true)
@Composable
private fun TraceTimelinePreview() {
    JxPreviewTheme {
        TraceTimeline(
            view = TraceView(
                headerCode = "1B-CG-0001-0002-261020-01-099-000-8",
                headerType = "原料吨袋",
                headerMeta = "岳阳观盛 · 石油焦 · 2026-10-18",
                nodes = listOf(
                    TraceTimelineNode("原料车次 A-0001", "湘F·A1234 · 2026-10-18"),
                    TraceTimelineNode("生产批 2410-007", "2026-10-19 · 进行中", batchId = 7),
                    TraceTimelineNode("成品批 D-2410-003", "石油焦 · 39.8 t"),
                    TraceTimelineNode("出货单 CH261020-001", "岳阳观盛 · 湘F·A1234"),
                ),
                hasFlow = true,
            ),
            onNodeClick = {},
        )
    }
}
