package cn.hunanjiangxi.jxlabtrace.scan

/**
 * HID 扫码枪拼接器（M04）：扫码枪＝键盘，按键流缓冲到 Enter 结束。
 *
 * 规则（docs/MODULES M04 判据 3）：
 * - 只接收字母表字符 [0-9A-Za-z]（小写转大写）；空格/标点/控制键一律过滤（[M4-3] 非数字字符过滤）；
 * - 分片输入：字符间间隔超过 [timeoutMs] 视为新一轮，旧缓冲清空（超时清空）；
 * - Enter 提交时：空/超时/长度≠27/校验失败 ⇒ [HidOutcome.Rejected] 带明确原因，
 *   **不静默丢弃**（红边+原因由取景层展示）。
 */
class HidCodeAssembler(
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
) {

    sealed interface Outcome {
        /** 校验通过的完整 27 位码。 */
        data class Accepted(val raw: String) : Outcome

        /** 被拒绝的输入，[reason] 为可直接展示的中文原因。 */
        data class Rejected(val reason: String) : Outcome
    }

    private val buffer = StringBuilder()
    private var lastFeedAt: Long = Long.MIN_VALUE / 2

    /** 当前缓冲（调试/测试可见）。 */
    val pending: String get() = buffer.toString()

    /** 输入一个按键字符；[at] 为单调时钟毫秒（单测注入时间）。 */
    fun feed(ch: Char, at: Long) {
        expireIfStale(at)
        lastFeedAt = at
        if (ch in '0'..'9' || ch in 'a'..'z' || ch in 'A'..'Z') {
            buffer.append(ch.uppercaseChar())
        }
        // 其余字符（空格、标点、控制字符）过滤掉，但不打断本轮拼接
    }

    /** 批量喂入（便利入口）。 */
    fun feedAll(text: CharSequence, at: Long) {
        text.forEach { feed(it, at) }
    }

    /** Enter：提交校验。 */
    fun submit(at: Long): Outcome {
        expireIfStale(at)
        if (buffer.isEmpty()) {
            return Outcome.Rejected("未收到扫码内容（扫码枪输入为空）")
        }
        val raw = buffer.toString()
        buffer.setLength(0)
        if (raw.length != CodeParser.TOTAL_LENGTH) {
            return Outcome.Rejected("收到 ${raw.length} 位，不是 27 位码，请重扫")
        }
        return when (val result = CodeParser.parse(raw)) {
            is CodeParser.Result.Ok -> Outcome.Accepted(result.code.full)
            is CodeParser.Result.Rejected ->
                Outcome.Rejected(
                    when (result.reject) {
                        CodeParser.Reject.CHECKSUM -> "校验位不匹配，码可能读错，请重扫"
                        else -> result.reject.message
                    },
                )
        }
    }

    /** 清空缓冲（新一轮/取消）。 */
    fun clear() {
        buffer.setLength(0)
        lastFeedAt = Long.MIN_VALUE / 2
    }

    private fun expireIfStale(at: Long) {
        if (buffer.isNotEmpty() && at - lastFeedAt > timeoutMs) {
            buffer.setLength(0)
        }
    }

    companion object {
        const val DEFAULT_TIMEOUT_MS = 500L
    }
}
