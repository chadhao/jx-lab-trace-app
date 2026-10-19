package cn.hunanjiangxi.jxlabtrace.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import cn.hunanjiangxi.jxlabtrace.ui.icons.JxIcons
import cn.hunanjiangxi.jxlabtrace.ui.theme.Tokens
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * M12 全局断网页：订阅 M01 `NetworkMonitor.online` StateFlow，
 * 离线时置顶红条「网络中断 · 作业提交已暂停」+ 重试按钮（高 ≥ touchMin，手套可点）。
 */
@Composable
fun GlobalOfflineBar(
    online: StateFlow<Boolean>,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isConnected by online.collectAsState()
    AnimatedVisibility(
        visible = !isConnected,
        enter = fadeIn() + slideInVertically { -it },
        exit = fadeOut() + slideOutVertically { -it },
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(Tokens.Size.touchMin)
                .background(Tokens.Color.Status.danger)
                .padding(horizontal = Tokens.Space.space3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = JxIcons.NetworkOff,
                contentDescription = null,
                tint = Tokens.Color.Text.inverse,
                modifier = Modifier.size(Tokens.Size.iconSm),
            )
            Text(
                text = "网络中断 · 作业提交已暂停",
                color = Tokens.Color.Text.inverse,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = Tokens.Space.space3),
            )
            TextButton(onClick = onRetry) {
                Text(
                    text = "重试",
                    color = Tokens.Color.Text.inverse,
                    style = MaterialTheme.typography.titleSmall,
                )
            }
        }
    }
}

@Preview(name = "w360", widthDp = 360, showBackground = true)
@Preview(name = "w390", widthDp = 390, showBackground = true)
@Preview(name = "w411", widthDp = 411, showBackground = true)
@Composable
private fun GlobalOfflineBarPreview() {
    JxPreviewTheme {
        GlobalOfflineBar(online = MutableStateFlow(false), onRetry = {})
    }
}

@Preview(name = "w390", widthDp = 390, showBackground = true)
@Composable
private fun GlobalOfflineBarOnlinePreview() {
    JxPreviewTheme {
        GlobalOfflineBar(online = MutableStateFlow(true), onRetry = {})
    }
}
