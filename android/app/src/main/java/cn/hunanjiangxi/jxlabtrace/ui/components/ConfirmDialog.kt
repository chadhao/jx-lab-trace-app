package cn.hunanjiangxi.jxlabtrace.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.window.Dialog
import cn.hunanjiangxi.jxlabtrace.ui.theme.Tokens

/**
 * 二次确认对话框（M05 退车 / 建袋等破坏性动作复用 · 判据 M5-4）。
 * 标题 + 说明 + 危险主按钮 + 安静取消。
 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    dismissText: String = "取消",
    danger: Boolean = true,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = modifier
                .clip(RoundedCornerShape(Tokens.Radius.lg))
                .background(Tokens.Color.Surface.card)
                .padding(Tokens.Space.space5),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.space4),
        ) {
            Text(
                text = title,
                color = Tokens.Color.Text.primary,
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = message,
                color = Tokens.Color.Text.secondary,
                style = MaterialTheme.typography.bodyMedium,
            )
            BigButton(
                text = confirmText,
                onClick = onConfirm,
                style = if (danger) BigButtonStyle.Danger else BigButtonStyle.Primary,
            )
            BigButton(
                text = dismissText,
                onClick = onDismiss,
                style = BigButtonStyle.Quiet,
            )
        }
    }
}

@Preview(name = "w360", widthDp = 360, showBackground = true)
@Preview(name = "w390", widthDp = 390, showBackground = true)
@Preview(name = "w411", widthDp = 411, showBackground = true)
@Composable
private fun ConfirmDialogPreview() {
    JxPreviewTheme {
        // Preview 只展示静态文案口径（Dialog 窗口不进截图流）
        Column(
            modifier = Modifier
                .padding(Tokens.Space.space4)
                .fillMaxWidth(),
        ) {
            Text("确认退车？", style = MaterialTheme.typography.titleLarge)
            Text(
                "退车后该车全部袋码将作废，且需质检「退货」判定在先。此操作不可撤销。",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
