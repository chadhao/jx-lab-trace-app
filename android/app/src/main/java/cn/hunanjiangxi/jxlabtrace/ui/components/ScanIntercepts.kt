package cn.hunanjiangxi.jxlabtrace.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import cn.hunanjiangxi.jxlabtrace.net.ApiError
import cn.hunanjiangxi.jxlabtrace.net.ErrorMessages
import cn.hunanjiangxi.jxlabtrace.scan.ScanFailure
import cn.hunanjiangxi.jxlabtrace.scan.display
import cn.hunanjiangxi.jxlabtrace.ui.icons.JxIcons
import cn.hunanjiangxi.jxlabtrace.ui.theme.Tokens

/** 综合运营部电话（拦截拨号用；号码为设计稿占位，待 WorkBuddy 定稿）。 */
const val OPERATIONS_PHONE = "0730-xxxxxxx"

/**
 * M12 四类拦截页实体之一：**码无效**（NotOurs / UnknownCode）。
 * 三要素齐备：原因 + 谁负责（综合运营部）+ 一键拨号；出路另给「返回重扫」（无死胡同）。
 */
@Composable
fun CodeInvalidIntercept(
    failure: ScanFailure,
    onBackToScan: () -> Unit,
    onCall: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val display = failure.display()
    Column(modifier = modifier.fillMaxSize()) {
        InterceptPage(
            reason = display.title,
            detail = "${display.detail}\n${display.codeLine}",
            owner = "综合运营部",
            phone = OPERATIONS_PHONE,
            onCall = onCall,
            modifier = Modifier.fillMaxWidth(),
            icon = JxIcons.Exception,
        )
        BigButton(
            text = "返回重扫",
            onClick = onBackToScan,
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = Tokens.Space.space6,
                    end = Tokens.Space.space6,
                    bottom = Tokens.Space.space6,
                ),
        )
    }
}

/**
 * M12 四类拦截页实体之二：**无权限**（403 Forbidden，含当前角色显示）。
 * 话术 = `ErrorMessages.of(Forbidden)`（SCOPE 铁律 2：给话术，不给白屏/技术码）。
 */
@Composable
fun ForbiddenIntercept(
    currentRoles: List<String>,
    onCall: () -> Unit,
    modifier: Modifier = Modifier,
) {
    InterceptPage(
        reason = ErrorMessages.of(ApiError.Forbidden()),
        detail = if (currentRoles.isEmpty()) {
            "当前角色：（未分配角色）"
        } else {
            "当前角色：${currentRoles.joinToString(" · ")}"
        },
        owner = "综合运营部",
        phone = OPERATIONS_PHONE,
        onCall = onCall,
        modifier = modifier,
        icon = JxIcons.Intercept,
    )
}

@Preview(name = "w360", widthDp = 360, showBackground = true)
@Preview(name = "w390", widthDp = 390, showBackground = true)
@Preview(name = "w411", widthDp = 411, showBackground = true)
@Composable
private fun CodeInvalidInterceptPreview() {
    JxPreviewTheme {
        CodeInvalidIntercept(
            failure = ScanFailure.UnknownCode("格式正确但系统内不存在：1B…007"),
            onBackToScan = {},
            onCall = {},
        )
    }
}

@Preview(name = "w360", widthDp = 360, showBackground = true)
@Preview(name = "w390", widthDp = 390, showBackground = true)
@Preview(name = "w411", widthDp = 411, showBackground = true)
@Composable
private fun ForbiddenInterceptPreview() {
    JxPreviewTheme {
        ForbiddenIntercept(currentRoles = listOf("收货员 receiver"), onCall = {})
    }
}
