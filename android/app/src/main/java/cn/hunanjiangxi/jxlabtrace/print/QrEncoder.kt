package cn.hunanjiangxi.jxlabtrace.print

/**
 * 极简 QR 编码器（M09 · 纯 Kotlin，零 Android 依赖，可 JVM 单测）。
 *
 * 固定口径：**版本 3（25×25）· 字节模式 · ECC M · 掩模自选（惩罚分最低）**。
 * 容量：v3-M 字节模式 42 字节 —— 27 位裸码 + 余量充足；换更长载荷需升版本（见 [QrEncoder.MAX_BYTES]）。
 *
 * 参考：ISO/IEC 18004。实现刻意保持最小闭环：不支持结构化追加/ECI/汉字模式。
 */
object QrEncoder {

    /** 版本 3 的模块数（含功能图形）：v1=21, v2=25, **v3=29**。 */
    const val SIZE = 29

    /** 版本 3 + ECC M 的字节模式容量上限。 */
    const val MAX_BYTES = 42

    /** 静区（quiet zone）模块数 —— 渲染层外扩，编码器只出核心矩阵。 */
    const val QUIET_ZONE = 4

    /** 生成 QR 矩阵；[true]=黑模块。返回 [SIZE]×[SIZE]，行优先。 */
    fun encode(text: String): BooleanArray {
        val bytes = text.toByteArray(Charsets.ISO_8859_1)
        require(bytes.size in 1..MAX_BYTES) {
            "QR 载荷长度 ${bytes.size} 超出版本3-M 容量 1..$MAX_BYTES"
        }
        val codewords = buildCodewords(bytes)
        val modules = BooleanArray(SIZE * SIZE)
        drawFunctionPatterns(modules)
        placeData(modules, codewords)
        val masked = selectMask(modules)
        drawFormatInfo(masked)
        return masked
    }

    fun moduleAt(modules: BooleanArray, x: Int, y: Int): Boolean = modules[y * SIZE + x]

    // ---- 数据编码 ----

    private const val TOTAL_CODEWORDS = 70 // v3 全部码字
    private const val DATA_CODEWORDS = 44 // v3-M 数据码字
    private const val EC_CODEWORDS = 26 // v3-M 纠错码字
    private const val MASK_ID = 7 // 占位；实际由 selectMask 决定，写格式信息时传入

    private fun buildCodewords(data: ByteArray): IntArray {
        // 比特流：模式 0100 + 长度 8bit + 数据 + 终止符 0000 + 补齐到字节 + 填充
        val bits = ArrayList<Int>(TOTAL_CODEWORDS * 8)
        fun push(value: Int, count: Int) {
            for (i in count - 1 downTo 0) bits.add((value shr i) and 1)
        }
        push(0b0100, 4) // byte mode
        push(data.size, 8)
        for (b in data) push(b.toInt() and 0xFF, 8)
        // 终止符：最多 4 个 0
        val remaining = DATA_CODEWORDS * 8 - bits.size
        push(0, minOf(4, remaining.coerceAtLeast(0)))
        // 字节对齐
        while (bits.size % 8 != 0) bits.add(0)
        // 填充字节
        val pad = intArrayOf(0xEC, 0x11)
        var padIndex = 0
        while (bits.size < DATA_CODEWORDS * 8) {
            push(pad[padIndex % 2], 8)
            padIndex++
        }
        val dataCw = IntArray(DATA_CODEWORDS) { i ->
            var v = 0
            for (b in 0 until 8) v = (v shl 1) or bits[i * 8 + b]
            v
        }
        val ec = reedSolomon(dataCw, EC_CODEWORDS)
        return dataCw + ec
    }

    // ---- Reed-Solomon（GF(256)，本原多项式 0x11D）----

    private val EXP = IntArray(512)
    private val LOG = IntArray(256)

    init {
        var x = 1
        for (i in 0 until 255) {
            EXP[i] = x
            LOG[x] = i
            x = x shl 1
            if (x and 0x100 != 0) x = x xor 0x11D
        }
        for (i in 255 until 512) EXP[i] = EXP[i - 255]
    }

