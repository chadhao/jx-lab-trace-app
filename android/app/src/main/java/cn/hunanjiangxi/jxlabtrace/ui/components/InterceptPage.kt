package cn.hunanjiangxi.jxlabtrace.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import cn.hunanjiangxi.jxlabtrace.ui.icons.JxIcons
import cn.hunanjiangxi.jxlabtrace.ui.theme.Tokens

/**
 * InterceptPage：拦截页三要素 —— **原因 + 谁负责 + 一键拨号**（缺一不可）。
 * 例：无预报 / 车辆档案不存在 / 无权限 / 码无效。
 */
@Composable
fun InterceptPage(
    reason: String,
    owner: String,
    phone: String,
    onCall: () -> Unit,
    modifier: Modifier = Modifier,
    detail: String? = null,
    icon: ImageVector = JxIcons.Intercept,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(Tokens.Space.space6),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(Tokens.Size.touchComfort * 1.6f)
                .clip(CircleShape)
                .background(Tokens.Color.Status.dangerSoft),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Tokens.Color.Status.danger,
                modifier = Modifier.size(Tokens.Size.iconLg),
            )
        }
        Text(
            text = reason,
            color = Tokens.Color.Text.primary,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Tokens.Space.space5),
        )
        if (detail != null) {
            Text(
                text = detail,
                color = Tokens.Color.Text.secondary,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = Tokens.Space.space3),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Tokens.Space.space5),
            horizontalArrangement = Arrangement.Center,
        ) {
            StatusPill(text = "负责人：$owner", tone = PillTone.Info)
        }
        BigButton(
            text = "拨打 $phone",
            onClick = onCall,
            modifier = Modifier.padding(top = Tokens.Space.space6),
        )
        Text(
            text = "综合运营部 · 工作日 08:00–18:00",
            color = Tokens.Color.Text.tertiary,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(top = Tokens.Space.space2),
        )
    }
}

@Preview(name = "w360", widthDp = 360, showBackground = true)
@Preview(name = "w390", widthDp = 390, showBackground = true)
@Preview(name = "w411", widthDp = 411, showBackground = true)
@Composable
private fun InterceptPagePreview() {
    JxPreviewTheme {
        InterceptPage(
            reason = "该车牌今日无预报",
            detail = "湘F·12345 最近 3 条预报均不在今日。请电话报车号，PC 补预报后下拉刷新重试。",
            owner = "综合运营部",
            phone = "0730-xxxxxxx",
            onCall = {},
        )
    }
}
