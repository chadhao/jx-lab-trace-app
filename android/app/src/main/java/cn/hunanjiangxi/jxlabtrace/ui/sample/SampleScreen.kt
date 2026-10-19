package cn.hunanjiangxi.jxlabtrace.ui.sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import cn.hunanjiangxi.jxlabtrace.sample.SampleForm
import cn.hunanjiangxi.jxlabtrace.sample.SampleKind
import cn.hunanjiangxi.jxlabtrace.sample.SampleTargetType
import cn.hunanjiangxi.jxlabtrace.sample.SampleTakeBody
import cn.hunanjiangxi.jxlabtrace.ui.components.BigButton
import cn.hunanjiangxi.jxlabtrace.ui.components.FieldCard
import cn.hunanjiangxi.jxlabtrace.ui.components.JxPreviewTheme
import cn.hunanjiangxi.jxlabtrace.ui.components.NumPad
import cn.hunanjiangxi.jxlabtrace.ui.components.TopBar
import cn.hunanjiangxi.jxlabtrace.ui.theme.Tokens

/**
 * S07 取样（M06 · 生产班组）：对象三选 → 扫码/手输定位（复用 M04）→
 * 类型/取样量/份样数（全默认）→ POST take。
 * 取样人＝会话账号，**UI 无编辑入口**；无留样入库入口。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SampleScreen(
    locatedCode: String,
    locatedTargetId: Long,
    targetTypes: List<SampleTargetType> = SampleTargetType.entries,
    onTake: (SampleTakeBody) -> Unit,
    onScanTarget: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var targetType by remember { mutableStateOf(SampleTargetType.TRUCK) }
    var kind by remember { mutableStateOf(SampleKind.INCREMENTAL) }
    var weightText by remember { mutableStateOf(SampleForm.DEFAULT_WEIGHT_G.toInt().toString()) }
    var samplesText by remember { mutableStateOf(SampleForm.DEFAULT_SAMPLES.toString()) }
    var editingWeight by remember { mutableStateOf(false) }

    val weight = weightText.toDoubleOrNull() ?: 0.0
    val samples = samplesText.toIntOrNull() ?: 0
    val body = SampleForm.build(
        code = locatedCode,
        targetType = targetType,
        targetId = locatedTargetId,
        weightG = weight,
        samples = samples,
    )
    val valid = SampleForm.isValid(body)

    Column(modifier = modifier.fillMaxSize()) {
        TopBar(title = "取样")
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(Tokens.Space.space4),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.space3),
        ) {
            // 对象三选
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.space2)) {
                targetTypes.forEach { type ->
                    FilterChip(
                        selected = targetType == type,
                        onClick = { targetType = type },
                        label = { Text(type.label) },
                    )
                }
            }
            BigButton(text = "扫码定位对象", onClick = onScanTarget, style = cn.hunanjiangxi.jxlabtrace.ui.components.BigButtonStyle.Quiet)
            if (locatedCode.isNotBlank()) {
                FieldCard(
                    label = "已定位（扫码/选择）",
                    value = locatedCode,
                    hint = "对象 #$locatedTargetId",
                )
            } else {
                FieldCard(label = "对象", value = "未定位 · 请扫码", hint = "扫码后自动带出")
            }

            // 取样类型
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.space2)) {
                SampleKind.entries.forEach { k ->
                    FilterChip(
                        selected = kind == k,
                        onClick = { kind = k },
                        label = { Text(k.label) },
                    )
                }
            }

            // 取样量（默认 500g）
            FieldCard(
                label = "取样量（默认 ${SampleForm.DEFAULT_WEIGHT_G.toInt()} g）",
                value = weightText,
                hint = "g",
            )
            if (editingWeight) {
                NumPad(
                    onDigit = { d -> if (d != ".") weightText = (weightText + d).filter { it.isDigit() }.take(6) },
                    onBackspace = { weightText = weightText.dropLast(1) },
                    allowDecimal = false,
                    onConfirm = { editingWeight = false },
                )
            } else {
                BigButton(
                    text = "修改取样量",
                    style = cn.hunanjiangxi.jxlabtrace.ui.components.BigButtonStyle.Quiet,
                    onClick = { editingWeight = true },
                )
            }

            // 份样数（默认按约定 5）
            OutlinedTextField(
                value = samplesText,
                onValueChange = { v -> samplesText = v.filter { it.isDigit() }.take(3) },
                label = { Text("份样数（按批约定 ${SampleForm.DEFAULT_SAMPLES}）") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Text(
                text = "取样人＝登录账号（不可改）。取样后样品交化验室：登记柜位与检测在 PC 端。",
                color = Tokens.Color.Text.tertiary,
                style = MaterialTheme.typography.labelMedium,
            )

            BigButton(
                text = "确认取样",
                onClick = { onTake(body) },
                enabled = valid,
            )
        }
    }
}

@Preview(name = "w360", widthDp = 360, showBackground = true)
@Preview(name = "w390", widthDp = 390, showBackground = true)
@Preview(name = "w411", widthDp = 411, showBackground = true)
@Composable
private fun SampleScreenPreview() {
    JxPreviewTheme {
        SampleScreen(
            locatedCode = "1BCG00010002261007030070007",
            locatedTargetId = 7,
            onTake = {},
            onScanTarget = {},
        )
    }
}
