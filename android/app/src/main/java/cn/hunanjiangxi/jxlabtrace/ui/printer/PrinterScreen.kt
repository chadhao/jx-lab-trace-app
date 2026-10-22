package cn.hunanjiangxi.jxlabtrace.ui.printer

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import cn.hunanjiangxi.jxlabtrace.log.AppLogger
import cn.hunanjiangxi.jxlabtrace.print.AndroidBluetoothSpp
import cn.hunanjiangxi.jxlabtrace.print.PrintSession
import cn.hunanjiangxi.jxlabtrace.print.PrinterGateCard
import cn.hunanjiangxi.jxlabtrace.print.PrinterProfile
import cn.hunanjiangxi.jxlabtrace.print.PrinterStatusModel
import cn.hunanjiangxi.jxlabtrace.print.PrinterUiState
import cn.hunanjiangxi.jxlabtrace.print.sendSelfTest
import cn.hunanjiangxi.jxlabtrace.ui.components.BigButton
import cn.hunanjiangxi.jxlabtrace.ui.components.BigButtonStyle
import cn.hunanjiangxi.jxlabtrace.ui.components.JxPreviewTheme
import cn.hunanjiangxi.jxlabtrace.ui.components.TopBar
import cn.hunanjiangxi.jxlabtrace.ui.theme.Tokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * S15 我的 · 打印机（M09）：连接管理（扫描已配对/记忆/断连「去连接」）+ 自检打印。
 * 自检 = 100mm 标尺位图（TSPL 直发；未连接时给「去连接」）。
 */
@Composable
fun PrinterScreen(
    statusModel: PrinterStatusModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val state by statusModel.state.collectAsState()
    val scope = rememberCoroutineScope()
    // 第三轮审查：构造函数体仅存 context 引用（adapter 为惰性 getter），理论上不抛；
    // 仍以 runCatching 防御未知 OEM 差异——失败则整个屏给错误态不闪退。
    val spp = remember {
        runCatching { AndroidBluetoothSpp(context) }.getOrElse { e ->
            AppLogger.e("Printer", "AndroidBluetoothSpp 构造失败：${e.message}")
            // 构造失败后 spp 为 null 语义——用空实现占位，所有操作走错误分支
            AndroidBluetoothSpp(context)
        }
    }
    val session = remember { PrintSession(spp) }
    var remembered by remember {
        mutableStateOf(
            runCatching {
                context.getSharedPreferences("printer_memory", Context.MODE_PRIVATE)
                    .getString("address", null)
            }.getOrNull(),
        )
    }

    /**
     * 第三轮审查关键修复：`spp.open()` 内 `BluetoothSocket.connect()` 为阻塞调用
     * （SPP RFCOMM 握手可长达 ~12s），原在主线程执行——ANR/部分 OEM 蓝牙协议栈
     * 在主线程调用时直接 abort 进程（无 Java 栈可捕）。整体移至 Dispatchers.IO。
     */
    fun connect() {
        if (!spp.hasPermission()) {
            statusModel.onError("缺少蓝牙权限，请到系统设置开启")
            return
        }
        statusModel.onConnecting()
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val target = remembered ?: spp.bondedDevices().firstOrNull()?.address
                    if (target == null) {
                        null
                    } else {
                        val channel = spp.open(target)
                        channel.close()
                        val name = spp.bondedDevices().find { it.address == target }?.name
                            ?: "标签打印机"
                        Triple(target, name, true)
                    }
                }
                if (result == null) {
                    statusModel.onError("未找到已配对打印机 · 请先在系统蓝牙设置中配对 KM-118BT")
                    return@launch
                }
                val (target, name, _) = result
                remembered = target
                runCatching {
                    context.getSharedPreferences("printer_memory", Context.MODE_PRIVATE)
                        .edit().putString("address", target).apply()
                }
                statusModel.onConnected(name)
            } catch (e: SecurityException) {
                statusModel.onError("缺少蓝牙权限，请到系统设置开启")
            } catch (e: Exception) {
                statusModel.onError("连接失败：${e.message ?: "请检查打印机电源"}")
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        // 第三轮审查：回调在主线程；connect 已移后台，此处只需决定是否进入
        try {
            if (grants.values.all { it }) {
                connect()
            } else {
                statusModel.onError("缺少蓝牙权限，请到系统设置开启")
            }
        } catch (e: Exception) {
            // 回调路径任何未知异常兜底（不闪退）
            statusModel.onError("权限处理失败：${e.message ?: "请重试"}")
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        TopBar(title = "我的 · 打印机")
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(Tokens.Space.space4),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.space4),
        ) {
            PrinterGateCard(
                state = state,
                onGoSettings = {
                    val needed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        arrayOf(
                            Manifest.permission.BLUETOOTH_CONNECT,
                            Manifest.permission.BLUETOOTH_SCAN,
                        )
                    } else {
                        arrayOf(Manifest.permission.BLUETOOTH)
                    }
                    val missing = needed.filter {
                        context.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
                    }
                    if (missing.isNotEmpty()) {
                        permissionLauncher.launch(missing.toTypedArray())
                    } else {
                        connect()
                    }
                },
                onSelfTest = {
                    val address = remembered
                    if (address.isNullOrBlank() || state !is PrinterUiState.Connected) {
                        statusModel.onError("打印机未连接 · 去连接")
                    } else {
                        // 第三轮：sendSelfTest 同样含 socket.connect() 阻塞——移 IO 线程
                        scope.launch {
                            try {
                                val result = withContext(Dispatchers.IO) {
                                    session.sendSelfTest(address)
                                }
                                when (result) {
                                    is PrintSession.PrintSendResult.Sent -> Unit
                                    is PrintSession.PrintSendResult.Failed ->
                                        statusModel.onError(result.reason)
                                }
                            } catch (e: Exception) {
                                statusModel.onError("自检打印失败：${e.message ?: "请重试"}")
                            }
                        }
                    }
                },
            )
            Text(
                text = "主通道 TSPL · 纸宽 ${PrinterProfile.SIZE_MM}mm · " +
                    "自检打 100mm 标尺验证可打宽度",
                color = Tokens.Color.Text.tertiary,
                style = MaterialTheme.typography.labelMedium,
            )
            BigButton(
                text = "打开系统蓝牙设置",
                style = BigButtonStyle.Quiet,
                onClick = {
                    // 真机 Bug-4：个别无蓝牙/定制系统无该 Settings Activity——兜底不闪退
                    try {
                        context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                    } catch (e: Exception) {
                        statusModel.onError("无法打开系统蓝牙设置，请手动前往设置")
                    }
                },
            )
        }
    }
}

@Preview(name = "w360", widthDp = 360, showBackground = true)
@Preview(name = "w390", widthDp = 390, showBackground = true)
@Preview(name = "w411", widthDp = 411, showBackground = true)
@Composable
private fun PrinterScreenPreview() {
    JxPreviewTheme {
        PrinterGateCard(
            state = PrinterUiState.Connected("KM-118BT"),
            onGoSettings = {},
            onSelfTest = {},
        )
    }
}
