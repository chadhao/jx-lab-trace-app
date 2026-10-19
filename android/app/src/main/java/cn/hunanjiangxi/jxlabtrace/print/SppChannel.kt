package cn.hunanjiangxi.jxlabtrace.print

import java.io.IOException
import java.io.OutputStream
import java.util.UUID

/**
 * 蓝牙 SPP 通道抽象（M09）：生产实现见 [AndroidBluetoothSpp]；单测用 [ByteArraySpp]。
 * 只负责写字节流，不理解 TSPL 语义。
 */
interface SppChannel {
    val isOpen: Boolean
    fun write(bytes: ByteArray)
    fun close()
}

/** 仅单测：写入内存缓冲。 */
class ByteArraySpp : SppChannel {
    val written = ArrayList<ByteArray>()
    override var isOpen: Boolean = true
        private set

    override fun write(bytes: ByteArray) {
        check(isOpen) { "通道已关闭" }
        written.add(bytes.copyOf())
    }

    override fun close() {
        isOpen = false
    }
}

/**
 * 蓝牙 SPP 连接器（薄封装；真实蓝牙在 Android 侧注入 [SppFactory]）。
 * [SppFactory.open] 抛 IOException ⇒ 连接失败；成功返回已打开通道。
 */
fun interface SppFactory {
    fun open(address: String): SppChannel

    companion object {
        /** SPP 标准 UUID。 */
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }
}

/**
 * 打印会话：连接 → 发送 TSPL 指令 → 关闭。
 * 断连/写失败映射为 [PrintSendResult.Failed]，不抛给 UI 层。
 */
class PrintSession(private val factory: SppFactory) {

    sealed interface PrintSendResult {
        object Sent : PrintSendResult
        data class Failed(val reason: String) : PrintSendResult
    }

    fun sendLabel(address: String, bits: BooleanArray): PrintSendResult {
        val command = TsplPrinter.buildCommand(bits)
        val channel = try {
            factory.open(address)
        } catch (e: IOException) {
            return PrintSendResult.Failed("蓝牙连接失败：${e.message ?: "请检查打印机电源与配对"}")
        } catch (e: SecurityException) {
            return PrintSendResult.Failed("缺少蓝牙权限，请到系统设置开启")
        }
        return try {
            if (!channel.isOpen) {
                return PrintSendResult.Failed("打印机连接已断开 · 去连接")
            }
            channel.write(command)
            PrintSendResult.Sent
        } catch (e: IOException) {
            PrintSendResult.Failed("打印数据发送失败：${e.message ?: "连接中断"}")
        } catch (e: Exception) {
            PrintSendResult.Failed("打印失败：${e.message ?: "未知错误"}")
        } finally {
            runCatching { channel.close() }
        }
    }
}

/** 自检打印（100mm 标尺）。 */
fun PrintSession.sendSelfTest(address: String): PrintSession.PrintSendResult =
    sendLabel(address, LabelRenderer.renderRuler())

/** 供 OutputStream 适配（如反射拿到 SPP 流时）。 */
fun OutputStream.writeAll(bytes: ByteArray) {
    write(bytes)
    flush()
}
