package cn.hunanjiangxi.jxlabtrace.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import cn.hunanjiangxi.jxlabtrace.ui.icons.JxIcons
import cn.hunanjiangxi.jxlabtrace.ui.theme.Tokens

/**
 * TopBar：顶栏，高 [Tokens.Size.topbar]（56dp）。返回键触控区 56×56。
 */
@Composable
fun TopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(Tokens.Size.topbar)
            .background(Tokens.Color.Surface.card)
            .padding(horizontal = Tokens.Space.space1)
            .testTag("topbar"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            Box(
                modifier = Modifier
                    .size(Tokens.Size.topbar)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = JxIcons.Back,
                    contentDescription = "返回",
                    tint = Tokens.Color.Text.primary,
                )
            }
        } else {
            androidx.compose.foundation.layout.Spacer(
                modifier = Modifier
                    .padding(horizontal = Tokens.Space.space3)
                    .size(Tokens.Space.space1),
            )
        }
        Text(
            text = title,
            color = Tokens.Color.Text.primary,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = Tokens.Space.space2),
        )
        actions()
    }
}

@Preview(name = "w360", widthDp = 360, showBackground = true)
@Preview(name = "w390", widthDp = 390, showBackground = true)
@Preview(name = "w411", widthDp = 411, showBackground = true)
@Composable
private fun TopBarPreview() {
    JxPreviewTheme {
        TopBar(
            title = "到货确认",
            onBack = {},
            actions = {
                Box(
                    modifier = Modifier
                        .size(Tokens.Size.topbar)
                        .background(Tokens.Color.Surface.card),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "刷新",
                        color = Tokens.Color.Brand.primary,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            },
        )
    }
}