    private fun gfMul(a: Int, b: Int): Int {
        if (a == 0 || b == 0) return 0
        return EXP[LOG[a] + LOG[b]]
    }

    private fun reedSolomon(data: IntArray, ecCount: Int): IntArray {
        // 生成多项式
        var gen = intArrayOf(1)
        for (i in 0 until ecCount) {
            val next = IntArray(gen.size + 1)
            for (j in gen.indices) {
                next[j] = next[j] xor gen[j]
                next[j + 1] = next[j + 1] xor gfMul(gen[j], EXP[i])
            }
            gen = next
        }
        val remainder = IntArray(ecCount)
        for (d in data) {
            val factor = d xor remainder[0]
            System.arraycopy(remainder, 1, remainder, 0, ecCount - 1)
            remainder[ecCount - 1] = 0
            for (i in remainder.indices) {
                remainder[i] = remainder[i] xor gfMul(gen[i + 1], factor)
            }
        }
        return remainder
    }

    // ---- 矩阵 ----

    private fun idx(x: Int, y: Int) = y * SIZE + x

    private fun drawFunctionPatterns(m: BooleanArray) {
        // 定位图案 + 分隔带
        drawFinder(m, 0, 0)
        drawFinder(m, SIZE - 7, 0)
        drawFinder(m, 0, SIZE - 7)
        // 定时图案
        for (i in 8 until SIZE - 8) {
            val dark = i % 2 == 0
            m[idx(i, 6)] = dark
            m[idx(6, i)] = dark
        }
        // 校正图案（v3：中心 (22,22)）
        drawAlignment(m, 22, 22)
        // 暗模块：(8, 4*version+9) = (8, 21)
        m[idx(8, SIZE - 8)] = true
    }

    private fun drawFinder(m: BooleanArray, left: Int, top: Int) {
        for (dy in -1..7) {
            for (dx in -1..7) {
                val x = left + dx
                val y = top + dy
                if (x !in 0 until SIZE || y !in 0 until SIZE) continue
                val inOuter = dx in 0..6 && dy in 0..6
                val ring = dx == 0 || dx == 6 || dy == 0 || dy == 6
                val core = dx in 2..4 && dy in 2..4
                m[idx(x, y)] = inOuter && (ring || core)
            }
        }
    }

    private fun drawAlignment(m: BooleanArray, cx: Int, cy: Int) {
        for (dy in -2..2) {
            for (dx in -2..2) {
                val ring = kotlin.math.max(kotlin.math.abs(dx), kotlin.math.abs(dy)) != 1
                m[idx(cx + dx, cy + dy)] = ring
            }
        }
    }

    /** 功能模块掩码：true=该位是功能模块（数据不得覆盖）。 */
    private fun functionMask(): BooleanArray {
        val f = BooleanArray(SIZE * SIZE)
        fun markRect(left: Int, top: Int, w: Int, h: Int) {
            for (y in top until top + h) {
                for (x in left until left + w) {
                    if (x in 0 until SIZE && y in 0 until SIZE) f[idx(x, y)] = true
                }
            }
        }
        // 定位+分隔（8×8 角块）
        markRect(0, 0, 9, 9)
        markRect(SIZE - 8, 0, 8, 9)
        markRect(0, SIZE - 8, 9, 8)
        // 定时
        for (i in 0 until SIZE) {
            f[idx(i, 6)] = true
            f[idx(6, i)] = true
        }
        // 校正 (22,22) 5×5
        markRect(20, 20, 5, 5)
        // 格式信息（绕左上定位两条带）+ 暗模块
        for (i in 0..8) {
            f[idx(i, 8)] = true
            f[idx(8, i)] = true
        }
        for (i in 0..7) {
            f[idx(SIZE - 1 - i, 8)] = true
            f[idx(8, SIZE - 1 - i)] = true
        }
        return f
    }

