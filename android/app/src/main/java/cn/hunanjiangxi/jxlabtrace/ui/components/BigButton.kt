package cn.hunanjiangxi.jxlabtrace.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cn.hunanjiangxi.jxlabtrace.ui.theme.Tokens

/** 主按钮样式：主操作 / 危险 / 次要。 */
enum class BigButtonStyle { Primary, Danger, Quiet }

/**
 * BigButton：手套友好的大按钮，高恒为 [Tokens.Size.primaryButton]（60dp）。
 */
@Composable
fun BigButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: BigButtonStyle = BigButtonStyle.Primary,
) {
    val height = Tokens.Size.primaryButton
    val shape = RoundedCornerShape(Tokens.Radius.md)
    val colors = when (style) {
        BigButtonStyle.Primary -> ButtonDefaults.buttonColors(
            containerColor = Tokens.Color.Brand.primary,
            contentColor = Tokens.Color.Brand.onPrimary,
            disabledContainerColor = Tokens.Color.Status.neutralSoft,
            disabledContentColor = Tokens.Color.Text.disabled,
        )
        BigButtonStyle.Danger -> ButtonDefaults.buttonColors(
            containerColor = Tokens.Color.Status.danger,
            contentColor = Tokens.Color.Text.inverse,
            disabledContainerColor = Tokens.Color.Status.neutralSoft,
            disabledContentColor = Tokens.Color.Text.disabled,
        )
        BigButtonStyle.Quiet -> ButtonDefaults.buttonColors(
            containerColor = Tokens.Color.Brand.primarySoft,
            contentColor = Tokens.Color.Brand.primary,
            disabledContainerColor = Tokens.Color.Status.neutralSoft,
            disabledContentColor = Tokens.Color.Text.disabled,
        )
    }

    val modifierWithSize = modifier
        .fillMaxWidth()
        .height(height)

    if (style == BigButtonStyle.Quiet) {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifierWithSize,
            shape = shape,
            border = androidx.compose.foundation.BorderStroke(1.dp, Tokens.Color.Surface.borderStrong),
            contentPadding = PaddingValues(horizontal = Tokens.Space.space4),
        ) {
            Text(text = text, style = MaterialTheme.typography.titleMedium)
        }
    } else {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = modifierWithSize,
            shape = shape,
            colors = colors,
            contentPadding = PaddingValues(horizontal = Tokens.Space.space4),
        ) {
            Text(text = text, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Preview(name = "w360", widthDp = 360, showBackground = true)
@Preview(name = "w390", widthDp = 390, showBackground = true)
@Preview(name = "w411", widthDp = 411, showBackground = true)
@Composable
private fun BigButtonPreview() {
    JxPreviewTheme {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.padding(Tokens.Space.space4),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.space3),
        ) {
            BigButton("提交过磅", onClick = {})
            BigButton("作废（二次确认后）", onClick = {}, style = BigButtonStyle.Danger)
            BigButton("重试", onClick = {}, style = BigButtonStyle.Quiet)
            BigButton("网络中断 · 不可提交", onClick = {}, enabled = false)
        }
    }
}
