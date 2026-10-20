package cn.hunanjiangxi.jxlabtrace

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import cn.hunanjiangxi.jxlabtrace.auth.LoginFailure
import cn.hunanjiangxi.jxlabtrace.auth.LoginFailureKind
import cn.hunanjiangxi.jxlabtrace.auth.Me
import cn.hunanjiangxi.jxlabtrace.auth.loginOutcomeOf
import cn.hunanjiangxi.jxlabtrace.auth.LoginOutcome
import cn.hunanjiangxi.jxlabtrace.auth.LoginSignal
import cn.hunanjiangxi.jxlabtrace.ui.components.JxPreviewTheme
import cn.hunanjiangxi.jxlabtrace.ui.login.LoginScreen
import cn.hunanjiangxi.jxlabtrace.ui.login.LoginUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * LOGIN-FLOW-FIX：登录后导航缺陷修复（F-1 / F-2）。
 * - F-1 NoRole 失败卡：「重试登录」触发 onRetry（beginAuthorize）；「我知道了」触发 onAcknowledge（finish 信号）。
 * - F-2 MainActivity 路由纯函数 shouldRouteToLogin：null / 空 / 非空三分支。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class LoginFlowFixTest {

    @get:Rule
    val compose = createComposeRule()

    private fun noRoleFailedState(): LoginUiState.Failed {
        val outcome = loginOutcomeOf(
            LoginSignal.MeLoaded(Me(name = "某人", roles = emptyList(), permissions = emptyList())),
        ) as LoginOutcome.Failed
        return LoginUiState.Failed(outcome.failure)
    }

    private fun setFailedContent(
        onRetry: () -> Unit,
        onAcknowledge: () -> Unit,
    ) {
        compose.setContent {
            JxPreviewTheme {
                LoginScreen(
                    state = noRoleFailedState(),
                    versionName = "local00",
                    serverHost = "内网 10.0.0.12",
                    onLogin = {}, onManualOpen = {}, onCancel = {},
                    onRetry = onRetry,
                    onAcknowledge = onAcknowledge,
                    onCallSupport = {},
                )
            }
        }
    }

    @Test
    fun `NoRole 失败卡 - 重试登录按钮显示且点击触发 onRetry`() {
        var retried = false
        setFailedContent(onRetry = { retried = true }, onAcknowledge = {})
        compose.onNodeWithText("重试登录").assertIsDisplayed()
        compose.onNodeWithText("重试登录").performClick()
        assertTrue("重试登录应触发 onRetry（beginAuthorize）", retried)
    }

    @Test
    fun `NoRole 失败卡 - 我知道了显示且点击触发 onAcknowledge（finish 信号）`() {
        var acknowledged = false
        setFailedContent(onRetry = {}, onAcknowledge = { acknowledged = true })
        compose.onNodeWithText("我知道了").assertIsDisplayed()
        compose.onNodeWithText("我知道了").performClick()
        assertTrue("我知道了应触发 onAcknowledge（宿主 finish 返回主屏）", acknowledged)
    }

    @Test
    fun `NoRole 失败卡 - 重试与我知道了互不串扰`() {
        var retried = false
        var acknowledged = false
        setFailedContent(onRetry = { retried = true }, onAcknowledge = { acknowledged = true })
        compose.onNodeWithText("重试登录").performClick()
        assertTrue(retried)
        assertFalse("点重试不应触发 acknowledge", acknowledged)
        compose.onNodeWithText("我知道了").performClick()
        assertTrue(acknowledged)
    }

    @Test
    fun `F-2 shouldRouteToLogin - null 视为无会话应去登录`() {
        assertTrue(shouldRouteToLogin(null))
    }

    @Test
    fun `F-2 shouldRouteToLogin - 空串与纯空白应去登录`() {
        assertTrue(shouldRouteToLogin(""))
        assertTrue(shouldRouteToLogin("   "))
    }

    @Test
    fun `F-2 shouldRouteToLogin - 非空 sid 停留主页不拉登录`() {
        assertFalse(shouldRouteToLogin("sid-abc"))
        assertFalse(shouldRouteToLogin("0123456789abcdef"))
    }

    @Test
    fun `NoRole 数据层 - canRetry 仍为 false 且主出路是我知道了`() {
        val failure: LoginFailure = noRoleFailedState().failure
        assertEquals(LoginFailureKind.NoRole, failure.kind)
        assertFalse("数据层 canRetry 保持 false（M3-1 口径，UI 另加副按钮不改标志）", failure.canRetry)
        assertEquals("我知道了", failure.actionLabel)
    }
}
