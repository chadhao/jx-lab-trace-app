package cn.hunanjiangxi.jxlabtrace.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import cn.hunanjiangxi.jxlabtrace.ui.theme.Tokens

/**
 * NowBar：进行中条（顶部常驻「正在做的事」，一键回到作业）。
 * 右侧动作区触控高度 ≥ [Tokens.Size.touchMin]。
 */
@Composable
fun NowBar(
    title: String,
    subtitle: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Tokens.Size.touchComfort)
            .clip(RoundedCornerShape(Tokens.Radius.md))
            .background(Tokens.Color.Brand.primarySoft)
            .padding(Tokens.Space.space3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Tokens.Space.space3),
    ) {
        Box(
            modifier = Modifier
                .width(Tokens.Space.space1)
                .heightIn(min = Tokens.Size.iconLg)
                .clip(RoundedCornerShape(Tokens.Radius.pill))
                .background(Tokens.Color.Brand.primary),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.space1),
        ) {
            Text(
                text = title,
                color = Tokens.Color.Text.primary,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
            )
            Text(
                text = subtitle,
                color = Tokens.Color.Text.secondary,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 2,
            )
        }
        Box(
            modifier = Modifier
                .heightIn(min = Tokens.Size.touchMin)
                .clip(RoundedCornerShape(Tokens.Radius.sm))
                .background(Tokens.Color.Brand.primary)
                .clickable(onClick = onAction)
                .padding(horizontal = Tokens.Space.space3, vertical = Tokens.Space.space2),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = actionLabel,
                color = Tokens.Color.Brand.onPrimary,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Preview(name = "w360", widthDp = 360, showBackground = true)
@Preview(name = "w390", widthDp = 390, showBackground = true)
@Preview(name = "w411", widthDp = 411, showBackground = true)
@Composable
private fun NowBarPreview() {
    JxPreviewTheme {
        NowBar(
            title = "进行中：车次 C20261019-03 过磅",
            subtitle = "湘F·12345 · 毛重待录入 · 已等待 6 分钟",
            actionLabel = "继续",
            onAction = {},
            modifier = Modifier.padding(Tokens.Space.space4),
        )
    }
}
