package cn.hunanjiangxi.jxlabtrace

import cn.hunanjiangxi.jxlabtrace.print.KmPrinterBridge
import cn.hunanjiangxi.jxlabtrace.print.PrinterProfile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 自检打印内容构造纯函数单测（追加任务 2026-10-22 22:05）：
 * buildSelfTestContent 四行文本/时间格式/版本号/二维码与条码内容；
 * printFailMessage 失败码文案。
 */
class KmSelfTestContentTest {

    /** 固定时间戳：2026-10-22 22:15:30 +08:00 附近（本地时区格式化）。 */
    private val fixedMillis: Long = 1_792_678_530_000L

    @Test
    fun `buildSelfTestContent - 四行文本含标题时间版本纸宽`() {
        val c = KmPrinterBridge.buildSelfTestContent("9b38484", fixedMillis)
        assertEquals(4, c.lines.size)
        assertEquals("江熙追溯 · 打印自检", c.lines[0])
        assertTrue("时间行应含格式化时间：${c.lines[1]}", c.lines[1].startsWith("时间："))
        // 时间格式 yyyy-MM-dd HH:mm:ss
        val timePart = c.lines[1].removePrefix("时间：")
        assertTrue(
            "时间应匹配 yyyy-MM-dd HH:mm:ss：$timePart",
            Regex("""\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}""").containsMatchIn(timePart),
        )
        assertEquals("版本：9b38484", c.lines[2])
        assertTrue(
            "纸宽行应含 mm 与型号：${c.lines[3]}",
            c.lines[3].contains("mm") && c.lines[3].contains(KmPrinterBridge.MODEL),
        )
    }

    @Test
    fun `buildSelfTestContent - 时间格式与参考实现一致`() {
        val c = KmPrinterBridge.buildSelfTestContent("v1", fixedMillis)
        val expected = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(Date(fixedMillis))
        assertEquals("时间：$expected", c.lines[1])
        // 二维码内容 = 同一时间戳串
        assertEquals(expected, c.qrContent)
    }

    @Test
    fun `buildSelfTestContent - 二维码与条码内容非空且条码为数字`() {
        val c = KmPrinterBridge.buildSelfTestContent("v1", fixedMillis)
        assertTrue("二维码内容不应为空", c.qrContent.isNotBlank())
        assertTrue("条码内容不应为空", c.barcodeContent.isNotBlank())
        // CODE128 友好：纯数字
        assertTrue("条码应为数字串：${c.barcodeContent}", c.barcodeContent.all { it.isDigit() })
        // 条码日期数字 = yyyyMMddHHmmss（14 位）
        assertEquals(14, c.barcodeContent.length)
    }

    @Test
    fun `buildSelfTestContent - 页面尺寸用 PrinterProfile 口径`() {
        val c = KmPrinterBridge.buildSelfTestContent("v1", fixedMillis)
        assertEquals(PrinterProfile.MAX_WIDTH_DOTS, c.pageWidthDots)
        assertEquals(KmPrinterBridge.SELF_TEST_HEIGHT_DOTS, c.pageHeightDots)
        assertEquals(50 * PrinterProfile.DOTS_PER_MM, c.pageHeightDots)
    }

    @Test
    fun `describe - 含二维码与条码调用线索`() {
        val c = KmPrinterBridge.buildSelfTestContent("v1", fixedMillis)
        val d = c.describe()
        assertTrue(d.contains("qr="))
        assertTrue(d.contains("barcode="))
        assertTrue(d.contains("page="))
    }

    @Test
    fun `printFailMessage - 零码与非零码文案`() {
        assertEquals("打印失败（未知原因）", KmPrinterBridge.printFailMessage(0))
        val msg = KmPrinterBridge.printFailMessage(7)
        assertTrue(msg.contains("7"))
        assertTrue(msg.contains("纸张"))
    }
}
