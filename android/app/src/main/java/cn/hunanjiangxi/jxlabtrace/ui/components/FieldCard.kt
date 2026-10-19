package cn.hunanjiangxi.jxlabtrace.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cn.hunanjiangxi.jxlabtrace.ui.theme.Tokens

/**
 * FieldCard：字段卡（标签 + 值 + 可选尾注）。非交互展示件。
 */
@Composable
fun FieldCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
    valueColor: Color = Tokens.Color.Text.primary,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Tokens.Radius.md))
            .background(Tokens.Color.Surface.card)
            .border(1.dp, Tokens.Color.Surface.border, RoundedCornerShape(Tokens.Radius.md))
            .padding(Tokens.Space.space4),
        verticalArrangement = Arrangement.spacedBy(Tokens.Space.space1),
    ) {
        Text(
            text = label,
            color = Tokens.Color.Text.tertiary,
            style = MaterialTheme.typography.labelMedium,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Tokens.Space.space2),
        ) {
            Text(
                text = value,
                color = valueColor,
                style = MaterialTheme.typography.titleMedium,
            )
            if (hint != null) {
                Text(
                    text = hint,
                    color = Tokens.Color.Text.secondary,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Preview(name = "w360", widthDp = 360, showBackground = true)
@Preview(name = "w390", widthDp = 390, showBackground = true)
@Preview(name = "w411", widthDp = 411, showBackground = true)
@Composable
private fun FieldCardPreview() {
    JxPreviewTheme {
        Column(
            modifier = Modifier.padding(Tokens.Space.space4),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.space3),
        ) {
            FieldCard("车牌号", "湘F·12345", hint = "上次过磅 19:42")
            FieldCard("毛重（kg）", "42,180", valueColor = Tokens.Color.Brand.primary)
        }
    }
}
