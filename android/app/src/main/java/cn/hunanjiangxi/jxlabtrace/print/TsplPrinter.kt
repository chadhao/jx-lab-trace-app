package cn.hunanjiangxi.jxlabtrace.print

/**
 * TSPL 指令组装（M09 · 纯函数，不发真机，可 JVM 单测）。
 *
 * 主通道定案：`SIZE <w> mm,<h> mm` + `CLS` + `BITMAP` + `PRINT 1`（docs/PRINTER-KM118BT.md）。
 * BITMAP 行数据：每行 [bytesPerRow] 字节（位图宽/8），共 [height] 行；mode 0=覆盖。
 */
object TsplPrinter {

    /** 组装完整打印指令字节。[bits] 为行优先黑白位图（true=黑）。 */
    fun buildCommand(bits: BooleanArray): ByteArray {
        val w = PrinterProfile.MAX_WIDTH_DOTS
        val h = PrinterProfile.MAX_HEIGHT_DOTS
        require(bits.size == w * h) {
            "位图尺寸不符：期望 ${w}x$h=${w * h}，实际 ${bits.size}"
        }
        val bytesPerRow = w / 8
        val header = buildString {
            append("SIZE ${PrinterProfile.SIZE_MM} mm,${PrinterProfile.SIZE_MM} mm\r\n")
            append("DIRECTION 1\r\n")
            append("CLS\r\n")
            append("BITMAP 0,0,$w,$h,0,")
        }.toByteArray(Charsets.US_ASCII)

        val bitmap = packBits(bits, w, h, bytesPerRow)
        val footer = "\r\nPRINT 1\r\n".toByteArray(Charsets.US_ASCII)
        return header + bitmap + footer
    }

    /** 位图打包：每字节 8 像素，高位在左（TSPL 惯例）。 */
    fun packBits(bits: BooleanArray, w: Int, h: Int, bytesPerRow: Int = w / 8): ByteArray {
        val bitmap = ByteArray(bytesPerRow * h)
        for (y in 0 until h) {
            for (byteCol in 0 until bytesPerRow) {
                var b = 0
                for (bit in 0 until 8) {
                    if (bits[y * w + byteCol * 8 + bit]) {
                        b = b or (0x80 shr bit)
                    }
                }
                bitmap[y * bytesPerRow + byteCol] = b.toByte()
            }
        }
        return bitmap
    }

    /**
     * 从指令字节解析 BITMAP 数据段行数（M9-2 断言用）。
     * 找到 `BITMAP 0,0,W,H,0,` 头后的二进制长度 ÷ 每行字节数；找不到返回 -1。
     */
    fun bitmapRowCount(command: ByteArray): Int {
        val prefix = (
            "BITMAP 0,0,${PrinterProfile.MAX_WIDTH_DOTS},${PrinterProfile.MAX_HEIGHT_DOTS},0,"
            ).toByteArray(Charsets.US_ASCII)
        val headAt = indexOf(command, prefix, 0)
        if (headAt < 0) return -1
        val dataStart = headAt + prefix.size
        val printNeedle = "\r\nPRINT".toByteArray(Charsets.US_ASCII)
        val printAt = indexOf(command, printNeedle, dataStart)
        if (printAt < 0) return -1
        val dataLen = printAt - dataStart
        val bytesPerRow = PrinterProfile.MAX_WIDTH_DOTS / 8
        return dataLen / bytesPerRow
    }

    /** 校验指令含 SIZE 与 PRINT 1，且位图行数正确（M9-2 断言辅助）。 */
    fun commandChecks(bits: BooleanArray): CommandChecks {
        val cmd = buildCommand(bits)
        val textHeader = cmd.copyOfRange(0, cmd.size.coerceAtMost(128)).toString(Charsets.US_ASCII)
        return CommandChecks(
            containsSize100 = textHeader.contains("SIZE ${PrinterProfile.SIZE_MM} mm"),
            containsPrint1 = cmd.toString(Charsets.US_ASCII).contains("PRINT 1"),
            bitmapRows = bitmapRowCount(cmd),
        )
    }

    data class CommandChecks(
        val containsSize100: Boolean,
        val containsPrint1: Boolean,
        val bitmapRows: Int,
    )

    private fun indexOf(haystack: ByteArray, needle: ByteArray, from: Int): Int {
        outer@ for (i in from..haystack.size - needle.size) {
            for (j in needle.indices) {
                if (haystack[i + j] != needle[j]) continue@outer
            }
            return i
        }
        return -1
    }
}
