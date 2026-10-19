package cn.hunanjiangxi.jxlabtrace

import cn.hunanjiangxi.jxlabtrace.print.PrintSession
import cn.hunanjiangxi.jxlabtrace.print.ByteArraySpp
import cn.hunanjiangxi.jxlabtrace.print.SppFactory
import cn.hunanjiangxi.jxlabtrace.print.TsplPrinter
import cn.hunanjiangxi.jxlabtrace.print.LabelRenderer
import cn.hunanjiangxi.jxlabtrace.recv.WeighLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** S05 过磅纯逻辑 + SPP 发送会话单测。 */
class WeighAndSppTest {

    // ---- 过磅 ----

    @Test
    fun `净重=毛-皮 - 3位小数`() {
        assertEquals(23.56, WeighLogic.netTons("32.18", "8.62")!!, 0.0001)
        assertEquals(30.5, WeighLogic.netTons("35.5", "5.0")!!, 0.0001)
    }

    @Test
    fun `非法输入 - 净重null`() {
        assertNull(WeighLogic.netTons("", "5"))
        assertNull(WeighLogic.netTons("abc", "5"))
        assertNull(WeighLogic.netTons("3", "5")) // 净 ≤ 0
        assertNull(WeighLogic.netTons("5", "5"))
    }

    @Test
    fun `袋数默认折算 - ceil(net/0_6)`() {
        assertEquals(40, WeighLogic.defaultBagCount(23.56)) // 39.27 → 40
        assertEquals(51, WeighLogic.defaultBagCount(30.5)) // 50.83 → 51
        assertEquals(1, WeighLogic.defaultBagCount(0.1))
        assertEquals(0, WeighLogic.defaultBagCount(null))
        assertEquals(0, WeighLogic.defaultBagCount(0.0))
    }

    @Test
    fun `提交门禁 - 毛皮合法才true`() {
        assertTrue(WeighLogic.canSubmitWeigh("32.18", "8.62"))
        assertFalse(WeighLogic.canSubmitWeigh("32.18", ""))
        assertFalse(WeighLogic.canSubmitWeigh("", "8.62"))
        assertFalse(WeighLogic.canSubmitWeigh("5", "5"))
    }

    // ---- SPP ----

    @Test
    fun `SPP发送 - 指令字节完整落流`() {
        val spp = ByteArraySpp()
        val session = PrintSession(SppFactory { spp })
        val bits = LabelRenderer.render(
            bareCode = "1BCG00010002261020030070012",
            humanLine = "H",
            objectType = "BAG",
            dateText = "2026-10-20",
            seqText = "012",
        )
        val result = session.sendLabel("00:11:22:33:44:55", bits)
        assertTrue(result is PrintSession.PrintSendResult.Sent)
        assertEquals(1, spp.written.size)
        val sent = spp.written[0].toString(Charsets.US_ASCII)
        assertTrue(sent.contains("SIZE 100 mm"))
        assertTrue(sent.contains("PRINT 1"))
        assertEquals(800, TsplPrinter.bitmapRowCount(spp.written[0]))
        assertFalse("发送后应关闭通道", spp.isOpen)
    }

    @Test
    fun `SPP关闭后再写 - 失败有话术`() {
        val spp = ByteArraySpp()
        val session = PrintSession(SppFactory { spp })
        spp.close()
        val result = session.sendLabel("00:11:22:33:44:55", BooleanArray(800 * 800))
        assertTrue(result is PrintSession.PrintSendResult.Failed)
        assertNotNull((result as PrintSession.PrintSendResult.Failed).reason)
    }

    @Test
    fun `工厂抛IO - 映射连接失败`() {
        val session = PrintSession(SppFactory { throw java.io.IOException("refused") })
        val result = session.sendLabel("00:11:22:33:44:55", BooleanArray(800 * 800))
        assertTrue(result is PrintSession.PrintSendResult.Failed)
        assertTrue((result as PrintSession.PrintSendResult.Failed).reason.contains("蓝牙"))
    }
}