    private fun placeData(m: BooleanArray, codewords: IntArray) {
        val functions = functionMask()
        val bits = ArrayList<Int>(codewords.size * 8)
        for (cw in codewords) {
            for (i in 7 downTo 0) bits.add((cw shr i) and 1)
        }
        var bitIndex = 0
        var upward = true
        var col = SIZE - 1
        while (col > 0) {
            if (col == 6) col-- // 跳过定时列
            val rows = if (upward) (SIZE - 1 downTo 0) else (0 until SIZE)
            for (row in rows) {
                for (c in intArrayOf(col, col - 1)) {
                    if (functions[idx(c, row)]) continue
                    val dark = if (bitIndex < bits.size) bits[bitIndex] == 1 else false
                    bitIndex++
                    m[idx(c, row)] = dark
                }
            }
            upward = !upward
            col -= 2
        }
    }

    // ---- 掩模 ----

    private fun maskBit(mask: Int, x: Int, y: Int): Boolean = when (mask) {
        0 -> (x + y) % 2 == 0
        1 -> y % 2 == 0
        2 -> x % 3 == 0
        3 -> (x + y) % 3 == 0
        4 -> (x / 3 + y / 2) % 2 == 0
        5 -> (x * y) % 2 + (x * y) % 3 == 0
        6 -> ((x * y) % 2 + (x * y) % 3) % 2 == 0
        7 -> ((x + y) % 2 + (x * y) % 3) % 2 == 0
        else -> false
    }

    private fun selectMask(base: BooleanArray): BooleanArray {
        val functions = functionMask()
        var best: BooleanArray? = null
        var bestPenalty = Int.MAX_VALUE
        for (mask in 0..7) {
            val candidate = base.copyOf()
            for (y in 0 until SIZE) {
                for (x in 0 until SIZE) {
                    if (functions[idx(x, y)]) continue
                    if (maskBit(mask, x, y)) candidate[idx(x, y)] = !candidate[idx(x, y)]
                }
            }
            // 写入该掩模的格式信息再评惩罚
            drawFormatInfo(candidate, mask)
            val penalty = penalty(candidate)
            if (penalty < bestPenalty) {
                bestPenalty = penalty
                best = candidate
            }
        }
        return requireNotNull(best)
    }

    /** 格式信息：ECC M(00) + 掩模 3bit + BCH(15,5) + 掩码 0x5412。 */
    private fun drawFormatInfo(m: BooleanArray, mask: Int = 0) {
        val data = (0b00 shl 3) or mask // ECC M
        var rem = data
        for (i in 0 until 10) rem = (rem shl 1) xor ((rem ushr 9) * 0x537)
        val bits = ((data shl 10) or rem) xor 0x5412
        fun bit(i: Int) = ((bits shr i) and 1) == 1
        // 左上环绕
        for (i in 0..5) m[idx(8, i)] = bit(i)
        m[idx(8, 7)] = bit(6)
        m[idx(8, 8)] = bit(7)
        m[idx(7, 8)] = bit(8)
        for (i in 9..14) m[idx(14 - i, 8)] = bit(i)
        // 右上 + 左下
        for (i in 0..7) m[idx(SIZE - 1 - i, 8)] = bit(i)
        for (i in 8..14) m[idx(8, SIZE - 15 + i)] = bit(i)
        m[idx(8, SIZE - 8)] = true // 暗模块恒黑
    }

    private fun penalty(m: BooleanArray): Int {
        var score = 0
        // 规则1：同色连续 ≥5
        fun runs(line: List<Boolean>): Int {
            var s = 0
            var run = 1
            for (i in 1 until line.size) {
                if (line[i] == line[i - 1]) {
                    run++
                    if (run == 5) s += 3 else if (run > 5) s += 1
                } else run = 1
            }
            return s
        }
        for (y in 0 until SIZE) score += runs((0 until SIZE).map { m[idx(it, y)] })
        for (x in 0 until SIZE) score += runs((0 until SIZE).map { m[idx(x, it)] })
        // 规则2：2×2 同色
        for (y in 0 until SIZE - 1) {
            for (x in 0 until SIZE - 1) {
                val v = m[idx(x, y)]
                if (v == m[idx(x + 1, y)] && v == m[idx(x, y + 1)] && v == m[idx(x + 1, y + 1)]) {
                    score += 3
                }
            }
        }
        return score
    }

    @Suppress("unused")
    private val MASK_ID_KEEP = MASK_ID
}
