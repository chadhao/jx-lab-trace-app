package cn.hunanjiangxi.jxlabtrace

import cn.hunanjiangxi.jxlabtrace.log.AppLogger
import cn.hunanjiangxi.jxlabtrace.log.LogLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** 环形缓冲日志：容量 500、溢出丢最旧、可查询。 */
class AppLoggerTest {

    @Before
    fun setUp() {
        AppLogger.clear()
        AppLogger.configureCapacity(AppLogger.DEFAULT_CAPACITY)
    }

    @Test
    fun `写入 500 条全部保留`() {
        repeat(500) { AppLogger.i("T", "msg-$it") }
        val all = AppLogger.snapshot()
        assertEquals(500, all.size)
        assertEquals("msg-0", all.first().message)
        assertEquals("msg-499", all.last().message)
    }

    @Test
    fun `超过容量丢弃最旧条目`() {
        repeat(520) { AppLogger.i("T", "msg-$it") }
        val all = AppLogger.snapshot()
        assertEquals(500, all.size)
        assertEquals("msg-20", all.first().message)
        assertEquals("msg-519", all.last().message)
    }

    @Test
    fun `query 按关键字过滤`() {
        AppLogger.log(LogLevel.E, "ApiClient", "前置探测拦截：/healthz 不可达")
        AppLogger.i("Other", "无关日志")
        val hits = AppLogger.query("前置探测")
        assertEquals(1, hits.size)
        assertTrue(hits.single().message.contains("前置探测拦截"))
    }
}
