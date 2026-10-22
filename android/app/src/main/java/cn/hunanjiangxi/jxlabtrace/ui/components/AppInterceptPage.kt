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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import cn.hunanjiangxi.jxlabtrace.net.ApiError
import cn.hunanjiangxi.jxlabtrace.net.ErrorMessages
import cn.hunanjiangxi.jxlabtrace.ui.icons.JxIcons
import cn.hunanjiangxi.jxlabtrace.ui.theme.Tokens

/**
 * 结构化拦截四字段（docs/APP-API-A-B.md §2.1/§5）→ 三段展示：
 * ① 首行 [ApiError.Intercept.error] 短标签作标题；
 * ② 正文 [ApiError.Intercept.reason] 人话原因；
 * ③ 底部固定 owner（谁负责）+ contact（怎么联系）。
 * 出路按钮由调用方按场景给（403 无重试 / 409 刷新 / 404 联系文员——§5.4–6）。
 * 不嵌套 InterceptPage（其内部强制 fillMaxSize，在带按钮的布局里会把下方内容挤出屏幕）。
 */
@Composable
fun AppInterceptPage(
    intercept: ApiError.Intercept,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actionText: String = "返回",
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
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
                imageVector = JxIcons.Intercept,
                contentDescription = null,
                tint = Tokens.Color.Status.danger,
                modifier = Modifier.size(Tokens.Size.iconLg),
            )
        }
        // ① error 短标签作标题
        Text(
            text = intercept.error.ifBlank { "操作被拦截" },
            color = Tokens.Color.Text.primary,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Tokens.Space.space5),
        )
        // ② reason 人话原因
        Text(
            text = intercept.reason,
            color = Tokens.Color.Text.secondary,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Tokens.Space.space3),
        )
        // ③ owner 责任角色
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Tokens.Space.space5),
            horizontalArrangement = Arrangement.Center,
        ) {
            StatusPill(text = "负责人：${intercept.owner}", tone = PillTone.Info)
        }
        // ③ contact 联系途径
        Text(
            text = "联系途径：${intercept.contact}",
            color = Tokens.Color.Text.secondary,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Tokens.Space.space3),
        )
        if (onAction != null) {
            BigButton(
                text = actionText,
                onClick = onAction,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Tokens.Space.space6),
            )
            BigButton(
                text = "返回",
                onClick = onBack,
                style = BigButtonStyle.Quiet,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Tokens.Space.space3),
            )
        } else {
            BigButton(
                text = "返回",
                onClick = onBack,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Tokens.Space.space6),
            )
        }
    }
}

/** S04/S05/S06 错误态统一渲染：结构化拦截 ⇒ 四字段页；否则旧话术空态。 */
@Composable
fun ErrorStateContent(
    error: ApiError,
    fallbackTitle: String,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    if (error is ApiError.Intercept && onBack != null) {
        AppInterceptPage(
            intercept = error,
            onBack = onBack,
            modifier = modifier,
        )
    } else {
        EmptyState(
            title = fallbackTitle,
            hint = ErrorMessages.of(error),
            modifier = modifier.padding(Tokens.Space.space6),
        )
    }
}

@Preview(name = "w360", widthDp = 360, showBackground = true)
@Preview(name = "w390", widthDp = 390, showBackground = true)
@Preview(name = "w411", widthDp = 411, showBackground = true)
@Composable
private fun AppInterceptPagePreview() {
    JxPreviewTheme {
        AppInterceptPage(
            intercept = ApiError.Intercept(
                httpCode = 404,
                error = "无预报",
                reason = "该车牌今日无预报记录，不能确认到货。",
                owner = "收货文员（预报登记）",
                contact = "飞书联系收货文员在 Web「收货 › 预报登记」补录预报后再操作",
            ),
            onBack = {},
        )
    }
}
