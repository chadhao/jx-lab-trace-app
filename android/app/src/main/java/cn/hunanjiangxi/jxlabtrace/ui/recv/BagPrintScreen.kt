package cn.hunanjiangxi.jxlabtrace.ui.recv

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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import cn.hunanjiangxi.jxlabtrace.print.PrintHelper
import cn.hunanjiangxi.jxlabtrace.print.PrinterGateCard
import cn.hunanjiangxi.jxlabtrace.print.PrinterUiState
import cn.hunanjiangxi.jxlabtrace.recv.BagPrintViewModel
import cn.hunanjiangxi.jxlabtrace.recv.BagUiState
import cn.hunanjiangxi.jxlabtrace.recv.RecvBag
import cn.hunanjiangxi.jxlabtrace.recv.ReprintForm
import cn.hunanjiangxi.jxlabtrace.ui.components.BigButton
import cn.hunanjiangxi.jxlabtrace.ui.components.BigButtonStyle
import cn.hunanjiangxi.jxlabtrace.ui.components.FieldCard
import cn.hunanjiangxi.jxlabtrace.ui.components.JxPreviewTheme
import cn.hunanjiangxi.jxlabtrace.ui.components.NumPad
import cn.hunanjiangxi.jxlabtrace.ui.components.PillTone
import cn.hunanjiangxi.jxlabtrace.ui.components.StatusPill
import cn.hunanjiangxi.jxlabtrace.ui.components.TopBar
import cn.hunanjiangxi.jxlabtrace.ui.theme.Tokens

/**
 * S06 建袋打码（M05/M09）：袋数折算可改 → 建袋（幂等）→ 连打队列；补打须原因。
 * 打印出口：蓝牙已连 ⇒ TSPL；未连 ⇒ 系统打印（B3-a）或「去连接」（M9-3）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BagPrintScreen(
    viewModel: BagPrintViewModel,
    printerState: PrinterUiState,
    onGoPrinter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val countInput by viewModel.bagCountInput.collectAsState()
    val context = LocalContext.current
    var reprintBag by remember { mutableStateOf<RecvBag?>(null) }
    var reprintReason by remember { mutableStateOf<String?>(null) }
    var reprintDetail by remember { mutableStateOf("") }
    var showReprintSheet by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize()) {
        TopBar(title = "建袋打码")
        PrinterGateCard(
            state = printerState,
            onGoSettings = onGoPrinter,
            onSelfTest = {},
            modifier = Modifier.padding(Tokens.Space.space4),
        )
        when (val s = state) {
            is BagUiState.Error -> Text(
                text = s.message,
                color = Tokens.Color.Status.danger,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(Tokens.Space.space4),
            )

            is BagUiState.Ready -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(Tokens.Space.space4),
                verticalArrangement = Arrangement.spacedBy(Tokens.Space.space3),
            ) {
                FieldCard(
                    label = "袋数（按净重 ÷0.6t 折算，可改）",
                    value = countInput.ifBlank { "0" },
                )
                NumPad(
                    onDigit = viewModel::onCountDigit,
                    onBackspace = viewModel::onCountBackspace,
                )
                BigButton(
                    text = "建袋",
                    onClick = viewModel::generateBags,
                    enabled = viewModel.desiredCount > 0,
                )
            }

            is BagUiState.Built -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(Tokens.Space.space4),
                verticalArrangement = Arrangement.spacedBy(Tokens.Space.space3),
            ) {
                FieldCard(
                    label = "已建袋",
                    value = "${s.bags.size} 袋",
                    hint = "序号 001–${s.bags.size.toString().padStart(3, '0')} 连号",
                    valueColor = Tokens.Color.Status.success,
                )
                BigButton(text = "全部连打", onClick = viewModel::startPrintQueue)
                BigButton(
                    text = "系统打印一张样张",
                    onClick = {
                        s.bags.firstOrNull()?.let { bag ->
                            PrintHelper.printBitmapViaSystem(context, viewModel.labelBitsFor(bag))
                        }
                    },
                    style = BigButtonStyle.Quiet,
                )
                Text("补打（须原因）", style = MaterialTheme.typography.labelLarge)
                LazyColumn {
                    items(s.bags, key = { it.id }) { bag ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "第 ${bag.bag_seq} 袋",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            TextButton(onClick = {
                                reprintBag = bag
                                showReprintSheet = true
                            }) {
                                Text("补打")
                            }
                        }
                    }
                }
            }

            is BagUiState.Printing -> Column(
                modifier = Modifier.padding(Tokens.Space.space6),
                verticalArrangement = Arrangement.spacedBy(Tokens.Space.space3),
            ) {
                Text(
                    text = "正在打印 ${s.done + 1} / ${s.total}",
                    style = MaterialTheme.typography.headlineSmall,
                )
                StatusPill(text = "进行中", tone = PillTone.Info)
                Text(text = s.currentCode, style = MaterialTheme.typography.bodyMedium)
            }

            is BagUiState.PrintDone -> Column(
                modifier = Modifier.padding(Tokens.Space.space4),
                verticalArrangement = Arrangement.spacedBy(Tokens.Space.space3),
            ) {
                StatusPill(text = "已出 ${s.printed} 张", tone = PillTone.Success)
                if (s.failures.isNotEmpty()) {
                    Text(
                        text = "异常 ${s.failures.size} 张",
                        color = Tokens.Color.Status.danger,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    s.failures.forEach { f ->
                        Text(
                            text = "第 ${f.bagSeq} 袋 · ${f.reason}",
                            color = Tokens.Color.Text.secondary,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
        }
    }

    if (showReprintSheet) {
        Column(
            modifier = Modifier
                .padding(Tokens.Space.space4)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.space2),
        ) {
            Text(
                text = "补打第 ${reprintBag?.bag_seq ?: "-"} 袋 · 须填原因",
                style = MaterialTheme.typography.titleSmall,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.space2)) {
                ReprintForm.REASONS.forEach { reason ->
                    FilterChip(
                        selected = reprintReason == reason,
                        onClick = { reprintReason = reason },
                        label = { Text(reason) },
                    )
                }
            }
            androidx.compose.material3.OutlinedTextField(
                value = reprintDetail,
                onValueChange = { reprintDetail = it },
                label = { Text("原因说明（必填）") },
                modifier = Modifier.fillMaxWidth(),
            )
            val valid = ReprintForm.isValid(reprintReason, reprintDetail)
            BigButton(
                text = "确认补打",
                enabled = valid,
                onClick = {
                    reprintBag?.let {
                        viewModel.reprint(it.bag_seq, reprintReason, reprintDetail)
                    }
                    showReprintSheet = false
                    reprintBag = null
                    reprintReason = null
                    reprintDetail = ""
                },
            )
            BigButton(
                text = "取消",
                style = BigButtonStyle.Quiet,
                onClick = {
                    showReprintSheet = false
                    reprintBag = null
                    reprintReason = null
                    reprintDetail = ""
                },
            )
        }
    }
}

@Preview(name = "w360", widthDp = 360, showBackground = true)
@Preview(name = "w390", widthDp = 390, showBackground = true)
@Preview(name = "w411", widthDp = 411, showBackground = true)
@Composable
private fun BagPrintPreview() {
    JxPreviewTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(Tokens.Space.space4),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.space3),
        ) {
            PrinterGateCard(state = PrinterUiState.Disconnected, onGoSettings = {}, onSelfTest = {})
            FieldCard("袋数", "40", hint = "按净重 23.56 t ÷ 0.6 t 折算，可改")
        }
    }
}
