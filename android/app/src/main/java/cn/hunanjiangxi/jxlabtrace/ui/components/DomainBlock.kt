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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import cn.hunanjiangxi.jxlabtrace.ui.icons.JxIcons
import cn.hunanjiangxi.jxlabtrace.ui.theme.Tokens

/**
 * DomainBlock：业务域色块入口（域色 + 图标 + 标题/副题 + 可选角标）。
 * 最小高度 ≥ [Tokens.Size.touchMin]（56dp），默认排版为首页入口卡（更高）。
 */
@Composable
fun DomainBlock(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badgeCount: Int? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Tokens.Size.touchComfort)
            .clip(RoundedCornerShape(Tokens.Radius.lg))
            .background(containerColor)
            .clickable(onClick = onClick)
            .padding(Tokens.Space.space4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Tokens.Space.space4),
    ) {
        Box(
            modifier = Modifier
                .size(Tokens.Size.entryIcon)
                .clip(RoundedCornerShape(Tokens.Radius.md))
                .background(contentColor.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.material3.Icon(
                imageVector = icon,
                contentDescription = title,
                tint = contentColor,
                modifier = Modifier.size(Tokens.Size.iconLg),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.space1),
        ) {
            Text(
                text = title,
                color = contentColor,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
            )
            Text(
                text = subtitle,
                color = contentColor.copy(alpha = 0.82f),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 2,
            )
        }
        if (badgeCount != null && badgeCount > 0) {
            Box(
                modifier = Modifier
                    .size(Tokens.Size.touchMin / 2)
                    .clip(CircleShape)
                    .background(Tokens.Color.Status.danger),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (badgeCount > 99) "99+" else badgeCount.toString(),
                    color = Tokens.Color.Text.inverse,
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
private fun DomainBlockPreview() {
    JxPreviewTheme {
        Column(
            modifier = Modifier.padding(Tokens.Space.space4),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.space3),
        ) {
            DomainBlock(
                title = "收货",
                subtitle = "到货确认 · 过磅 · 建袋打码",
                icon = JxIcons.RecvTruck,
                containerColor = Tokens.Color.Domain.Receiving.base,
                contentColor = Tokens.Color.Domain.Receiving.on,
                onClick = {},
                badgeCount = 3,
            )
            DomainBlock(
                title = "取样",
                subtitle = "扫码取样（份样 / 大样 / 保留样）",
                icon = JxIcons.Sample,
                containerColor = Tokens.Color.Domain.Qc.base,
                contentColor = Tokens.Color.Domain.Qc.on,
                onClick = {},
            )
        }
    }
}
