package cn.hunanjiangxi.jxlabtrace.print

/**
 * 标签渲染器（M09 · **纯函数**，零 Android 依赖，可 JVM 单测）。
 *
 * 输入袋码字段（人读行 + 对象类型 + 日期 + 序号）→ 输出 [BooleanArray]（true=黑），
 * 尺寸恒为 [PrinterProfile.MAX_WIDTH_DOTS]×[PrinterProfile.MAX_HEIGHT_DOTS]（800×800）。
 *
 * 版式硬约束（docs/PRINTER-KM118BT.md 定案）：
 * - 安全边距 2mm（16 dots）—— 四角安全区恒为白；
 * - 内容区 ≤96×96mm；右缘 2mm 禁区；
 * - QR ≥6 dots/module（本实现 12 dots/module，含 4-module 静区）。
 */
object LabelRenderer {

    /** QR 每模块点数（≥6 的硬下限）。 */
    const val QR_DOTS_PER_MODULE = 12

    /** 渲染标签位图。[bareCode] 为 27 位裸码（QR 载荷）。 */
    fun render(
        bareCode: String,
        humanLine: String,
        objectType: String,
        dateText: String,
        seqText: String,
    ): BooleanArray {
        val w = PrinterProfile.MAX_WIDTH_DOTS
        val h = PrinterProfile.MAX_HEIGHT_DOTS
        val pixels = BooleanArray(w * h)

        // —— QR（顶部居中；静区含在块内）——
        val qr = QrEncoder.encode(bareCode)
        val module = QR_DOTS_PER_MODULE
        val qrCore = QrEncoder.SIZE * module
        val quiet = QrEncoder.QUIET_ZONE * module
        val qrBlock = qrCore + quiet * 2
        val qrLeft = (w - qrBlock) / 2
        val qrTop = PrinterProfile.SAFE_MARGIN_DOTS + 8
        blitQr(pixels, w, qr, qrLeft + quiet, qrTop + quiet, module)

        // —— 人读行（等宽 5×7 字形 ×3 放大，行高 21+4 间距）——
        var textY = qrTop + qrBlock + 12
        textY = drawText(pixels, w, humanLine, center = true, top = textY, scale = 3)

        // —— 对象类型 + 日期 + 序号 ——
        textY = drawText(pixels, w, objectType, center = true, top = textY + 6, scale = 2)
        val meta = listOfNotNull(dateText.ifBlank { null }, seqText.ifBlank { null })
            .joinToString("  ")
        if (meta.isNotBlank()) {
            drawText(pixels, w, meta, center = true, top = textY + 6, scale = 2)
        }

        // —— 强制安全区清白（防越界绘制；含右缘 2mm 禁区）——
        clearSafetyBorder(pixels, w, h, PrinterProfile.SAFE_MARGIN_DOTS)
        return pixels
    }

    /** 自检页：100mm 标尺（与 docs/printer/ruler-100mm.png 同布局意图：满宽刻度可验边缘）。 */
    fun renderRuler(): BooleanArray {
        val w = PrinterProfile.MAX_WIDTH_DOTS
        val h = PrinterProfile.MAX_HEIGHT_DOTS
        val pixels = BooleanArray(w * h)
        val margin = PrinterProfile.SAFE_MARGIN_DOTS
        val content = PrinterProfile.CONTENT_MAX_DOTS
        // 外框
        strokeRect(pixels, w, margin, margin, content, content)
        // 横向刻度：每 8 dots = 1mm 细齿；每 10mm 粗齿；中线贯通
        val baseY = margin + content / 2
        var x = margin
        while (x <= margin + content - 1) {
            val mm = (x - margin) / PrinterProfile.DOTS_PER_MM
            val tick = when {
                mm % 10 == 0 -> content / 4
                else -> content / 12
            }
            strokeLine(pixels, w, x, baseY - tick / 2, x, baseY + tick / 2)
            x += PrinterProfile.DOTS_PER_MM
        }
        strokeLine(pixels, w, margin, baseY, margin + content - 1, baseY)
        // 四角标记（对齐可打边界）
        val mark = PrinterProfile.DOTS_PER_MM * 4
        for (corner in listOf(
            intArrayOf(margin, margin),
            intArrayOf(margin + content - mark, margin),
            intArrayOf(margin, margin + content - mark),
            intArrayOf(margin + content - mark, margin + content - mark),
        )) {
            strokeRect(pixels, w, corner[0], corner[1], mark, mark)
        }
        drawText(pixels, w, "100MM RULER", center = true, top = margin + 16, scale = 3)
        clearSafetyBorder(pixels, w, h, PrinterProfile.SAFE_MARGIN_DOTS)
        return pixels
    }

