package cn.hunanjiangxi.jxlabtrace

import cn.hunanjiangxi.jxlabtrace.auth.LoginDiag
import cn.hunanjiangxi.jxlabtrace.log.LogEntry
import cn.hunanjiangxi.jxlabtrace.log.LogLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 登录诊断导出文本：逐行格式 + 空缓冲兜底。 */
class LoginDiagTest {

    @Test
    fun `空缓冲返回兜底文案`() {
        assertEquals("(缓冲为空)", LoginDiag.exportText(emptyList()))
    }

    @Test
    fun `单条格式为 HH_mm_ss_SSS LEVEL tag message`() {
        val millis = 1_730_000_000_123L
        val entry = LogEntry(
            timeMillis = millis,
            level = LogLevel.I,
            tag = "LoginWeb",
            message = "onPageStarted: https://example.com/start",
        )
        val expectedStamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.CHINA).format(Date(millis))
        assertEquals(
            "$expectedStamp I [LoginWeb] onPageStarted: https://example.com/start",
            LoginDiag.exportText(listOf(entry)),
        )
    }

    @Test
    fun `多条按时间顺序逐行拼接`() {
        val a = LogEntry(1_000L, LogLevel.D, "A", "first")
        val b = LogEntry(2_000L, LogLevel.E, "LoginWeb", "console[E] boom (x:1)")
        val lines = LoginDiag.exportText(listOf(a, b)).lines()
        assertEquals(2, lines.size)
        val fmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.CHINA)
        assertTrue(lines[0].startsWith(fmt.format(Date(1_000L))))
        assertTrue(lines[0].endsWith(" D [A] first"))
        assertTrue(lines[1].startsWith(fmt.format(Date(2_000L))))
        assertTrue(lines[1].endsWith(" E [LoginWeb] console[E] boom (x:1)"))
    }

    @Test
    fun `每行级别与标签用方括号包裹`() {
        val line = LoginDiag.formatLine(
            LogEntry(0L, LogLevel.W, "LoginWeb", "warn-msg"),
            timeMillis = 0L,
        )
        assertTrue(Regex("""^\d{2}:\d{2}:\d{2}\.\d{3} W \[LoginWeb\] warn-msg$""").matches(line))
    }
}
