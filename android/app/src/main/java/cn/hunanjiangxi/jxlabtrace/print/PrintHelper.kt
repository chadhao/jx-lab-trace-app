package cn.hunanjiangxi.jxlabtrace.print

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.print.PrintAttributes
import android.print.PrintManager

/**
 * 系统打印通道（M09 · 两级出口 D21 的 **B3-a**）：
 * 位图 → Android 系统打印服务（PrintHelper 打开位图）。
 * 蓝牙 TSPL 直发是 B3-b（[PrintSession]），本类不依赖蓝牙。
 */
object PrintHelper {

    /** BooleanArray(true=黑) → Android Bitmap（ARGB_8888，白底黑码）。 */
    fun toBitmap(bits: BooleanArray, width: Int, height: Int): Bitmap {
        require(bits.size == width * height) { "位图尺寸不符" }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height) { i -> if (bits[i]) Color.BLACK else Color.WHITE }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    /** 走系统打印服务打印位图（用户在系统界面选打印机；断连系统打印仍可用 ⇒ B3-a 出口）。 */
    fun printBitmapViaSystem(context: Context, bits: BooleanArray, jobName: String = "袋码标签") {
        val w = PrinterProfile.MAX_WIDTH_DOTS
        val h = PrinterProfile.MAX_HEIGHT_DOTS
        val bitmap = toBitmap(bits, w, h)
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        val adapter = LabelPrintAdapter(context, jobName, bitmap)
        printManager.print(jobName, adapter, PrintAttributes.Builder().build())
    }
}
