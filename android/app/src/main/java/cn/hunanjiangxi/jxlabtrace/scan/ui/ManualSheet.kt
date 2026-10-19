package cn.hunanjiangxi.jxlabtrace.scan.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import cn.hunanjiangxi.jxlabtrace.scan.ManualEntry
import cn.hunanjiangxi.jxlabtrace.ui.components.BigButton
import cn.hunanjiangxi.jxlabtrace.ui.components.JxPreviewTheme
import cn.hunanjiangxi.jxlabtrace.ui.components.NumPad
import cn.hunanjiangxi.jxlabtrace.ui.theme.Tokens

/** 手输的父对象选项（宿主提供：车次/生产批/成品批…）。 */
data class ManualParent(val label: String, val code: String)

/**
 * S03 ④ 手输 sheet：**先选父对象 → 输本层序号**，校验位系统计算回显全码。
 * 不提供 27 位裸串输入框（spec `code_faces.manual_entry` / D07 / M4-4）。
 *
 * @param childType 本层对象类型（B/D/E）
 * @param parents 可选父对象（宿主业务给出，含各自的 27 位码）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualSheet(
    childType: Char,
    parents: List<ManualParent>,
    onGenerated: (fullCode: String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedIndex by remember {
        mutableStateOf(parents.indices.firstOrNull { parents[it].code.isNotBlank() })
    }
    var seqDigits by remember { mutableStateOf("") }

    val parent = selectedIndex?.let { parents.getOrNull(it) }
    val seqValue = seqDigits.toIntOrNull() ?: 0
    val assembled = parent?.let { ManualEntry.assemble(childType, it.code, seqValue) }
    val previewCode = (assembled as? ManualEntry.Result.Ok)?.full
    val previewHuman = (assembled as? ManualEntry.Result.Ok)?.human
    val assembledError = (assembled as? ManualEntry.Result.Rejected)?.reason

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        containerColor = Tokens.Color.Surface.card,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Tokens.Space.space4)
                .padding(bottom = Tokens.Space.space6),
        ) {
            Text(
                text = "第 1 步 · 选父对象（本层＝${seqLabelOf(childType)}）",
                color = Tokens.Color.Text.secondary,
                style = MaterialTheme.typography.labelLarge,
            )
            Row(
                modifier = Modifier
                    .padding(top = Tokens.Space.space3)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Tokens.Space.space2),
            ) {
                parents.forEachIndexed { index, item ->
                    val selected = index == selectedIndex
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(Tokens.Radius.md))
                            .background(
                                if (selected) Tokens.Color.Brand.primarySoft
                                else Tokens.Color.Surface.sunken,
                            )
                            .clickable { selectedIndex = index }
                            .padding(Tokens.Space.space3),
                    ) {
                        Text(
                            text = item.label,
                            color = Tokens.Color.Text.primary,
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            text = item.code.takeLast(9),
                            color = Tokens.Color.Text.tertiary,
                            style = MaterialTheme.typography.labelSmall,
                        )
                        Text(
                            text = if (selected) "已选择" else "点选",
                            color = if (selected) Tokens.Color.Brand.primary else Tokens.Color.Text.disabled,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }

            Text(
                text = "第 2 步 · 输${seqLabelOf(childType)}（3 位，系统算校验位）",
                color = Tokens.Color.Text.secondary,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = Tokens.Space.space5),
            )
            Row(
                modifier = Modifier
                    .padding(top = Tokens.Space.space2)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Tokens.Radius.md))
                    .background(Tokens.Color.Surface.sunken)
                    .padding(Tokens.Space.space3),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = seqLabelOf(childType),
                    color = Tokens.Color.Text.secondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = seqDigits.padStart(3, '0'),
                    color = if (seqDigits.isEmpty()) Tokens.Color.Text.disabled else Tokens.Color.Text.primary,
                    style = MaterialTheme.typography.headlineSmall,
                )
            }

            // 校验位自动计算回显全码（本地计算，不发请求）
            Text(
                text = when {
                    previewHuman != null -> "校验位自动计算：$previewHuman ✓"
                    assembledError != null -> assembledError
                    else -> "校验位自动计算：选父对象并输入序号后显示全码"
                },
                color = if (previewHuman != null) Tokens.Color.Status.success else Tokens.Color.Text.tertiary,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = Tokens.Space.space3),
            )

            NumPad(
                onDigit = { d -> if (seqDigits.length < 3) seqDigits += d },
                onBackspace = { seqDigits = seqDigits.dropLast(1) },
                allowDecimal = false,
                modifier = Modifier.padding(top = Tokens.Space.space3),
            )

            Row(
                modifier = Modifier
                    .padding(top = Tokens.Space.space4)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Tokens.Space.space3),
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = Tokens.Size.primaryButton),
                    shape = RoundedCornerShape(Tokens.Radius.md),
                ) {
                    Text("取消")
                }
                BigButton(
                    text = "生成并继续",
                    onClick = { previewCode?.let(onGenerated) },
                    enabled = previewCode != null,
                    modifier = Modifier.weight(1.4f),
                )
            }
        }
    }
}

/** 手输本层序号的中文名（对齐设计稿「袋序」等）。 */
fun seqLabelOf(childType: Char): String = when (childType) {
    'B' -> "袋序"
    'D' -> "成品批序"
    'E' -> "袋序"
    else -> "序号"
}

@Preview(name = "w360", widthDp = 360, showBackground = true)
@Preview(name = "w390", widthDp = 390, showBackground = true)
@Preview(name = "w411", widthDp = 411, showBackground = true)
@Composable
private fun ManualSheetPreview() {
    JxPreviewTheme {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Tokens.Color.Surface.card)
                .padding(Tokens.Space.space4),
        ) {
            Column {
                Text(
                    text = "第 1 步 · 选父对象（本层＝袋序）",
                    color = Tokens.Color.Text.secondary,
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    text = "校验位自动计算：1B-CG-0001-0002-261007-03-007-000-7 ✓",
                    color = Tokens.Color.Status.success,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = Tokens.Space.space3),
                )
            }
        }
    }
}
