package cn.hunanjiangxi.jxlabtrace.scan.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cn.hunanjiangxi.jxlabtrace.scan.ScanFailure
import cn.hunanjiangxi.jxlabtrace.scan.ScanField
import cn.hunanjiangxi.jxlabtrace.scan.ScanInfo
import cn.hunanjiangxi.jxlabtrace.scan.display
import cn.hunanjiangxi.jxlabtrace.ui.components.BigButton
import cn.hunanjiangxi.jxlabtrace.ui.components.BigButtonStyle
import cn.hunanjiangxi.jxlabtrace.ui.components.JxPreviewTheme
import cn.hunanjiangxi.jxlabtrace.ui.icons.JxIcons
import cn.hunanjiangxi.jxlabtrace.ui.theme.Tokens

private val sheetShape = RoundedCornerShape(Tokens.Radius.lg)
private val darkShape = RoundedCornerShape(Tokens.Radius.md)

/**
 * S03 ② 识别成功 · 确认卡：对象类型 + 人读码 + 四字段 + 主按钮（文案随宿主业务 = [confirmLabel]）。
 * 识别 ≠ 提交：二次确认后宿主才执行业务写。
 */
@Composable
fun ConfirmCard(
    info: ScanInfo,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onRescan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(sheetShape)
            .background(Tokens.Color.Surface.card)
            .padding(Tokens.Space.space4),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(Tokens.Radius.sm))
                    .background(Tokens.Color.Status.successSoft)
                    .padding(Tokens.Space.space2),
            ) {
                Icon(
                    imageVector = JxIcons.Success,
                    contentDescription = null,
                    tint = Tokens.Color.Status.success,
                )
            }
            Column(Modifier.padding(start = Tokens.Space.space3)) {
                Text(
                    text = "已识别 · ${info.typeLabel}",
                    color = Tokens.Color.Text.primary,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = info.fields.firstOrNull()?.let { "${it.label} ${it.value}" }.orEmpty(),
                    color = Tokens.Color.Text.secondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        Text(
            text = info.human,
            color = Tokens.Color.Text.primary,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier
                .padding(top = Tokens.Space.space3)
                .fillMaxWidth()
                .clip(darkShape)
                .background(Tokens.Color.Surface.sunken)
                .padding(Tokens.Space.space3),
        )
        // 四字段（S03 ② 只留四个关键字段）
        Column(
            modifier = Modifier.padding(top = Tokens.Space.space3),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.space2),
        ) {
            info.fields.forEach { field -> FieldRow(field) }
        }
        Row(
            modifier = Modifier.padding(top = Tokens.Space.space4),
            horizontalArrangement = Arrangement.spacedBy(Tokens.Space.space3),
        ) {
            BigButton(
                text = "重扫",
                onClick = onRescan,
                style = BigButtonStyle.Quiet,
                modifier = Modifier.weight(1f),
            )
            BigButton(
                text = confirmLabel,
                onClick = onConfirm,
                modifier = Modifier.weight(1.4f),
            )
        }
        Text(
            text = "确认后记录不可修改；连续扫下一单时本卡自动关闭",
            color = Tokens.Color.Text.tertiary,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier
                .padding(top = Tokens.Space.space3)
                .align(Alignment.CenterHorizontally),
        )
    }
}

@Composable
private fun FieldRow(field: ScanField) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = field.label,
            color = Tokens.Color.Text.secondary,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = field.value.ifEmpty { "—" },
            color = Tokens.Color.Text.primary,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/**
 * S03 ③ 失败条（五类 Failure 各自文案，禁止合并成「扫码失败」）；
 * 红边取景由 [ScanContent] 的 finder 负责，这里给原因 + 出路 + 排查小字。
 */
@Composable
fun ErrorBand(
    reason: ScanFailure,
    onRescan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val display = reason.display()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(sheetShape)
            .background(Tokens.Color.Status.dangerSoft)
            .border(1.dp, Tokens.Color.Status.danger, sheetShape)
            .padding(Tokens.Space.space4),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = JxIcons.Exception,
                contentDescription = null,
                tint = Tokens.Color.Status.danger,
            )
            Text(
                text = display.title,
                color = Tokens.Color.Status.danger,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = Tokens.Space.space2),
            )
        }
        Text(
            text = display.detail,
            color = Tokens.Color.Text.primary,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = Tokens.Space.space2),
        )
        Text(
            text = display.codeLine,
            color = Tokens.Color.Text.tertiary,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = Tokens.Space.space2),
        )
        BigButton(
            text = "返回取景重扫",
            onClick = onRescan,
            modifier = Modifier.padding(top = Tokens.Space.space3),
        )
    }
}

/** 宿主提交完成（Done 态）：给「继续扫码」回取景（连续扫）。 */
@Composable
fun DoneCard(
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(sheetShape)
            .background(Tokens.Color.Surface.card)
            .padding(Tokens.Space.space4),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = JxIcons.Success,
                contentDescription = null,
                tint = Tokens.Color.Status.success,
            )
            Text(
                text = "已提交",
                color = Tokens.Color.Status.success,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = Tokens.Space.space2),
            )
        }
        BigButton(
            text = "继续扫码",
            onClick = onContinue,
            modifier = Modifier.padding(top = Tokens.Space.space3),
        )
    }
}

private val previewInfo = ScanInfo(
    kind = "bag",
    code = "1BCG00010002261007030070007",
    human = "1B-CG-0001-0002-261007-03-007-000-7",
    typeLabel = "原料吨袋",
    fields = listOf(
        ScanField("车牌", "湘F·A1234"),
        ScanField("物料", "石油焦（硫<1%）"),
        ScanField("袋序", "7"),
        ScanField("状态", "待投料"),
    ),
    voided = false,
)

@Preview(name = "w360", widthDp = 360, showBackground = true)
@Preview(name = "w390", widthDp = 390, showBackground = true)
@Preview(name = "w411", widthDp = 411, showBackground = true)
@Composable
private fun ConfirmCardPreview() {
    JxPreviewTheme {
        ConfirmCard(info = previewInfo, confirmLabel = "确认投料", onConfirm = {}, onRescan = {})
    }
}

@Preview(name = "w360", widthDp = 360, showBackground = true)
@Preview(name = "w390", widthDp = 390, showBackground = true)
@Preview(name = "w411", widthDp = 411, showBackground = true)
@Composable
private fun ErrorBandPreview() {
    JxPreviewTheme {
        ErrorBand(
            reason = ScanFailure.Rejected(code = "ALREADY_FED", message = "该袋已于 09:41 投入批 2410-007。"),
            onRescan = {},
        )
    }
}

@Preview(name = "w390", widthDp = 390, showBackground = true)
@Composable
private fun DoneCardPreview() {
    JxPreviewTheme {
        DoneCard(onContinue = {})
    }
}
