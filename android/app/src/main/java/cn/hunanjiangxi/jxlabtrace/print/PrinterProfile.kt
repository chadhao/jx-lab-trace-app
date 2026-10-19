package cn.hunanjiangxi.jxlabtrace.print

/**
 * 打印机能力画像（M09 · 换机型只改本文件，不改渲染/指令代码）。
 *
 * 实测依据：docs/PRINTER-KM118BT.md —— KM-118BT · 203dpi=8 dots/mm ·
 * TSPL 主通道 · 实际可打宽度 ≈99mm ⇒ 位图 800×800、内容区 ≤96×96mm、右缘 2mm 禁区。
 *
 * 判据 M9-4：全仓不得散落 203 / 800 魔法数 —— 一律经本对象取值。
 */
object PrinterProfile {

    /** 每毫米点数（203 dpi ≈ 8 dots/mm）。 */
    const val DOTS_PER_MM = 8

    /** 最大可打宽度/高度（dots）。100mm × 8 dots/mm = 800。 */
    const val MAX_WIDTH_DOTS = 100 * DOTS_PER_MM
    const val MAX_HEIGHT_DOTS = 100 * DOTS_PER_MM

    /** 安全边距（dots）：2mm —— 四角与右缘禁区由渲染层强制。 */
    const val SAFE_MARGIN_DOTS = 2 * DOTS_PER_MM

    /** 内容区边长上限（dots）：96mm。 */
    const val CONTENT_MAX_DOTS = 96 * DOTS_PER_MM

    /** 协议主通道（已实测定案）。 */
    const val PROTOCOL = "TSPL"

    /** 标签纸宽（mm），TSPL SIZE 指令用。 */
    const val SIZE_MM = MAX_WIDTH_DOTS / DOTS_PER_MM

    /** 位图总点数（BooleanArray 长度）。 */
    const val PIXEL_COUNT = MAX_WIDTH_DOTS * MAX_HEIGHT_DOTS
}
