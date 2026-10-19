package cn.hunanjiangxi.jxlabtrace

import cn.hunanjiangxi.jxlabtrace.auth.LoginFailureKind
import cn.hunanjiangxi.jxlabtrace.auth.LoginOutcome
import cn.hunanjiangxi.jxlabtrace.auth.LoginSignal
import cn.hunanjiangxi.jxlabtrace.auth.Me
import cn.hunanjiangxi.jxlabtrace.auth.loginOutcomeOf
import cn.hunanjiangxi.jxlabtrace.net.ApiError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M3-1：S01 三类失败文案不同；③未分配角色无重试按钮（canRetry=false）。
 */
class LoginFailureTest {

    @Test
    fun `网络不可达类 - 文案与出路`() {
        val failed = loginOutcomeOf(LoginSignal.NetworkDown) as LoginOutcome.Failed
        val failure = failed.failure
        assertEquals(LoginFailureKind.Network, failure.kind)
        assertTrue("网络类文案应说清网络原因", failure.reason.contains("网络不可达"))
        assertTrue("网络类应可重试", failure.canRetry)
        assertEquals("重试", failure.actionLabel)
    }

    @Test
    fun `授权被拒类 - 与网络类文案不同且可重试`() {
        val failed = loginOutcomeOf(LoginSignal.AuthorizationRejected) as LoginOutcome.Failed
        val failure = failed.failure
        assertEquals(LoginFailureKind.Rejected, failure.kind)
        assertTrue("授权类文案应说清授权原因", failure.reason.contains("授权"))
        assertTrue("授权类应可重试", failure.canRetry)
    }

    @Test
    fun `未分配角色类 - 文案不同且无重试按钮`() {
        val failed = loginOutcomeOf(
            LoginSignal.MeLoaded(Me(name = "某人", roles = emptyList(), permissions = emptyList())),
        ) as LoginOutcome.Failed
        val failure = failed.failure
        assertEquals(LoginFailureKind.NoRole, failure.kind)
        assertTrue("应提示联系综合运营部", failure.reason.contains("综合运营部"))
        assertTrue("未分配角色是业务问题，不允许重试（M3-1 核心断言）", !failure.canRetry)
        assertEquals("我知道了", failure.actionLabel)
    }

    @Test
    fun `三类失败文案互不相同`() {
        val network = (loginOutcomeOf(LoginSignal.NetworkDown) as LoginOutcome.Failed).failure
        val rejected =
            (loginOutcomeOf(LoginSignal.AuthorizationRejected) as LoginOutcome.Failed).failure
        val noRole = (loginOutcomeOf(
            LoginSignal.MeLoaded(Me(name = "某人")),
        ) as LoginOutcome.Failed).failure

        assertNotEquals(network.reason, rejected.reason)
        assertNotEquals(network.reason, noRole.reason)
        assertNotEquals(rejected.reason, noRole.reason)
        assertNotEquals(network.errorCode, rejected.errorCode)
        assertNotEquals(network.errorCode, noRole.errorCode)
        assertNotEquals(rejected.errorCode, noRole.errorCode)
    }

    @Test
    fun `有角色则登录成功`() {
        val outcome = loginOutcomeOf(
            LoginSignal.MeLoaded(Me(name = "刘川", roles = listOf("receiver"))),
        )
        assertTrue("有业务角色应判成功", outcome is LoginOutcome.Success)
    }

    @Test
    fun `me 接口 401 - 归入授权被拒类`() {
        val failed = loginOutcomeOf(LoginSignal.MeError(ApiError.Unauthorized()))
            as LoginOutcome.Failed
        assertEquals(LoginFailureKind.Rejected, failed.failure.kind)
    }
}
