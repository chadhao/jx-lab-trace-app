package cn.hunanjiangxi.jxlabtrace.print

/**
 * 打印机连接状态机（M09 · 纯逻辑，可 JVM 单测）。
 * 扫描/配对/记忆/断连提示「去连接」——UI 按 [PrinterUiState] 渲染，不直接碰蓝牙 API。
 */
sealed interface PrinterUiState {
    /** 未连接：打印入口显示「去连接」状态卡（判据 M9-3）。 */
    object Disconnected : PrinterUiState

    object Connecting : PrinterUiState

    data class Connected(val deviceName: String, val protocol: String = PrinterProfile.PROTOCOL) : PrinterUiState

    data class Error(val reason: String) : PrinterUiState
}

/** 连接事件 → UI 状态纯映射（单测入口）。 */
fun printerUiStateOf(
    connected: Boolean,
    connecting: Boolean = false,
    deviceName: String = "",
    lastError: String? = null,
): PrinterUiState = when {
    lastError != null -> PrinterUiState.Error(lastError)
    connecting -> PrinterUiState.Connecting
    connected -> PrinterUiState.Connected(deviceName.ifBlank { "标签打印机" })
    else -> PrinterUiState.Disconnected
}

/** 打印入口是否可点（断连时入口仍在，但动作=引导去连接而非直接打印失败）。 */
fun printEntryEnabled(state: PrinterUiState): Boolean = state is PrinterUiState.Connected

/** 断连提示文案（「去连接」出路）。 */
fun printerDisconnectedHint(state: PrinterUiState): String? =
    if (state is PrinterUiState.Disconnected) "打印机未连接 · 去连接" else null

/** 已记忆设备（蓝牙地址）本地存储抽象。 */
interface PrinterMemory {
    fun savedAddress(): String?
    fun saveAddress(address: String)
    fun clear()
}

/** 仅供单测/预览。 */
class InMemoryPrinterMemory : PrinterMemory {
    private var address: String? = null
    override fun savedAddress(): String? = address
    override fun saveAddress(address: String) {
        this.address = address
    }
    override fun clear() {
        address = null
    }
}
