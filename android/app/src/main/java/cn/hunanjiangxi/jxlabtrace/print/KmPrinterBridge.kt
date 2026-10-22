package cn.hunanjiangxi.jxlabtrace.print

import io.vin.android.bluetoothprinter.kuaimai.KuaiMaiBluetoothPrinterFactory
import io.vin.android.bluetoothprinterprotocol.BluetoothPrinterManager
import io.vin.android.bluetoothprinterprotocol.IBluetoothPrinterProtocol
import io.vin.android.bluetoothprinterprotocol.PrintCallback
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 快麦 KM-118BT 驱动桥（MIMO-TASK-PRINTER-SDK + 自检打印追加）：
 * 封装 vinint/Android-BluetoothPrinter 的 BluetoothPrinterManager，
 * 隔离三方 SDK 于 UI/状态机之外。
 *
 * 库要点（源码实读）：
 * - registerPrinter 按型号前缀匹配（connect 时 printerName.startsWith(key)）；
 * - connect(name, addr) 为**同步阻塞**（内部 socket.connect），必须在 IO 线程调用；
 * - printerStatus() 返回 IBluetoothPrinterProtocol.STATUS_* 常量；
 * - printAndFeed(callback) 异步出纸，回调 onPrintSuccess/onPrintFail(code)。
 */
object KmPrinterBridge {

    /** 注册型号（与设备名前缀匹配；KM-118BT 与库示例 KM-118B 同族）。 */
    const val MODEL = "KM-118BT"

    /** 库模板类型：1 = 快麦（README 示例口径）。 */
    private const val TYPE_KUAIMAI = 1

    @Volatile
    private var registered = false

    /** 幂等注册快麦驱动（首次调用生效；重复调用无副作用）。 */
    fun ensureRegistered() {
        if (registered) return
        synchronized(this) {
            if (registered) return
            BluetoothPrinterManager.getInstance()
                .registerPrinter(MODEL, KuaiMaiBluetoothPrinterFactory(MODEL), TYPE_KUAIMAI)
            registered = true
        }
    }

    /**
     * 连接（同步阻塞——调用方必须已在 IO 线程）。
     * 返回 true=连接成功；false=不支持的型号或连接失败。
     */
    fun connect(printerName: String, printerAddr: String): Boolean {
        ensureRegistered()
        return try {
            BluetoothPrinterManager.getInstance().connect(printerName, printerAddr)
        } catch (_: Exception) {
            false
        }
    }

    /** 当前打印机状态（STATUS_DISCONNECT=断开，其余见 IBluetoothPrinterProtocol）。 */
    fun status(): Int = try {
        BluetoothPrinterManager.getInstance().printerStatus()
    } catch (_: Exception) {
        IBluetoothPrinterProtocol.STATUS_DISCONNECT
    }

    fun isConnected(): Boolean =
        status() != IBluetoothPrinterProtocol.STATUS_DISCONNECT

    fun disconnect() {
        try {
            BluetoothPrinterManager.getInstance().disconnect()
        } catch (_: Exception) {
            // 断开失败不抛——状态机自行回落
        }
    }

    fun printerName(): String = try {
        BluetoothPrinterManager.getInstance().printerName
    } catch (_: Exception) {
        ""
    }

    /** 状态码 → 中文短语（UI 直显用；未知码原样返回数字）。 */
    fun statusLabel(code: Int): String = when (code) {
        IBluetoothPrinterProtocol.STATUS_DISCONNECT -> "未连接"
        IBluetoothPrinterProtocol.STATUS_OK -> "正常"
        else -> "状态码 $code"
    }

    // ===== 自检打印（追加任务 2026-10-22 22:05）=====

    /**
     * 自检标签内容（纯数据——单测断言入口，不依赖 SDK/Android）。
     * 坐标单位 dots（203dpi = 8 dots/mm，见 PrinterProfile）。
     */
    data class SelfTestContent(
        /** 文本行（标题/时间/版本/纸宽，按 y 升序绘制）。 */
        val lines: List<String>,
        /** 二维码内容（时间戳字符串，验证清晰度）。 */
        val qrContent: String,
        /** CODE128 条码内容（数字串）。 */
        val barcodeContent: String,
        val pageWidthDots: Int,
        val pageHeightDots: Int,
    ) {
        /** 供单测：是否包含二维码与条码内容（调用方据此外配 drawQRCode/drawBarCode）。 */
        fun describe(): String =
            "page=${pageWidthDots}x$pageHeightDots lines=${lines.size} " +
                "qr=$qrContent barcode=$barcodeContent"
    }

    /** 自检页高度（dots）：50mm——一张紧凑测试标签。 */
    const val SELF_TEST_HEIGHT_DOTS = 50 * PrinterProfile.DOTS_PER_MM

