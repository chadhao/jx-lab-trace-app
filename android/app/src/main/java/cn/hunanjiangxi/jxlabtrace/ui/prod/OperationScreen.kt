package cn.hunanjiangxi.jxlabtrace.ui.prod

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import cn.hunanjiangxi.jxlabtrace.prod.OperationTracker
import cn.hunanjiangxi.jxlabtrace.prod.OperationUiState
import cn.hunanjiangxi.jxlabtrace.prod.OperationViewModel
import cn.hunanjiangxi.jxlabtrace.ui.components.BigButton
import cn.hunanjiangxi.jxlabtrace.ui.components.BigButtonStyle
import cn.hunanjiangxi.jxlabtrace.ui.components.EmptyState
import cn.hunanjiangxi.jxlabtrace.ui.components.JxPreviewTheme
import cn.hunanjiangxi.jxlabtrace.ui.components.Skeleton
import cn.hunanjiangxi.jxlabtrace.ui.components.TopBar
import cn.hunanjiangxi.jxlabtrace.ui.theme.Tokens

/**
 * S10 生产 · 作业段（M7-5）：开始/结束两点打点；本地计时仅展示；
 * 重复点「结束」幂等；断网提交拦截但状态保留。
 */
@Composable
fun OperationScreen(
    viewModel: OperationViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val message by viewModel.message.collectAsState()
    val elapsed by viewModel.elapsedMillis.collectAsState()

    Column(modifier = modifier.fillMaxSize()) {
        TopBar(title = "作业段")
        when (val s = state) {
            is OperationUiState.Error -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(Tokens.Space.space4),
                verticalArrangement = Arrangement.spacedBy(Tokens.Space.space3),
            ) {
                EmptyState(title = "作业段加载失败", hint = s.message)
                BigButton(text = "重试", onClick = viewModel::load, style = BigButtonStyle.Quiet)
            }

            else -> {
                val phase = when (s) {
                    is OperationUiState.Ready -> s.phase
                    is OperationUiState.Submitting -> s.phase
                    is OperationUiState.Done -> OperationTracker.Phase.Ended
                    is OperationUiState.Error -> OperationTracker.Phase.Idle
                }
                val operations = when (s) {
                    is OperationUiState.Ready -> s.operations
                    is OperationUiState.Done -> s.operations
                    else -> emptyList()
                }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(Tokens.Space.space4),
                    verticalArrangement = Arrangement.spacedBy(Tokens.Space.space3),
                ) {
                    // 当前段卡片
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(Tokens.Space.space2),
                    ) {
                        Text(
                            text = "当前段",
                            color = Tokens.Color.Text.secondary,
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Text(
                            text = formatElapsed(elapsed),
                            style = MaterialTheme.typography.headlineMedium,
                            color = when (phase) {
                                OperationTracker.Phase.Running -> Tokens.Color.Brand.primary
                                OperationTracker.Phase.Ended -> Tokens.Color.Status.success
                                OperationTracker.Phase.Idle -> Tokens.Color.Text.tertiary
                            },
                        )
                        val phaseText = when (phase) {
                            OperationTracker.Phase.Idle -> "未开始 · 点「开始」打点"
                            OperationTracker.Phase.Running -> "计时中 · 点「结束」提交本段"
                            OperationTracker.Phase.Ended -> "已结束 · 可开始下一段"
                        }
                        Text(
                            text = phaseText,
                            color = Tokens.Color.Text.secondary,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }

                    val errorMsg = message
                    if (errorMsg != null) {
                        Text(
                            text = errorMsg,
                            color = Tokens.Color.Status.danger,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }

                    when (phase) {
                        OperationTracker.Phase.Idle ->
                            BigButton(text = "开始", onClick = viewModel::start)
                        OperationTracker.Phase.Running ->
                            BigButton(text = "结束本段", onClick = viewModel::end, style = BigButtonStyle.Danger)
                        OperationTracker.Phase.Ended -> BigButton(
                            text = "开始下一段",
                            onClick = viewModel::startNext,
                        )
                    }

                    Text("已记段", style = MaterialTheme.typography.titleSmall)
                    LazyColumn {
                        items(operations, key = { it.id }) { op ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = Tokens.Space.space2),
                            ) {
                                Text(
                                    text = "段 ${op.seq} · ${op.operator.ifBlank { "—" }}",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    text = "${op.start_at ?: "—"} – ${op.end_at ?: "进行中"}",
                                    color = Tokens.Color.Text.tertiary,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun formatElapsed(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val sec = totalSeconds % 60
    return "%02d:%02d:%02d".format(h, m, sec)
}

@Preview(name = "w360", widthDp = 360, showBackground = true)
@Preview(name = "w390", widthDp = 390, showBackground = true)
@Preview(name = "w411", widthDp = 411, showBackground = true)
@Composable
private fun OperationPreview() {
    JxPreviewTheme {
        Column(modifier = Modifier.padding(Tokens.Space.space4)) {
            Text(text = formatElapsed(6_138_000), style = MaterialTheme.typography.headlineMedium)
            Text(
                text = "段 1 · 甲班 · 2026-10-20 07:30:00 – 2026-10-20 08:10:00",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