    // ---- 内部工具 ----

    private fun blitQr(
        pixels: BooleanArray,
        canvasW: Int,
        qr: BooleanArray,
        left: Int,
        top: Int,
        module: Int,
    ) {
        for (my in 0 until QrEncoder.SIZE) {
            for (mx in 0 until QrEncoder.SIZE) {
                if (!QrEncoder.moduleAt(qr, mx, my)) continue
                for (dy in 0 until module) {
                    val y = top + my * module + dy
                    for (dx in 0 until module) {
                        val x = left + mx * module + dx
                        pixels[y * canvasW + x] = true
                    }
                }
            }
        }
    }

    /** 画一行 ASCII 文本；返回下一行的 top。 */
    private fun drawText(
        pixels: BooleanArray,
        canvasW: Int,
        text: String,
        center: Boolean,
        top: Int,
        scale: Int,
    ): Int {
        val charW = MonoFont5x7.WIDTH * scale + scale // 字距 1 模块
        val charH = MonoFont5x7.HEIGHT * scale
        val totalW = text.length * charW - scale
        val left = if (center) (canvasW - totalW) / 2 else PrinterProfile.SAFE_MARGIN_DOTS
        for ((i, ch) in text.withIndex()) {
            val rows = MonoFont5x7.rows(ch)
            for (row in 0 until MonoFont5x7.HEIGHT) {
                val bits = rows[row]
                for (col in 0 until MonoFont5x7.WIDTH) {
                    if ((bits shr (MonoFont5x7.WIDTH - 1 - col)) and 1 == 0) continue
                    val px = left + i * charW + col * scale
                    val py = top + row * scale
                    for (dy in 0 until scale) {
                        for (dx in 0 until scale) {
                            pixels[(py + dy) * canvasW + (px + dx)] = true
                        }
                    }
                }
            }
        }
        return top + charH
    }

    private fun clearSafetyBorder(pixels: BooleanArray, w: Int, h: Int, margin: Int) {
        for (y in 0 until h) {
            for (x in 0 until w) {
                if (x < margin || y < margin || x >= w - margin || y >= h - margin) {
                    pixels[y * w + x] = false
                }
            }
        }
    }

    private fun strokeRect(pixels: BooleanArray, w: Int, left: Int, top: Int, width: Int, height: Int) {
        strokeLine(pixels, w, left, top, left + width - 1, top)
        strokeLine(pixels, w, left, top + height - 1, left + width - 1, top + height - 1)
        strokeLine(pixels, w, left, top, left, top + height - 1)
        strokeLine(pixels, w, left + width - 1, top, left + width - 1, top + height - 1)
    }

    private fun strokeLine(pixels: BooleanArray, w: Int, x0: Int, y0: Int, x1: Int, y1: Int) {
        var x = x0
        var y = y0
        val dx = kotlin.math.abs(x1 - x0)
        val dy = -kotlin.math.abs(y1 - y0)
        val sx = if (x0 < x1) 1 else -1
        val sy = if (y0 < y1) 1 else -1
        var err = dx + dy
        while (true) {
            pixels[y * w + x] = true
            if (x == x1 && y == y1) break
            val e2 = 2 * err
            if (e2 >= dy) {
                err += dy
                x += sx
            }
            if (e2 <= dx) {
                err += dx
                y += sy
            }
        }
    }
}
