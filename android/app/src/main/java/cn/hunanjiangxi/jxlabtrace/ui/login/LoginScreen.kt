package cn.hunanjiangxi.jxlabtrace.ui.login

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.clickable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import cn.hunanjiangxi.jxlabtrace.R
import cn.hunanjiangxi.jxlabtrace.auth.LoginFailure
import cn.hunanjiangxi.jxlabtrace.auth.LoginFailureKind
import cn.hunanjiangxi.jxlabtrace.auth.Me
import cn.hunanjiangxi.jxlabtrace.ui.components.JxPreviewTheme
import cn.hunanjiangxi.jxlabtrace.ui.icons.JxIcons
import cn.hunanjiangxi.jxlabtrace.ui.theme.Tokens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** S01 登录四态（design/screens/index.html#s01）。 */
sealed interface LoginUiState {
    /** ① 初始：全屏只有一个实心按钮。 */
    object Initial : LoginUiState

    /** ② 授权中：5s 超时给「手动打开」；[onRemotePage]=true 时展示飞书页本身。 */
    data class Authorizing(
        val manualAvailable: Boolean = false,
        val onRemotePage: Boolean = false,
        /** 诊断：WebView 最近一次开始加载的 URL（透出在过渡屏小字）。 */
        val diagUrl: String = "",
    ) : LoginUiState

    /** ④ 成功过渡：1.2s 自动进占位主页。 */
    data class Success(val me: Me) : LoginUiState

    /** ③ 失败卡：三类分因，NoRole 无重试按钮。 */
    data class Failed(val failure: LoginFailure) : LoginUiState
}

/** 角色码 → 中文（S01 成功页副行）。 */
fun roleLabel(code: String): String = when (code) {
    "receiver" -> "收货员"
    "production" -> "生产班组"
    "management" -> "管理层"
    "sales" -> "销售"
    else -> code
}

/**
 * S01 登录屏：按设计稿四态渲染。
 * 授权中且已跳到飞书远端页（[LoginUiState.Authorizing.onRemotePage]）时不盖任何蒙层，
 * 由宿主让 WebView 内容直接呈现给用户操作。
 */
@Composable
fun LoginScreen(
    state: LoginUiState,
    versionName: String,
    serverHost: String,
    onLogin: () -> Unit,
    onManualOpen: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onAcknowledge: () -> Unit,
    onCallSupport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val exposeWebContent = (state as? LoginUiState.Authorizing)?.onRemotePage == true
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Tokens.Color.Brand.primary),
    ) {
        when {
            exposeWebContent -> Unit // 飞书授权页由 WebView 直接呈现，不盖任何蒙层

            state is LoginUiState.Initial -> InitialContent(versionName, serverHost, onLogin)

            state is LoginUiState.Authorizing ->
                AuthorizingContent(state, onManualOpen, onCancel)

            state is LoginUiState.Success -> SuccessContent(state.me)

            state is LoginUiState.Failed ->
                FailedContent(state.failure, onRetry, onAcknowledge, onCallSupport)
        }
    }
}

/** 品牌区 + 说明 + 唯一主按钮 + 底部服务状态。 */
@Composable
private fun InitialContent(
    versionName: String,
    serverHost: String,
    onLogin: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Tokens.Space.space6),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(Tokens.Space.space12))
        BrandMark()
        Spacer(Modifier.height(Tokens.Space.space5))
        Text(
            text = "江熙生产端",
            color = Tokens.Color.Brand.onPrimary,
            fontSize = Tokens.Type.Display.size,
            lineHeight = Tokens.Type.Display.lineHeight,
            fontWeight = FontWeight(Tokens.Type.Display.weight),
        )
        Text(
            text = "JX LAB TRACE · 实验检测数据追踪",
            color = Tokens.Color.Brand.onPrimary,
            fontSize = Tokens.Type.Label.size,
            modifier = Modifier.padding(top = Tokens.Space.space2),
            letterSpacing = 0.6.sp,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = "用企业飞书账号登录。\n登录账号即操作人，权限由后台角色决定。",
            color = Tokens.Color.Brand.onPrimary,
            fontSize = Tokens.Type.Body.size,
            lineHeight = Tokens.Type.Body.lineHeight,
            textAlign = TextAlign.Center,
        )
        Button(
            onClick = onLogin,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Tokens.Space.space6)
                .height(Tokens.Size.primaryButton),
            shape = RoundedCornerShape(Tokens.Radius.md),
            colors = ButtonDefaults.buttonColors(
                containerColor = Tokens.Color.Brand.onPrimary,
                contentColor = Tokens.Color.Brand.primary,
            ),
        ) {
            Text("使用飞书账号登录", style = MaterialTheme.typography.titleMedium)
        }
        Text(
            text = "首次登录需在飞书内确认授权",
            color = Tokens.Color.Brand.onPrimary,
            fontSize = Tokens.Type.Label.size,
            modifier = Modifier.padding(top = Tokens.Space.space3),
        )
        Spacer(Modifier.weight(1f))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = Tokens.Space.space6),
        ) {
            Box(
                Modifier
                    .size(Tokens.Space.space2)
                    .clip(CircleShape)
                    .background(Tokens.Color.Status.success),
            )
            Text(
                text = "服务正常 · v$versionName · $serverHost",
                color = Tokens.Color.Brand.onPrimary,
                fontSize = Tokens.Type.Label.size,
                modifier = Modifier.padding(start = Tokens.Space.space2),
            )
        }
    }
}

