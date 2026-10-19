package cn.hunanjiangxi.jxlabtrace.ui.recv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import cn.hunanjiangxi.jxlabtrace.recv.RecvTruck
import cn.hunanjiangxi.jxlabtrace.recv.WeighLogic
import cn.hunanjiangxi.jxlabtrace.recv.WeighUiState
import cn.hunanjiangxi.jxlabtrace.recv.WeighViewModel
import cn.hunanjiangxi.jxlabtrace.ui.components.BigButton
import cn.hunanjiangxi.jxlabtrace.ui.components.FieldCard
import cn.hunanjiangxi.jxlabtrace.ui.components.JxPreviewTheme
import cn.hunanjiangxi.jxlabtrace.ui.components.NumPad
import cn.hunanjiangxi.jxlabtrace.ui.components.TopBar
import cn.hunanjiangxi.jxlabtrace.ui.theme.Tokens

/**
 * S05 过磅（M05）：皮重历史默认 + 毛重 NumPad + 净重自动；提交 POST weigh。
 */
@Composable
fun WeighScreen(
    viewModel: WeighViewModel,
    onWeighed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val gross by viewModel.gross.collectAsState()
    val tare by viewModel.tare.collectAsState()
    val net = WeighLogic.netTons(gross, tare)

    (state as? WeighUiState.Done)?.let {
        onWeighed()
    }

    Column(modifier = modifier.fillMaxSize()) {
        val truck = when (val s = state) {
            is WeighUiState.Editing -> s.truck
            is WeighUiState.Done -> s.truck
            else -> RecvTruck()
        }
        TopBar(title = "过磅 · ${truck.plate_no.ifBlank { "车次" }}")
        Text(
            text = "预报 · ${truck.customer_name} · ${truck.material_name}",
            color = Tokens.Color.Text.secondary,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = Tokens.Space.space4),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(Tokens.Space.space4),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.space3),
        ) {
            FieldCard(
                label = "皮重",
                value = tare.ifBlank { "0" },
                hint = truck.tare_weight?.let { "历史 ${WeighViewModel.formatTons(it)} t · 可改" }
                    ?: "可改",
            )
            FieldCard(
                label = "毛重（必填）",
                value = gross.ifBlank { "0" },
                valueColor = Tokens.Color.Brand.primary,
            )
            FieldCard(
                label = "净重（自动）",
                value = net?.let { WeighViewModel.formatTons(it) } ?: "—",
                valueColor = Tokens.Color.Status.success,
            )
            Text(
                text = "皮重 ← NumPad 下方键 | 毛重 ← 当前录入",
                color = Tokens.Color.Text.tertiary,
                style = MaterialTheme.typography.labelSmall,
            )
            Text(
                text = "正在录入：毛重",
                color = Tokens.Color.Text.secondary,
                style = MaterialTheme.typography.labelLarge,
            )
            NumPad(
                onDigit = viewModel::onGrossDigit,
                onBackspace = viewModel::onGrossBackspace,
            )
            BigButton(
                text = "提交过磅",
                onClick = viewModel::submit,
                enabled = viewModel.canSubmit && state !is WeighUiState.Submitting,
            )
            Text(
                text = "提交后进入「建袋打码」；袋数默认按净重折算，可改",
                color = Tokens.Color.Text.tertiary,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Preview(name = "w360", widthDp = 360, showBackground = true)
@Preview(name = "w390", widthDp = 390, showBackground = true)
@Preview(name = "w411", widthDp = 411, showBackground = true)
@Composable
private fun WeighStaticPreview() {
    JxPreviewTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(Tokens.Space.space4),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.space3),
        ) {
            FieldCard("皮重", "8.62", hint = "历史 8.62 t · 可改")
            FieldCard("毛重（必填）", "32.18", valueColor = Tokens.Color.Brand.primary)
            FieldCard("净重（自动）", "23.56", valueColor = Tokens.Color.Status.success)
        }
    }
}
