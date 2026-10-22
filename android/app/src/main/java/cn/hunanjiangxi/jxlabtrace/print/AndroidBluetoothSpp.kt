package cn.hunanjiangxi.jxlabtrace.print

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import java.io.IOException
import java.io.OutputStream

/**
 * Android 蓝牙 SPP 实现（M09）：扫描已配对设备 / 连接 / 写流。
 * 权限：API 31+ 需 BLUETOOTH_CONNECT/SCAN；低版本需 BLUETOOTH。
 * 每个入口先 [hasPermission] 运行时校验——lint 的 MissingPermission 无法穿透
 * 自定义守卫方法，故在守卫后的调用点抑制（B5 lint 门禁首跑修正）。
 */
class AndroidBluetoothSpp(private val context: Context) : SppFactory {

    data class BondedItem(val name: String, val address: String)

    private val adapter: BluetoothAdapter?
        get() {
            val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            return manager?.adapter
        }

    fun hasPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Manifest.permission.BLUETOOTH_CONNECT
        } else {
            Manifest.permission.BLUETOOTH
        }
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission") // 入口已 hasPermission() 守卫；单设备 name 访问仍可能 SecurityException（OEM 差异），逐项兜底
    fun bondedDevices(): List<BondedItem> {
        if (!hasPermission()) return emptyList()
        // 真机 Bug-4：部分 OEM 在权限刚授予/撤销窗口期 getBondedDevices 仍抛 SecurityException——整体兜底
        val bonded = runCatching { adapter?.bondedDevices }.getOrNull() ?: return emptyList()
        return bonded.mapNotNull { device ->
            // it.name 在 API31+ 需 CONNECT；个别设备即使已授权仍可能瞬时抛错——逐设备兜底不拖垮整表
            val name = runCatching { device.name }.getOrNull()
            val address = runCatching { device.address }.getOrNull() ?: return@mapNotNull null
            BondedItem(name ?: address, address)
        }
    }

    @SuppressLint("MissingPermission") // 入口已 hasPermission() 守卫
    override fun open(address: String): SppChannel {
        if (!hasPermission()) throw SecurityException("缺少蓝牙权限")
        val ad = adapter ?: throw IOException("设备不支持蓝牙")
        if (!ad.isEnabled) throw IOException("蓝牙未开启")
        val device: BluetoothDevice = ad.getRemoteDevice(address)
        val socket: BluetoothSocket = device.createRfcommSocketToServiceRecord(SppFactory.SPP_UUID)
        try {
            ad.cancelDiscovery()
            socket.connect()
        } catch (e: IOException) {
            runCatching { socket.close() }
            throw e
        }
        return SocketSppChannel(socket)
    }
}

private class SocketSppChannel(private val socket: BluetoothSocket) : SppChannel {
    private val out: OutputStream = socket.outputStream

    override val isOpen: Boolean get() = socket.isConnected

    override fun write(bytes: ByteArray) {
        out.write(bytes)
        out.flush()
    }

    override fun close() {
        runCatching { out.close() }
        runCatching { socket.close() }
    }
}
