package cn.hunanjiangxi.jxlabtrace

import cn.hunanjiangxi.jxlabtrace.auth.LoginDiag
import cn.hunanjiangxi.jxlabtrace.log.CrashLogWriter
import cn.hunanjiangxi.jxlabtrace.log.LogEntry
import cn.hunanjiangxi.jxlabtrace.log.LogLevel
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * 全局崩溃留栈（追加任务 2026-10-22）：
 * formatCrash 纯函数格式 + append/readAll 文件往返 + exportTextWithCrash 并入。
 */
class CrashLogWriterTest {

    @Test
    fun `formatCrash - 含时间线程异常类完整堆栈版本号`() {
        val t = RuntimeException("蓝牙 socket 失败")
        val text = CrashLogWriter.formatCrash(
            timeMillis = 1_761_150_000_000L, // 2026-10-22 附近
            threadName = "main",
            throwable = t,
            versionName = "9b38484",
        )
        assertTrue(text.startsWith(CrashLogWriter.HEADER_PREFIX))
        assertTrue("应含版本号：$text", text.contains("v9b38484"))
        assertTrue(text.contains("thread: main"))
        assertTrue(text.contains("java.lang.RuntimeException: 蓝牙 socket 失败"))
        assertTrue("应含堆栈帧：$text", text.contains("at ")) // printStackTrace 产物
        assertTrue(text.contains("===== END CRASH ====="))
    }

    @Test
    fun `append与readAll - 追加式写读往返`() {
        val dir = TemporaryFolder()
        dir.create()
        try {
            val file = File(dir.root, CrashLogWriter.FILE_NAME)
            val block1 = CrashLogWriter.formatCrash(0L, "main", IllegalStateException("a"), "v1")
            val block2 = CrashLogWriter.formatCrash(1L, "Binder", IllegalStateException("b"), "v1")
            assertTrue(CrashLogWriter.append(file, block1))
            assertTrue(CrashLogWriter.append(file, block2))
            val all = CrashLogWriter.readAll(file)
            assertTrue(all.contains("IllegalStateException: a"))
            assertTrue("追加式应含两条：$all", all.contains("IllegalStateException: b"))
            // 第二次写入不覆盖第一次
            assertEquals(2, all.split(CrashLogWriter.HEADER_PREFIX).size - 1)
        } finally {
            dir.delete()
        }
    }

    @Test
    fun `readAll - 文件不存在返回空串`() {
        val missing = File("nowhere", "no-such-crash-log.txt")
        assertEquals("", CrashLogWriter.readAll(missing))
    }

    @Test
    fun `install - 捕获后写入文件并交还原handler`() {
        val dir = TemporaryFolder()
        dir.create()
        try {
            val file = File(dir.root, CrashLogWriter.FILE_NAME)
            var previousCalled = false
            val fakePrevious = Thread.UncaughtExceptionHandler { _, _ -> previousCalled = true }
            // 先装 fake 作为「系统默认」，再 install 链上去
            Thread.setDefaultUncaughtExceptionHandler(fakePrevious)
            CrashLogWriter.install(file, versionName = "test")

            val thread = Thread.currentThread()
            val boom = IllegalArgumentException("boom")
            // 直接调用当前 handler（模拟未捕获分发）
            Thread.getDefaultUncaughtExceptionHandler()!!.uncaughtException(thread, boom)

            assertTrue("crash-log 应被写入", file.isFile && file.readText().contains("boom"))
            assertTrue("应交还原 handler", previousCalled)
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(null)
            dir.delete()
        }
    }

    @Test
    fun `exportTextWithCrash - 上诊断下崩溃两段`() {
        val entries = listOf(
            LogEntry(timeMillis = 0L, level = LogLevel.E, tag = "Printer", message = "连接失败"),
        )
        val out = LoginDiag.exportTextWithCrash(entries, "===== CRASH xxx =====\nthread: main")
        assertTrue(out.contains("诊断日志"))
        assertTrue(out.contains("[Printer] 连接失败"))
        assertTrue(out.contains("崩溃留栈"))
        assertTrue(out.contains("thread: main"))
    }

    @Test
    fun `exportTextWithCrash - 崩溃为空占位`() {
        val out = LoginDiag.exportTextWithCrash(emptyList(), "")
        assertTrue(out.contains("(缓冲为空)"))
        assertTrue(out.contains("(无崩溃记录)"))
    }
}
