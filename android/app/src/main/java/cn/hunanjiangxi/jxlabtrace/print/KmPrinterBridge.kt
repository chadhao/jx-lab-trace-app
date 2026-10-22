package cn.hunanjiangxi.jxlabtrace.print

import io.vin.android.bluetoothprinter.kuaimai.KuaiMaiBluetoothPrinterFactory
import io.vin.android.bluetoothprinterprotocol.BluetoothPrinterManager
import io.vin.android.bluetoothprinterprotocol.IBluetoothPrinterProtocol

/**
 * 快麦 KM-118BT 驱动桥（MIMO-TASK-PRINTER-SDK）：
 * 封装 vinint/Android-BluetoothPrinter 的 BluetoothPrinterManager，
 * 隔离三方 SDK 于 UI/状态机之外。本批只做连接打通（打印绘制下批）。
 *
 * 库要点（源码实读）：
 * - registerPrinter 按型号前缀匹配（connect 时 printerName.startsWith(key)）；
 * - connect(name, addr) 为**同步阻塞**（内部 socket.connect），必须在 IO 线程调用；
 * - printerStatus() 返回 IBluetoothPrinterProtocol.STATUS_* 常量。
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
}