    /**
     * 构造自检标签内容（纯函数，可 JVM 单测）：
     * - 四行文本：标题 / 时间（yyyy-MM-dd HH:mm:ss）/ 版本 / 纸宽·型号；
     * - 二维码内容 = 格式化时间串；条码内容 = 日期数字（CODE128 友好）。
     */
    fun buildSelfTestContent(
        versionName: String,
        timeMillis: Long,
    ): SelfTestContent {
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(Date(timeMillis))
        val dateDigits = SimpleDateFormat("yyyyMMddHHmmss", Locale.CHINA).format(Date(timeMillis))
        return SelfTestContent(
            lines = listOf(
                "江熙追溯 · 打印自检",
                "时间：$stamp",
                "版本：$versionName",
                "纸宽 ${PrinterProfile.SIZE_MM}mm · 快麦 $MODEL",
            ),
            qrContent = stamp,
            barcodeContent = dateDigits,
            pageWidthDots = PrinterProfile.MAX_WIDTH_DOTS,
            pageHeightDots = SELF_TEST_HEIGHT_DOTS,
        )
    }

    /** printAndFeed 失败码 → 友好文案（纯函数）。 */
    fun printFailMessage(code: Int): String = when (code) {
        0 -> "打印失败（未知原因）"
        else -> "打印失败（错误码 $code）· 请检查纸张与电量"
    }

    /**
     * 打印自检标签（连接状态下）：
     * setPage → 四行文本 → 二维码 → CODE128 条码 → printAndFeed 出纸。
     * **调用方必须已在 IO 线程**（绘制+出纸均阻塞）；全路径 try-catch，
     * 失败经 [onFail] 上抛（含异常 message 与 printAndFeed 错误码）。
     *
     * @param onSuccess printAndFeed 成功回调（可能来自库内部线程）
     * @param onFail    绘制异常或 printAndFeed 失败回调
     */
    fun printSelfTest(
        versionName: String,
        timeMillis: Long = System.currentTimeMillis(),
        onSuccess: () -> Unit,
        onFail: (message: String) -> Unit,
    ) {
        try {
            val protocol = BluetoothPrinterManager.getInstance().bluetoothPrinterProtocol
                ?: return onFail("打印机协议未就绪 · 请先连接")
            if (status() == IBluetoothPrinterProtocol.STATUS_DISCONNECT) {
                return onFail("打印机未连接 · 去连接")
            }
            val content = buildSelfTestContent(versionName, timeMillis)
            val dpm = PrinterProfile.DOTS_PER_MM
            val margin = PrinterProfile.SAFE_MARGIN_DOTS

            protocol.setPage(content.pageWidthDots, content.pageHeightDots, IBluetoothPrinterProtocol.ORIENTATION_DEFAULT)

            // 四行文本（行距 5mm = 40 dots；字号 24 ≈ 3mm 高）
            content.lines.forEachIndexed { i, line ->
                protocol.drawText(
                    margin,
                    margin + i * 5 * dpm,
                    0, 0,
                    line,
                    IBluetoothPrinterProtocol.FONT_SIZE_24,
                    IBluetoothPrinterProtocol.STYLE_TEXT_NO,
                    IBluetoothPrinterProtocol.COLOR_BLACK,
                    IBluetoothPrinterProtocol.STYLE_ROTATION_0,
                )
            }

            // 二维码（内容=时间戳串；单位宽 6 · 纠错 H）——放在文本下方 5mm
            val qrY = margin + content.lines.size * 5 * dpm + 2 * dpm
            protocol.drawQRCode(
                margin,
                qrY,
                content.qrContent,
                IBluetoothPrinterProtocol.QRCODE_UNIT_WIDTH_6,
                IBluetoothPrinterProtocol.QRCODE_CORRECTION_LEVEL_H,
                IBluetoothPrinterProtocol.STYLE_ROTATION_0,
            )

            // CODE128 条码（内容=日期数字）——二维码下方留 4mm
            val barcodeY = qrY + 20 * dpm + 4 * dpm
            protocol.drawBarCode(
                margin,
                barcodeY,
                10 * dpm, // 条码高 10mm
                2,        // 线宽 2 dots
                content.barcodeContent,
                IBluetoothPrinterProtocol.STYLE_BARCODE_CODE128,
                IBluetoothPrinterProtocol.STYLE_ROTATION_0,
            )

            // 出纸（异步回调）
            protocol.printAndFeed(object : PrintCallback {
                override fun onPrintSuccess() {
                    onSuccess()
                }

                override fun onPrintFail(code: Int) {
                    onFail(printFailMessage(code))
                }
            })
        } catch (e: SecurityException) {
            onFail("缺少蓝牙权限，请到系统设置开启")
        } catch (e: Exception) {
            onFail("自检打印异常：${e.message ?: e.javaClass.simpleName}")
        }
    }
}