/** 授权中：转圈 + 超时后的手动打开兜底 + 取消。 */
@Composable
private fun AuthorizingContent(
    state: LoginUiState.Authorizing,
    onManualOpen: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Tokens.Space.space6),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(
            color = Tokens.Color.Brand.onPrimary,
            modifier = Modifier.size(Tokens.Size.iconLg * 1.5f),
        )
        Text(
            text = "正在打开飞书授权…",
            color = Tokens.Color.Brand.onPrimary,
            fontSize = Tokens.Type.H3.size,
            fontWeight = FontWeight(Tokens.Type.H3.weight),
            modifier = Modifier.padding(top = Tokens.Space.space5),
        )
        Text(
            text = "若 5 秒内没有自动跳转，点下面手动打开",

            Spacer(Modifier.height(Tokens.Space.space3))
            Text(
                text = "正在加载：" + state.diagUrl.ifBlank { "(等待首个跳转)" },
                color = Tokens.Color.Brand.onPrimary.copy(alpha = 0.75f),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 3,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            color = Tokens.Color.Brand.onPrimary,
            fontSize = Tokens.Type.Label.size,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Tokens.Space.space3),
        )
        if (state.manualAvailable) {
            OutlinedButton(
                onClick = onManualOpen,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Tokens.Space.space4)
                    .height(Tokens.Size.primaryButton),
                shape = RoundedCornerShape(Tokens.Radius.md),
            ) {
                Text("手动打开授权页", color = Tokens.Color.Brand.onPrimary)
            }
        }
        Text(
            text = "取消登录",
            color = Tokens.Color.Brand.onPrimary,
            fontSize = Tokens.Type.Label.size,
            modifier = Modifier
                .padding(top = Tokens.Space.space5)
                .clip(RoundedCornerShape(Tokens.Radius.sm))
                .clickable(onClick = onCancel)
                .padding(horizontal = Tokens.Space.space4, vertical = Tokens.Space.space2),
        )
    }
}

/** 成功过渡：绿环对勾 + 欢迎语 + 角色行。 */
@Composable
private fun SuccessContent(me: Me) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(Tokens.Size.iconLg * 2.4f)
                .clip(CircleShape)
                .background(Tokens.Color.Status.success),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = JxIcons.Success,
                contentDescription = null,
                tint = Tokens.Color.Status.successSoft,
                modifier = Modifier.size(Tokens.Size.iconLg * 1.4f),
            )
        }
        Text(
            text = "欢迎，${me.name}",
            color = Tokens.Color.Brand.onPrimary,
            fontSize = Tokens.Type.H1.size,
            fontWeight = FontWeight(Tokens.Type.H1.weight),
            modifier = Modifier.padding(top = Tokens.Space.space5),
        )
        Text(
            text = me.allRoles.joinToString(" · ") { roleLabel(it) },
            color = Tokens.Color.Brand.onPrimary,
            fontSize = Tokens.Type.Body.size,
            modifier = Modifier.padding(top = Tokens.Space.space2),
        )
    }
}

