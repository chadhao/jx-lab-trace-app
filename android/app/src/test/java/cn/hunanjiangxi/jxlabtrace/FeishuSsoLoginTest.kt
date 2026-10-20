package cn.hunanjiangxi.jxlabtrace

import cn.hunanjiangxi.jxlabtrace.auth.FeishuSsoLogin
import cn.hunanjiangxi.jxlabtrace.auth.LoginSignal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MIMO-TASK-LARKSSO：FeishuSsoLogin 纯函数单测（state / percent-encode / SDK 错误码分类）。
 */
class FeishuSsoLoginTest {

    @Test
    fun `newState 是 32 位小写 hex 且两次不重复`() {
        val a = FeishuSsoLogin.newState()
        val b = FeishuSsoLogin.newState()
        assertTrue("期望 32 位 hex，实际 $a", a.matches(Regex("[0-9a-f]{32}")))
        assertTrue("期望 32 位 hex，实际 $b", b.matches(Regex("[0-9a-f]{32}")))
        assertNotEquals(a, b)
    }

    @Test
    fun `encode 单次 percent-encode 空格转百分之二十且不双重编码`() {
        assertEquals("a%20b", FeishuSsoLogin.encode("a b"))
        assertEquals("%2Fpath%2Fa", FeishuSsoLogin.encode("/path/a"))
        // 对已编码串再跑一次 encode 会二次编码——单次调用语义由本用例锚定
        val once = FeishuSsoLogin.encode("a b")
        assertNotEquals(once, FeishuSsoLogin.encode(once))
    }

    @Test
    fun `SDK -4 跳转飞书失败 返回 null 供回落 WebView 兜底`() {
        assertNull(FeishuSsoLogin.signalForError("-4"))
        assertNull(FeishuSsoLogin.signalForError(" -4 "))
    }

    @Test
    fun `SDK -3 用户取消 映射 SsoDenied`() {
        assertEquals(LoginSignal.SsoDenied, FeishuSsoLogin.signalForError("-3"))
    }

    @Test
    fun `SDK -1 state 不匹配 映射 StateMismatch`() {
        assertEquals(LoginSignal.StateMismatch, FeishuSsoLogin.signalForError("-1"))
    }

    @Test
    fun `其余错误码与空值 映射 AuthorizationRejected`() {
        listOf("-2", "-5", "-6", "0", "unknown", "", null).forEach { code ->
            assertEquals("code=$code", LoginSignal.AuthorizationRejected, FeishuSsoLogin.signalForError(code))
        }
    }
}
