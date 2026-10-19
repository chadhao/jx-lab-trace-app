package cn.hunanjiangxi.jxlabtrace.print

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import cn.hunanjiangxi.jxlabtrace.ui.components.BigButton
import cn.hunanjiangxi.jxlabtrace.ui.components.BigButtonStyle
import cn.hunanjiangxi.jxlabtrace.ui.components.JxPreviewTheme
import cn.hunanjiangxi.jxlabtrace.ui.components.StatusPill
import cn.hunanjiangxi.jxlabtrace.ui.components.PillTone
import cn.hunanjiangxi.jxlabtrace.ui.theme.Tokens

/**
 * S15 打印机状态卡（M9-3）：
 * 未连接 ⇒ 「去连接」出路（不是白屏/直接失败）；已连接 ⇒ 自检打印入口。
 */
@Composable
fun PrinterGateCard(
    state: PrinterUiState,
    onGoSettings: () -> Unit,
    onSelfTest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Tokens.Radius.lg))
            .background(Tokens.Color.Surface.card)
            .padding(Tokens.Space.space4),
        verticalArrangement = Arrangement.spacedBy(Tokens.Space.space3),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "标签打印机",
                color = Tokens.Color.Text.primary,
                style = MaterialTheme.typography.titleMedium,
            )
            when (state) {
                is PrinterUiState.Connected ->
                    StatusPill(text = "已连接", tone = PillTone.Success)
                is PrinterUiState.Connecting ->
                    StatusPill(text = "连接中", tone = PillTone.Warning)
                is PrinterUiState.Error ->
                    StatusPill(text = "连接异常", tone = PillTone.Danger)
                PrinterUiState.Disconnected ->
                    StatusPill(text = "未连接", tone = PillTone.Neutral)
            }
        }

        when (state) {
            is PrinterUiState.Connected -> {
                Text(
                    text = "${state.deviceName} · 主通道 ${state.protocol} · 纸宽 ${PrinterProfile.SIZE_MM}mm",
                    color = Tokens.Color.Text.secondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Tokens.Space.space3),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    BigButton(
                        text = "自检打印",
                        onClick = onSelfTest,
                        style = BigButtonStyle.Quiet,
                        modifier = Modifier.weight(1f),
                    )
                    BigButton(
                        text = "管理",
                        onClick = onGoSettings,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            PrinterUiState.Disconnected -> {
                Text(
                    text = printerDisconnectedHint(state)
                        ?: "打印机未连接 · 去连接",
                    color = Tokens.Color.Status.warning,
                    style = MaterialTheme.typography.bodyMedium,
                )
                BigButton(text = "去连接", onClick = onGoSettings)
            }
            is PrinterUiState.Connecting -> {
                Text(
                    text = "正在连接打印机…",
                    color = Tokens.Color.Text.secondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            is PrinterUiState.Error -> {
                Text(
                    text = state.reason,
                    color = Tokens.Color.Status.danger,
                    style = MaterialTheme.typography.bodyMedium,
                )
                BigButton(text = "去连接", onClick = onGoSettings, style = BigButtonStyle.Quiet)
            }
        }
    }
}

@Preview(name = "w360", widthDp = 360, showBackground = true)
@Preview(name = "w390", widthDp = 390, showBackground = true)
@Preview(name = "w411", widthDp = 411, showBackground = true)
@Composable
private fun PrinterGateCardPreview() {
    JxPreviewTheme {
        Column(verticalArrangement = Arrangement.spacedBy(Tokens.Space.space3)) {
            PrinterGateCard(PrinterUiState.Disconnected, {}, {})
            PrinterGateCard(PrinterUiState.Connected("KM-118BT"), {}, {})
        }
    }
}