/** 失败卡：原因 + 错误码 + 出路（NoRole 无重试，改为「我知道了」）。 */
@Composable
private fun FailedContent(
    failure: LoginFailure,
    onRetry: () -> Unit,
    onAcknowledge: () -> Unit,
    onCallSupport: () -> Unit,
) {
    val timeText = remember(failure.errorCode) {
        SimpleDateFormat("HH:mm:ss", Locale.CHINA).format(Date())
    }
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Bottom,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Tokens.Space.space5, vertical = Tokens.Space.space6)
                .clip(RoundedCornerShape(Tokens.Radius.lg))
                .background(Tokens.Color.Surface.card)
                .padding(Tokens.Space.space5),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(Tokens.Size.touchComfort)
                    .clip(CircleShape)
                    .background(Tokens.Color.Status.dangerSoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = JxIcons.Exception,
                    contentDescription = null,
                    tint = Tokens.Color.Status.danger,
                    modifier = Modifier.size(Tokens.Size.iconLg),
                )
            }
            Text(
                text = failure.title,
                color = Tokens.Color.Text.primary,
                fontSize = Tokens.Type.H2.size,
                fontWeight = FontWeight(Tokens.Type.H2.weight),
                modifier = Modifier.padding(top = Tokens.Space.space4),
            )
            Text(
                text = failure.reason,
                color = Tokens.Color.Text.secondary,
                fontSize = Tokens.Type.Body.size,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = Tokens.Space.space3),
            )
            Text(
                text = "错误码 ${failure.errorCode} · $timeText",
                color = Tokens.Color.Text.tertiary,
                fontSize = Tokens.Type.Label.size,
                modifier = Modifier.padding(top = Tokens.Space.space3),
            )
            if (failure.canRetry) {
                Button(
                    onClick = onRetry,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Tokens.Space.space5)
                        .height(Tokens.Size.primaryButton),
                    shape = RoundedCornerShape(Tokens.Radius.md),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Tokens.Color.Brand.primary,
                        contentColor = Tokens.Color.Brand.onPrimary,
                    ),
                ) {
                    Text(failure.actionLabel, style = MaterialTheme.typography.titleMedium)
                }
            } else {
                // ③未分配角色：业务问题，重试无意义 ⇒ 只出「我知道了」（M3-1）。
                OutlinedButton(
                    onClick = onAcknowledge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Tokens.Space.space5)
                        .height(Tokens.Size.primaryButton),
                    shape = RoundedCornerShape(Tokens.Radius.md),
                ) {
                    Text(failure.actionLabel, style = MaterialTheme.typography.titleMedium)
                }
            }
            Text(
                text = "查看排查指引",
                color = Tokens.Color.Brand.primary,
                fontSize = Tokens.Type.Label.size,
                modifier = Modifier
                    .padding(top = Tokens.Space.space4)
                    .clickable(onClick = onCallSupport),
            )
        }
    }
}

/** 品牌几何标：实心方块（码）+ 空格（袋）+ 右下三笔（检），取自设计稿描述。 */
@Composable
private fun BrandMark() {
    Image(
        painter = painterResource(R.drawable.ic_brand),
        contentDescription = "江熙生产端",
        modifier = Modifier.size(Tokens.Size.entryIcon * 2f),
    )
}

@Preview(name = "w360", widthDp = 360, showBackground = true)
@Preview(name = "w390", widthDp = 390, showBackground = true)
@Preview(name = "w411", widthDp = 411, showBackground = true)
@Composable
private fun LoginInitialPreview() {
    JxPreviewTheme {
        LoginScreen(
            state = LoginUiState.Initial,
            versionName = "local00",
            serverHost = "内网 10.0.0.12",
            onLogin = {}, onManualOpen = {}, onCancel = {}, onRetry = {},
            onAcknowledge = {}, onCallSupport = {},
        )
    }
}

@Preview(name = "w390", widthDp = 390, showBackground = true)
@Composable
private fun LoginAuthorizingPreview() {
    JxPreviewTheme {
        LoginScreen(
            state = LoginUiState.Authorizing(manualAvailable = true),
            versionName = "local00",
            serverHost = "内网 10.0.0.12",
            onLogin = {}, onManualOpen = {}, onCancel = {}, onRetry = {},
            onAcknowledge = {}, onCallSupport = {},
        )
    }
}

@Preview(name = "w390", widthDp = 390, showBackground = true)
@Composable
private fun LoginFailedPreview() {
    JxPreviewTheme {
        LoginScreen(
            state = LoginUiState.Failed(
                LoginFailure(
                    kind = LoginFailureKind.Network,
                    title = "登录未完成",
                    reason = "网络不可达，请确认已连车间 WiFi 或开启 4G",
                    errorCode = "AUTH_NETWORK",
                    canRetry = true,
                    actionLabel = "重试",
                ),
            ),
            versionName = "local00",
            serverHost = "内网 10.0.0.12",
            onLogin = {}, onManualOpen = {}, onCancel = {}, onRetry = {},
            onAcknowledge = {}, onCallSupport = {},
        )
    }
}

@Preview(name = "w390", widthDp = 390, showBackground = true)
@Composable
private fun LoginSuccessPreview() {
    JxPreviewTheme {
        LoginScreen(
            state = LoginUiState.Success(Me(name = "刘川", roles = listOf("receiver"))),
            versionName = "local00",
            serverHost = "内网 10.0.0.12",
            onLogin = {}, onManualOpen = {}, onCancel = {}, onRetry = {},
            onAcknowledge = {}, onCallSupport = {},
        )
    }
}
