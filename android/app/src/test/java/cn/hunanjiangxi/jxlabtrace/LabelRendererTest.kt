package cn.hunanjiangxi.jxlabtrace

import cn.hunanjiangxi.jxlabtrace.print.LabelRenderer
import cn.hunanjiangxi.jxlabtrace.print.PrinterProfile
import cn.hunanjiangxi.jxlabtrace.print.QrEncoder
import cn.hunanjiangxi.jxlabtrace.print.TsplPrinter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M9-1：LabelRenderer 三条单测 —— ①尺寸恒 800×800；②四角安全区全 0；③QR 定位图案存在。
 */
class LabelRendererTest {

    private val sample = LabelRenderer.render(
        bareCode = "1BCG00010002261020030070012",
        humanLine = "1B-CG-0001-0002-261020-03-007-012-2",
        objectType = "BAG",
        dateText = "2026-10-20",
        seqText = "012",
    )

    @Test
    fun `M9-1-1 尺寸恒800x800`() {
        val expected = PrinterProfile.MAX_WIDTH_DOTS * PrinterProfile.MAX_HEIGHT_DOTS
        assertEquals(expected, sample.size)
        assertEquals(800, PrinterProfile.MAX_WIDTH_DOTS)
        assertEquals(800, PrinterProfile.MAX_HEIGHT_DOTS)
        assertEquals(800 * 800, sample.size)
    }

    @Test
    fun `M9-1-2 四角安全区全0`() {
        val w = PrinterProfile.MAX_WIDTH_DOTS
        val h = PrinterProfile.MAX_HEIGHT_DOTS
        val m = PrinterProfile.SAFE_MARGIN_DOTS
        // 四角 m×m 区域必须全白（false）
        var black = 0
        for (y in 0 until m) {
            for (x in 0 until m) {
                if (sample[y * w + x]) black++
                if (sample[y * w + (w - 1 - x)]) black++
                if (sample[(h - 1 - y) * w + x]) black++
                if (sample[(h - 1 - y) * w + (w - 1 - x)]) black++
            }
        }
        assertEquals("四角安全区不得有黑点", 0, black)
    }

    @Test
    fun `M9-1-2 右缘2mm禁区全0`() {
        val w = PrinterProfile.MAX_WIDTH_DOTS
        val h = PrinterProfile.MAX_HEIGHT_DOTS
        val m = PrinterProfile.SAFE_MARGIN_DOTS
        for (y in 0 until h) {
            for (x in w - m until w) {
                assertFalse("右缘禁区 ($x,$y) 有黑点", sample[y * w + x])
            }
        }
    }

    @Test
    fun `M9-1-3 QR左上定位图案存在`() {
        // 独立验证编码器矩阵中的 finder（7×7：外圈黑、内环白、芯黑）
        val qr = QrEncoder.encode("1BCG00010002261020030070012")
        // 左上角 (0,0)-(6,6)
        assertTrue(QrEncoder.moduleAt(qr, 0, 0))
        assertTrue(QrEncoder.moduleAt(qr, 6, 0))
        assertTrue(QrEncoder.moduleAt(qr, 0, 6))
        assertTrue(QrEncoder.moduleAt(qr, 3, 3)) // 芯
        assertFalse(QrEncoder.moduleAt(qr, 1, 1)) // 内环白
        assertFalse(QrEncoder.moduleAt(qr, 5, 1))
        // 分隔带 (7,7) 为白
        assertFalse(QrEncoder.moduleAt(qr, 7, 7))

        // 渲染位图中对应区域（QR 块左上静区之后）也有 finder 黑点集群
        val w = PrinterProfile.MAX_WIDTH_DOTS
        val module = LabelRenderer.QR_DOTS_PER_MODULE
        val qrCore = QrEncoder.SIZE * module
        val quiet = QrEncoder.QUIET_ZONE * module
        val block = qrCore + quiet * 2
        val left = (w - block) / 2 + quiet
        val top = PrinterProfile.SAFE_MARGIN_DOTS + 8 + quiet
        // finder 中心模块 (3,3) 的中心像素应为黑
        val cx = left + 3 * module + module / 2
        val cy = top + 3 * module + module / 2
        assertTrue("位图中 QR finder 中心应为黑", sample[cy * w + cx])
        // 内环 (1,1) 中心像素应为白
        val ix = left + 1 * module + module / 2
        val iy = top + 1 * module + module / 2
        assertFalse("位图中 QR 内环应为白", sample[iy * w + ix])
    }

    @Test
    fun `QR模块数 - 每模块至少6点`() {
        assertTrue(LabelRenderer.QR_DOTS_PER_MODULE >= 6)
    }

    @Test
    fun `自检标尺 - 也满足尺寸与安全区`() {
        val ruler = LabelRenderer.renderRuler()
        assertEquals(800 * 800, ruler.size)
        val w = PrinterProfile.MAX_WIDTH_DOTS
        val m = PrinterProfile.SAFE_MARGIN_DOTS
        for (y in 0 until m) {
            for (x in 0 until m) {
                assertFalse(ruler[y * w + x])
            }
        }
    }
}

/**
 * M9-2：TsplPrinter 指令组装单测（不发真机）——
 * 断言指令字节含 SIZE 100 mm 与 PRINT 1，位图行数 800。
 */
class TsplPrinterTest {

    @Test
    fun `M9-2 指令含SIZE100mm与PRINT1且位图800行`() {
        val bits = LabelRenderer.render(
            bareCode = "1BCG00010002261020030070012",
            humanLine = "1B-CG-0001-0002-261020-03-007-012-2",
            objectType = "BAG",
            dateText = "2026-10-20",
            seqText = "012",
        )
        val checks = TsplPrinter.commandChecks(bits)
        assertTrue("应含 SIZE 100 mm", checks.containsSize100)
        assertTrue("应含 PRINT 1", checks.containsPrint1)
        assertEquals("位图行数应为 800", 800, checks.bitmapRows)
    }

    @Test
    fun `BITMAP头参数 - 宽800点按字节=100`() {
        val bits = BooleanArray(800 * 800)
        val cmd = TsplPrinter.buildCommand(bits).toString(Charsets.US_ASCII)
        assertTrue(cmd.contains("BITMAP 0,0,800,800,0,"))
        assertTrue(cmd.contains("CLS"))
    }

    @Test
    fun `packBits - 每行100字节`() {
        val bits = BooleanArray(800 * 800)
        bits[0] = true // 首像素黑 ⇒ 首字节 0x80
        val packed = TsplPrinter.packBits(bits, 800, 800)
        assertEquals(100 * 800, packed.size)
        assertEquals(0x80.toByte(), packed[0])
    }

    @Test
    fun `尺寸不符 - 抛异常`() {
        try {
            TsplPrinter.buildCommand(BooleanArray(10))
            throw AssertionError("应抛 IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("位图尺寸不符"))
        }
    }
}
