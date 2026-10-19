package cn.hunanjiangxi.jxlabtrace.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import cn.hunanjiangxi.jxlabtrace.ui.icons.JxIcons
import cn.hunanjiangxi.jxlabtrace.ui.theme.Tokens

/**
 * NumPad：大数字键盘（过磅/取样量用）。键位高 ≥ [Tokens.Size.touchComfort]（64dp）。
 * 每个键带 testTag（`numpad_key_<id>`），供尺寸断言单测使用。
 */
@Composable
fun NumPad(
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    modifier: Modifier = Modifier,
    allowDecimal: Boolean = true,
    onConfirm: (() -> Unit)? = null,
) {
    val keyHeight = Tokens.Size.touchComfort
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Tokens.Space.space2),
    ) {
        listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf(if (allowDecimal) "." else "", "0", "<"),
        ).forEach { rowKeys ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Tokens.Space.space2),
            ) {
                rowKeys.forEach { key ->
                    NumPadKey(
                        label = key,
                        onClick = {
                            when (key) {
                                "<" -> onBackspace()
                                "" -> Unit
                                else -> onDigit(key)
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(keyHeight),
                    )
                }
            }
        }
        if (onConfirm != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Tokens.Size.primaryButton)
                    .testTag("numpad_confirm")
                    .clip(RoundedCornerShape(Tokens.Radius.md))
                    .background(Tokens.Color.Brand.primary)
                    .clickable(onClick = onConfirm),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "确认",
                    color = Tokens.Color.Brand.onPrimary,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

@Composable
private fun NumPadKey(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isBackspace = label == "<"
    val display = if (isBackspace) "⌫" else label
    Box(
        modifier = modifier
            .heightIn(min = Tokens.Size.touchComfort)
            .clip(RoundedCornerShape(Tokens.Radius.md))
            .background(Tokens.Color.Surface.card)
            .clickable(onClick = onClick)
            .testTag("numpad_key_${if (isBackspace) "backspace" else label.ifEmpty { "blank" }}"),
        contentAlignment = Alignment.Center,
    ) {
        if (isBackspace) {
            androidx.compose.material3.Icon(
                imageVector = JxIcons.Backspace,
                contentDescription = "退格",
                tint = Tokens.Color.Text.secondary,
            )
        } else {
            Text(
                text = display,
                color = if (label.isEmpty()) Tokens.Color.Text.disabled else Tokens.Color.Text.primary,
                style = MaterialTheme.typography.headlineMedium,
            )
        }
    }
}

@Preview(name = "w360", widthDp = 360, showBackground = true)
@Preview(name = "w390", widthDp = 390, showBackground = true)
@Preview(name = "w411", widthDp = 411, showBackground = true)
@Composable
private fun NumPadPreview() {
    JxPreviewTheme {
        NumPad(
            onDigit = {},
            onBackspace = {},
            onConfirm = {},
            modifier = Modifier.padding(Tokens.Space.space3),
        )
    }
}
