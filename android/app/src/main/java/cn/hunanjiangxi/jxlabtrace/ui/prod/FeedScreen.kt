package cn.hunanjiangxi.jxlabtrace.ui.prod

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import cn.hunanjiangxi.jxlabtrace.prod.FeedCorrectForm
import cn.hunanjiangxi.jxlabtrace.prod.FeedCorrectSession
import cn.hunanjiangxi.jxlabtrace.prod.FeedProgress
import cn.hunanjiangxi.jxlabtrace.prod.FeedRecord
import cn.hunanjiangxi.jxlabtrace.prod.FeedUiState
import cn.hunanjiangxi.jxlabtrace.prod.FeedViewModel
import cn.hunanjiangxi.jxlabtrace.ui.components.BigButton
import cn.hunanjiangxi.jxlabtrace.ui.components.BigButtonStyle
import cn.hunanjiangxi.jxlabtrace.ui.components.EmptyState
import cn.hunanjiangxi.jxlabtrace.ui.components.FieldCard
import cn.hunanjiangxi.jxlabtrace.ui.components.JxPreviewTheme
import cn.hunanjiangxi.jxlabtrace.ui.components.NumPad
import cn.hunanjiangxi.jxlabtrace.ui.components.PillTone
import cn.hunanjiangxi.jxlabtrace.ui.components.Skeleton
import cn.hunanjiangxi.jxlabtrace.ui.components.StatusPill
import cn.hunanjiangxi.jxlabtrace.ui.components.TopBar
import cn.hunanjiangxi.jxlabtrace.ui.theme.Tokens

