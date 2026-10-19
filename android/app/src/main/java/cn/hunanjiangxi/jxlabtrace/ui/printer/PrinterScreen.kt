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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
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
    val spp = remember { AndroidBluetoothSpp(context) }
    val session = remember { PrintSession(spp) }
    var remembered by remember {
        mutableStateOf(
            context.getSharedPreferences("printer_memory", Context.MODE_PRIVATE)
                .getString("address", null),
        )
    }

    fun connect() {
        statusModel.onConnecting()
        val target = remembered ?: spp.bondedDevices().firstOrNull()?.address
        if (target == null) {
            statusModel.onError("未找到已配对打印机 · 请先在系统蓝牙设置中配对 KM-118BT")
            return
        }
        try {
            val channel = spp.open(target)
            channel.close()
            remembered = target
            context.getSharedPreferences("printer_memory", Context.MODE_PRIVATE)
                .edit().putString("address", target).apply()
            val name = spp.bondedDevices().find { it.address == target }?.name ?: "标签打印机"
            statusModel.onConnected(name)
        } catch (e: SecurityException) {
            statusModel.onError("缺少蓝牙权限，请到系统设置开启")
        } catch (e: Exception) {
            statusModel.onError("连接失败：${e.message ?: "请检查打印机电源"}")
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        if (grants.values.all { it }) {
            connect()
        } else {
            statusModel.onError("缺少蓝牙权限，请到系统设置开启")
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
                        when (val result = session.sendSelfTest(address)) {
                            is PrintSession.PrintSendResult.Sent -> Unit
                            is PrintSession.PrintSendResult.Failed ->
                                statusModel.onError(result.reason)
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
                onClick = { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) },
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
