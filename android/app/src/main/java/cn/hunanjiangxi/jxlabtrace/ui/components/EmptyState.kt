package cn.hunanjiangxi.jxlabtrace.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import cn.hunanjiangxi.jxlabtrace.ui.icons.JxIcons
import cn.hunanjiangxi.jxlabtrace.ui.theme.Tokens

/**
 * EmptyState：空态（图标 + 标题 + 提示 + 可选下一步动作）。
 */
@Composable
fun EmptyState(
    title: String,
    hint: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = JxIcons.Empty,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(Tokens.Space.space6),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Tokens.Space.space3),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Tokens.Color.Text.disabled,
            modifier = Modifier.size(Tokens.Size.iconLg * 2),
        )
        Text(
            text = title,
            color = Tokens.Color.Text.primary,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = hint,
            color = Tokens.Color.Text.secondary,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        if (actionText != null && onAction != null) {
            BigButton(
                text = actionText,
                onClick = onAction,
                style = BigButtonStyle.Quiet,
                modifier = Modifier.padding(top = Tokens.Space.space2),
            )
        }
    }
}

@Preview(name = "w360", widthDp = 360, showBackground = true)
@Preview(name = "w390", widthDp = 390, showBackground = true)
@Preview(name = "w411", widthDp = 411, showBackground = true)
@Composable
private fun EmptyStatePreview() {
    JxPreviewTheme {
        EmptyState(
            title = "今日还没有车次",
            hint = "车辆到磅房后，点「到货确认」开始收货。",
            actionText = "到货确认",
            onAction = {},
        )
    }
}
