package cn.hunanjiangxi.jxlabtrace

import cn.hunanjiangxi.jxlabtrace.scan.HidCodeAssembler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M4-3：HID 拼接器单测 —— 模拟按键流：
 * 分片输入 / 超时清空 / 非字母表字符过滤 / 长度≠27 与校验失败给原因（不静默丢弃）。
 */
class HidCodeAssemblerTest {

    private val fullCode = "1BCG00010002261007030070007"
    private val t0 = 1_000L

    @Test
    fun `分片输入 - 按键流拼满 27 位后 Enter 提交成功`() {
        val hid = HidCodeAssembler()
        // 拆成三片（扫码枪按正常速度连续击键）
        hid.feedAll(fullCode.substring(0, 9), t0)
        hid.feedAll(fullCode.substring(9, 20), t0 + 50)
        hid.feedAll(fullCode.substring(20), t0 + 100)

        val outcome = hid.submit(t0 + 150)
        assertTrue("实际 $outcome", outcome is HidCodeAssembler.Outcome.Accepted)
        assertEquals(fullCode, (outcome as HidCodeAssembler.Outcome.Accepted).raw)
        // 提交后缓冲清空
        assertEquals("", hid.pending)
    }

    @Test
    fun `超时清空 - 间隔过久的旧缓冲被丢弃且给明确原因`() {
        val hid = HidCodeAssembler(timeoutMs = 500)
        hid.feedAll("1BCG000", t0) // 半截旧码
        // 超过 500ms 后扫码枪再输入新的一轮（先喂完整码的开头）
        hid.feedAll(fullCode, t0 + 600)

        // 旧缓冲已被清掉，本轮 = 完整码 ⇒ 成功
        val outcome = hid.submit(t0 + 700)
        assertTrue("新一轮完整码应成功，实际 $outcome",
            outcome is HidCodeAssembler.Outcome.Accepted)
        assertEquals(fullCode, (outcome as HidCodeAssembler.Outcome.Accepted).raw)
    }

    @Test
    fun `超时后直接 Enter - 拒绝且原因非空（不静默丢弃）`() {
        val hid = HidCodeAssembler(timeoutMs = 500)
        hid.feedAll("1BCG000", t0)
        val outcome = hid.submit(t0 + 5_000)
        assertTrue("超时空缓冲应拒绝，实际 $outcome",
            outcome is HidCodeAssembler.Outcome.Rejected)
        val reason = (outcome as HidCodeAssembler.Outcome.Rejected).reason
        assertTrue("必须给出可展示的原因，实际：$reason", reason.isNotBlank())
    }

    @Test
    fun `非字母表字符过滤 - 空格标点控制字符被滤掉且不打断拼接`() {
        val hid = HidCodeAssembler()
        // 扫码枪夹带的空格、标点、不可见字符
        val noisy = fullCode.mapIndexedNotNull { i, c ->
            when (i % 5) {
                0 -> listOf(c, ' ')
                1 -> listOf('-', c, '!')
                2 -> listOf('\t', c)
                3 -> listOf(c, '.')
                else -> listOf(c)
            }.joinToString("")
        }.joinToString("")
        hid.feedAll(noisy, t0)

        assertEquals("过滤后应恰为 27 位字母表字符", fullCode, hid.pending)

        // 小写输入也被归一到大写（字母表口径）
        val hid2 = HidCodeAssembler()
        hid2.feedAll(fullCode.lowercase(), t0)
        assertEquals(fullCode, hid2.pending)
    }

    @Test
    fun `长度不足 27 - Enter 拒绝并说明位数`() {
        val hid = HidCodeAssembler()
        hid.feedAll("1BCG000100022610070300700", t0) // 26 位
        val outcome = hid.submit(t0 + 10)
        assertTrue(outcome is HidCodeAssembler.Outcome.Rejected)
        val reason = (outcome as HidCodeAssembler.Outcome.Rejected).reason
        assertTrue("原因应说明位数问题，实际：$reason", reason.contains("27"))
    }

    @Test
    fun `校验失败 - Enter 拒绝并提示重扫`() {
        val hid = HidCodeAssembler()
        val bad = fullCode.dropLast(1) + 'X' // 破坏校验位（X 也不等于原校验位 7）
        hid.feedAll(bad, t0)
        val outcome = hid.submit(t0 + 10)
        assertTrue("坏校验应拒绝，实际 $outcome",
            outcome is HidCodeAssembler.Outcome.Rejected)
        assertTrue((outcome as HidCodeAssembler.Outcome.Rejected).reason.contains("校验位"))
    }

    @Test
    fun `空缓冲直接 Enter - 拒绝而非静默`() {
        val hid = HidCodeAssembler()
        val outcome = hid.submit(t0)
        assertTrue(outcome is HidCodeAssembler.Outcome.Rejected)
        assertTrue((outcome as HidCodeAssembler.Outcome.Rejected).reason.isNotBlank())
    }

    @Test
    fun `clear - 清空后缓冲为空`() {
        val hid = HidCodeAssembler()
        hid.feedAll("123", t0)
        hid.clear()
        assertEquals("", hid.pending)
    }
}
