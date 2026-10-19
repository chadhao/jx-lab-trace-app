package cn.hunanjiangxi.jxlabtrace.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import cn.hunanjiangxi.jxlabtrace.ui.theme.JxLabTheme

/** 预览与组件测试共用的浅色主题壳。 */
@Composable
internal fun JxPreviewTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = JxLabTheme.lightColorScheme(),
        typography = JxLabTheme.typography,
        shapes = JxLabTheme.shapes,
        content = content,
    )
}
