package cn.hunanjiangxi.jxlabtrace.ui.prod

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import cn.hunanjiangxi.jxlabtrace.print.PrinterGateCard
import cn.hunanjiangxi.jxlabtrace.print.PrinterUiState
import cn.hunanjiangxi.jxlabtrace.prod.FgUiState
import cn.hunanjiangxi.jxlabtrace.prod.FgPrintViewModel
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
 * S11 生产 · 成品袋打码（M7-6）：成品批自动 → 袋数折算可改 →
 * 连打**复用 M09 队列与打印通道**（PrinterGateCard + printOne）；谱系后端挂接。
 */
@Composable
fun FgPrintScreen(
    viewModel: FgPrintViewModel,
    printerState: PrinterUiState,
    onGoPrinter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val message by viewModel.message.collectAsState()
    var countInput by remember { mutableStateOf<String?>(null) }
    var editingCount by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize()) {
        TopBar(title = "成品袋打码")
        PrinterGateCard(
            state = printerState,
            onGoSettings = onGoPrinter,
            onSelfTest = {},
            modifier = Modifier.padding(Tokens.Space.space4),
        )
        when (val s = state) {
            is FgUiState.Error -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(Tokens.Space.space4),
                verticalArrangement = Arrangement.spacedBy(Tokens.Space.space3),
            ) {
                EmptyState(title = "成品打码加载失败", hint = s.message)
                BigButton(text = "重试", onClick = viewModel::load, style = BigButtonStyle.Quiet)
            }

            is FgUiState.Preparing -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(Tokens.Space.space4),
                verticalArrangement = Arrangement.spacedBy(Tokens.Space.space3),
            ) {
                FieldCard(
                    label = "成品批号（系统生成）",
                    value = "建批后生成",
                    hint = "不可手输",
                    valueColor = Tokens.Color.Text.tertiary,
                )
                val display = countInput ?: s.defaultCount.takeIf { it > 0 }?.toString().orEmpty()
                FieldCard(
                    label = "成品袋数（折算 ${s.defaultCount} · 可改）",
                    value = display.ifBlank { "0" },
                    hint = "袋",
                )
                if (editingCount) {
                    NumPad(
                        onDigit = { d ->
                            if (d != ".") {
                                val base = countInput ?: ""
                                countInput = (base + d).filter { c -> c.isDigit() }.take(3)
                            }
                        },
                        onBackspace = { countInput = (countInput ?: display).dropLast(1) },
                        allowDecimal = false,
                        onConfirm = { editingCount = false },
                    )
                } else {
                    BigButton(text = "修改袋数", onClick = { editingCount = true }, style = BigButtonStyle.Quiet)
                }
                val errorMsg = message
                if (errorMsg != null) {
                    Text(
                        text = errorMsg,
                        color = Tokens.Color.Status.danger,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                val count = display.toIntOrNull() ?: 0
                BigButton(
                    text = "建成品批",
                    onClick = { viewModel.createLot(count, netWeightTons = null) },
                    enabled = count > 0,
                )
            }

            is FgUiState.LotCreated -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(Tokens.Space.space4),
                verticalArrangement = Arrangement.spacedBy(Tokens.Space.space3),
            ) {
                FieldCard(
                    label = "成品批号（系统生成）",
                    value = s.lot.human.ifBlank { s.lot.code },
                    valueColor = Tokens.Color.Brand.primary,
                )
                FieldCard(
                    label = "袋数",
                    value = "${s.lot.qty_bag} 袋",
                    hint = "打完自动入账并挂谱系",
                )
                val errorMsg = message
                if (errorMsg != null) {
                    Text(
                        text = errorMsg,
                        color = Tokens.Color.Status.danger,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                BigButton(
                    text = "生成袋码并开始连打",
                    onClick = { viewModel.generateAndPrint(s.lot.qty_bag) },
                )
            }

            is FgUiState.BagsReady -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(Tokens.Space.space4),
                verticalArrangement = Arrangement.spacedBy(Tokens.Space.space3),
            ) {
                FieldCard(label = "已生成", value = "${s.bags.size} 袋", hint = "连打队列就绪")
                BigButton(text = "全部连打", onClick = viewModel::startPrintQueue)
            }

            is FgUiState.Printing -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(Tokens.Space.space6),
                verticalArrangement = Arrangement.spacedBy(Tokens.Space.space3),
            ) {
                Text(
                    text = "正在打印 ${s.done + 1} / ${s.total}",
                    style = MaterialTheme.typography.headlineSmall,
                )
                StatusPill(text = "进行中", tone = PillTone.Info)
                Text(text = s.currentCode, style = MaterialTheme.typography.bodyMedium)
            }

            is FgUiState.PrintDone -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(Tokens.Space.space4),
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
                            text = f,
                            color = Tokens.Color.Text.secondary,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
        }
    }
}

@Preview(name = "w360", widthDp = 360, showBackground = true)
@Preview(name = "w390", widthDp = 390, showBackground = true)
@Preview(name = "w411", widthDp = 411, showBackground = true)
@Composable
private fun FgPrintPreview() {
    JxPreviewTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(Tokens.Space.space4),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.space3),
        ) {
            PrinterGateCard(state = PrinterUiState.Disconnected, onGoSettings = {}, onSelfTest = {})
            FieldCard("成品批号（系统生成）", "提交后生成", valueColor = Tokens.Color.Text.tertiary)
            FieldCard("成品袋数（折算 39 · 可改）", "39", hint = "袋")
        }
    }
}
