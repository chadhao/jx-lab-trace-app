package cn.hunanjiangxi.jxlabtrace.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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

/** TabBar 单项（图标 + 文字，颜色索引 + 文字标签双编码）。 */
data class TabBarItem(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val tint: androidx.compose.ui.graphics.Color,
)

/**
 * TabBar：底部平级导航，3/4 项角色自适应（由调用方传入条目）。
 * **无扫码键**（D25：无全局扫码键，相机由业务页内嵌唤起）。
 * 高 [Tokens.Size.tabbar]（64dp），每项触控高 ≥56dp。
 */
@Composable
fun TabBar(
    items: List<TabBarItem>,
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(Tokens.Size.tabbar)
            .background(Tokens.Color.Surface.card)
            .testTag("tabbar"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { item ->
            val selected = item.id == selectedId
            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(Tokens.Size.tabbar)
                    .clickable { onSelect(item.id) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    modifier = Modifier
                        .padding(bottom = Tokens.Space.space1)
                        .size(Tokens.Size.touchMin * 0.64f)
                        .clip(RoundedCornerShape(Tokens.Radius.sm))
                        .background(
                            if (selected) item.tint.copy(alpha = 0.14f)
                            else androidx.compose.ui.graphics.Color.Transparent
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label,
                        tint = if (selected) item.tint else Tokens.Color.Text.tertiary,
                        modifier = Modifier.size(Tokens.Size.iconMd),
                    )
                }
                Text(
                    text = item.label,
                    color = if (selected) item.tint else Tokens.Color.Text.tertiary,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                )
            }
        }
    }
}

@Preview(name = "w360", widthDp = 360, showBackground = true)
@Preview(name = "w390", widthDp = 390, showBackground = true)
@Preview(name = "w411", widthDp = 411, showBackground = true)
@Composable
private fun TabBarReceiverPreview() {
    JxPreviewTheme {
        TabBar(
            items = listOf(
                TabBarItem("home", "首页", JxIcons.Home, Tokens.Color.Brand.primary),
                TabBarItem("scan", "扫码记录", JxIcons.Scan, Tokens.Color.Domain.Receiving.base),
                TabBarItem("trace", "追溯", JxIcons.TraceForward, Tokens.Color.Domain.Trace.base),
                TabBarItem("me", "我的", JxIcons.Me, Tokens.Color.Brand.primary),
            ),
            selectedId = "home",
            onSelect = {},
        )
    }
}
