package cn.hunanjiangxi.jxlabtrace.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import cn.hunanjiangxi.jxlabtrace.ui.icons.JxIcons
import cn.hunanjiangxi.jxlabtrace.ui.theme.Tokens

/**
 * ScanHero：追溯页首屏大扫码入口，高 ≥ [Tokens.Size.traceScanHero]（120dp）。
 */
@Composable
fun ScanHero(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector = JxIcons.Scan,
    containerColor: androidx.compose.ui.graphics.Color = Tokens.Color.Domain.Trace.base,
    contentColor: androidx.compose.ui.graphics.Color = Tokens.Color.Domain.Trace.on,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(Tokens.Size.traceScanHero)
            .testTag("scan_hero")
            .clip(RoundedCornerShape(Tokens.Radius.lg))
            .background(containerColor)
            .clickable(onClick = onClick)
            .padding(Tokens.Space.space5),
        contentAlignment = Alignment.CenterStart,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.space2),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(Tokens.Size.iconLg),
            )
            Text(
                text = title,
                color = contentColor,
                style = MaterialTheme.typography.headlineMedium,
                maxLines = 1,
            )
            Text(
                text = subtitle,
                color = contentColor.copy(alpha = 0.84f),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 2,
            )
        }
    }
}

@Preview(name = "w360", widthDp = 360, showBackground = true)
@Preview(name = "w390", widthDp = 390, showBackground = true)
@Preview(name = "w411", widthDp = 411, showBackground = true)
@Composable
private fun ScanHeroPreview() {
    JxPreviewTheme {
        ScanHero(
            title = "扫一扫查追溯",
            subtitle = "扫袋码 / 车码 / 批号，一屏看完谱系",
            onClick = {},
            modifier = Modifier.padding(Tokens.Space.space4),
        )
    }
}
