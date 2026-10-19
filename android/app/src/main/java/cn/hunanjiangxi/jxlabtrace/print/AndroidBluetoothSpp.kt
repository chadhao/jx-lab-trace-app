package cn.hunanjiangxi.jxlabtrace.print

import android.Manifest
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

    fun bondedDevices(): List<BondedItem> {
        if (!hasPermission()) return emptyList()
        val bonded = adapter?.bondedDevices ?: return emptyList()
        return bonded.map { BondedItem(it.name ?: it.address, it.address) }
    }

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