/**
 * S09 生产 · 投料（M7-2/M7-3/M7-4）：进入即连续扫码（宿主挂 ScanViewModel）；
 * 进度实时 +1；每条记录带班次内更正入口（超班次置灰 + PC 指引，不发请求）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FeedScreen(
    viewModel: FeedViewModel,
    onStartScan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val message by viewModel.message.collectAsState()
    val correcting by viewModel.correcting.collectAsState()
    val correctDone by viewModel.correctDone.collectAsState()

    // 更正页整页替换（班次内可提交；超班次只给 PC 指引）
    val activeSession = correcting
    if (activeSession != null) {
        FeedCorrectSheet(
            session = activeSession,
            onSubmit = { category, detail, weight -> viewModel.submitCorrect(category, detail, weight) },
            onCancel = viewModel::cancelCorrect,
            modifier = modifier,
        )
    } else {
        when (val s = state) {
            null -> Column(modifier = modifier.fillMaxSize()) {
                TopBar(title = "投料")
                Skeleton(Modifier.padding(Tokens.Space.space4))
            }

            is FeedUiState.Error -> Column(modifier = modifier.fillMaxSize()) {
                TopBar(title = "投料")
                EmptyState(
                    title = "投料页加载失败",
                    hint = s.message,
                    modifier = Modifier.padding(Tokens.Space.space6),
                )
                BigButton(text = "重试", onClick = viewModel::load, style = BigButtonStyle.Quiet)
            }

            is FeedUiState.Active -> Column(modifier = modifier.fillMaxSize()) {
                TopBar(title = "投料 · ${s.batch.human.ifBlank { s.batch.code }}")
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(Tokens.Space.space4),
                    verticalArrangement = Arrangement.spacedBy(Tokens.Space.space3),
                ) {
                    // KPI：本地即时累计（M7-2）
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "${s.fedCount}",
                            style = MaterialTheme.typography.headlineMedium,
                            color = Tokens.Color.Brand.primary,
                        )
                        StatusPill(text = "进行中", tone = PillTone.Info)
                    }
                    Text(
                        text = FeedProgress.label(s.fedCount),
                        color = Tokens.Color.Text.secondary,
                        style = MaterialTheme.typography.labelLarge,
                    )

                    val errorMsg = message
                    if (errorMsg != null) {
                        Text(
                            text = errorMsg,
                            color = Tokens.Color.Status.danger,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }

                    BigButton(text = "扫下一袋", onClick = onStartScan)

                    Text("最近投料", style = MaterialTheme.typography.titleSmall)
                    LazyColumn {
                        items(s.feeds, key = { it.id }) { feed ->
                            FeedRow(feed = feed, onCorrect = { viewModel.openCorrect(feed) })
                        }
                    }
                }
            }
        }
    }

    androidx.compose.runtime.LaunchedEffect(correctDone) {
        correctDone?.let { viewModel.clearCorrectDone() }
    }
}

@Composable
private fun FeedRow(feed: FeedRecord, onCorrect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onCorrect)
            .padding(vertical = Tokens.Space.space2),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = "第 ${feed.id} 袋 · ${feed.feed_weight?.let { "${it} t" } ?: "未录重"}" +
                    if (feed.version >= 2) " · v${feed.version}" else "",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = listOfNotNull(feed.fed_at, feed.bag_human.ifBlank { null })
                    .joinToString(" · "),
                color = Tokens.Color.Text.tertiary,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        Text(
            text = "更正",
            color = Tokens.Color.Brand.primary,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

/** 更正页：原记录 + 新重量 + 原因三选一 + 说明。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FeedCorrectSheet(
    session: FeedCorrectSession,
    onSubmit: (category: String?, detail: String, weight: Double?) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var reason by remember { mutableStateOf<String?>(null) }
    var detail by remember { mutableStateOf("") }
    var weightText by remember {
        mutableStateOf(session.feed.feed_weight?.let { formatWeight(it) } ?: "")
    }
    var editingWeight by remember { mutableStateOf(false) }
    val weight = weightText.toDoubleOrNull()
    val valid = FeedCorrectForm.isValid(reason, detail, weight)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(Tokens.Space.space4),
        verticalArrangement = Arrangement.spacedBy(Tokens.Space.space3),
    ) {
        Text(
            text = "投料更正 · 记录 #${session.feed.id}",
            style = MaterialTheme.typography.titleMedium,
        )
        FieldCard(
            label = "原记录",
            value = "${session.feed.feed_weight ?: "—"} t · ${session.feed.fed_at ?: "—"} · " +
                "版本 v${session.feed.version}",
            hint = session.feed.bag_human.ifBlank { session.feed.bag_code },
        )


        FieldCard(label = "更正为（吨）", value = weightText.ifBlank { "0" }, hint = "t")
        if (editingWeight) {
            NumPad(
                onDigit = { d ->
                    if (d != ".") weightText = (weightText + d).filter { it.isDigit() || it == '.' }.take(8)
                },
                onBackspace = { weightText = weightText.dropLast(1) },
                allowDecimal = true,
                onConfirm = { editingWeight = false },
            )
        } else {
            BigButton(text = "修改重量", onClick = { editingWeight = true }, style = BigButtonStyle.Quiet)
        }

        Text("原因（必填三选一）", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.space2)) {
            FeedCorrectForm.REASONS.forEach { r ->
                FilterChip(selected = reason == r, onClick = { reason = r }, label = { Text(r) })
            }
        }
        OutlinedTextField(
            value = detail,
            onValueChange = { detail = it },
            label = { Text("原因说明（必填）") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        BigButton(
            text = "提交更正（v${session.feed.version + 1}）",
            onClick = { onSubmit(reason, detail, weight) },
            enabled = valid,
        )
        BigButton(text = "取消", onClick = onCancel, style = BigButtonStyle.Quiet)
    }
}

private fun formatWeight(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()

@Preview(name = "w360", widthDp = 360, showBackground = true)
@Preview(name = "w390", widthDp = 390, showBackground = true)
@Preview(name = "w411", widthDp = 411, showBackground = true)
@Composable
private fun FeedRowPreview() {
    JxPreviewTheme {
        Column(modifier = Modifier.padding(Tokens.Space.space4)) {
            FeedRow(
                feed = FeedRecord(
                    id = 19,
                    feed_weight = 1.02,
                    fed_at = "2026-10-20 10:22:41",
                    bag_human = "1B-CG-0001-0002-261020-01-019-000-8",
                ),
                onCorrect = {},
            )
        }
    }
}
