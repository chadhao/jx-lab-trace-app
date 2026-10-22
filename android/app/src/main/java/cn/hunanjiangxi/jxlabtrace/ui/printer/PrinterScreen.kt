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
import cn.hunanjiangxi.jxlabtrace.BuildConfig
import cn.hunanjiangxi.jxlabtrace.auth.LoginDiag
import cn.hunanjiangxi.jxlabtrace.log.AppLogger
import cn.hunanjiangxi.jxlabtrace.log.CrashLogWriter
import cn.hunanjiangxi.jxlabtrace.print.AndroidBluetoothSpp
import cn.hunanjiangxi.jxlabtrace.print.KmPrinterBridge
import cn.hunanjiangxi.jxlabtrace.print.PrinterGateCard
import cn.hunanjiangxi.jxlabtrace.print.PrinterProfile
import cn.hunanjiangxi.jxlabtrace.print.PrinterStatusModel
import cn.hunanjiangxi.jxlabtrace.print.PrinterUiState
import cn.hunanjiangxi.jxlabtrace.ui.components.BigButton
import cn.hunanjiangxi.jxlabtrace.ui.components.BigButtonStyle
import cn.hunanjiangxi.jxlabtrace.ui.components.JxPreviewTheme
import cn.hunanjiangxi.jxlabtrace.ui.components.TopBar
import cn.hunanjiangxi.jxlabtrace.ui.theme.Tokens
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * S15 我的 · 打印机（MIMO-TASK-PRINTER-SDK）：
 * 默认连接走快麦官方驱动库（KmPrinterBridge → BluetoothPrinterManager）；
 * 自研 SPP（AndroidBluetoothSpp/PrintSession）代码保留但**默认不启用**（回退开关 [useLegacySpp]）。
 * 本批只做连接打通 + 状态读取；标签绘制（drawText/drawQRCode）下批。
 */
@Composable
fun PrinterScreen(
    statusModel: PrinterStatusModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val state by statusModel.state.collectAsState()
    val scope = rememberCoroutineScope()

    // 自研回退开关：默认 false（SDK 为主路径）；真机 SDK 仍失败时可翻 true 走旧 SPP。
    var useLegacySpp by remember { mutableStateOf(false) }

    // 自研 SPP 仍构造（回退路径用）；SDK 路径仅用其 bondedDevices() 读已配对列表。
    val spp = remember {
        runCatching { AndroidBluetoothSpp(context) }.getOrElse { e ->
            AppLogger.e("Printer", "AndroidBluetoothSpp 构造失败：${e.message}")
            AndroidBluetoothSpp(context)
        }
    }
    var remembered by remember {
        mutableStateOf(
            runCatching {
                context.getSharedPreferences("printer_memory", Context.MODE_PRIVATE)
                    .getString("address", null)
            }.getOrNull(),
        )
    }

    /** SDK 路径连接（默认）：读已配对 → BluetoothPrinterManager.connect（IO 线程）。 */
    fun connectViaSdk() {
        if (!spp.hasPermission()) {
            statusModel.onError("缺少蓝牙权限，请到系统设置开启")
            return
        }
        statusModel.onConnecting()
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val bonded = spp.bondedDevices()
                    // 优先记忆地址；否则取名字以 KM-118BT 开头的已配对设备
                    val match = bonded.find { it.address == remembered }
                        ?: bonded.find { it.name.startsWith(KmPrinterBridge.MODEL) }
                        ?: bonded.firstOrNull()
                    if (match == null) {
                        null
                    } else {
                        val ok = KmPrinterBridge.connect(match.name, match.address)
                        Triple(match.address, match.name, ok)
                    }
                }
                if (result == null) {
                    statusModel.onError("未找到已配对打印机 · 请先在系统蓝牙设置中配对 ${KmPrinterBridge.MODEL}")
                    return@launch
                }
                val (addr, name, ok) = result
                if (!ok) {
                    statusModel.onError("连接失败 · 请检查打印机电源与配对状态（型号前缀 ${KmPrinterBridge.MODEL}）")
                    return@launch
                }
                remembered = addr
                runCatching {
                    context.getSharedPreferences("printer_memory", Context.MODE_PRIVATE)
                        .edit().putString("address", addr).apply()
                }
                // 连接后读一次状态（库 printerStatus 可能阻塞——已在 IO 线程外，用缓存口径）
                val st = withContext(Dispatchers.IO) { KmPrinterBridge.status() }
                AppLogger.i("Printer", "SDK 连接成功 name=$name addr=$addr status=${KmPrinterBridge.statusLabel(st)}")
                statusModel.onConnected(name)
            } catch (e: SecurityException) {
                statusModel.onError("缺少蓝牙权限，请到系统设置开启")
            } catch (e: Exception) {
                AppLogger.e("Printer", "SDK 连接异常：${e.message}")
                statusModel.onError("连接失败：${e.message ?: "请检查打印机电源"}")
            }
        }
    }

    /** 回退路径：自研 SPP（保留代码，默认不启用）。 */
    fun connectViaLegacySpp() {
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

    fun connect() = if (useLegacySpp) connectViaLegacySpp() else connectViaSdk()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        try {
            if (grants.values.all { it }) {
                connect()
            } else {
                statusModel.onError("缺少蓝牙权限，请到系统设置开启")
            }
        } catch (e: Exception) {
            statusModel.onError("权限处理失败：${e.message ?: "请重试"}")
        }
    }

    /** 导出诊断日志（P-2）：AppLogger 环形缓冲 + crash-log.txt → 系统分享面板。 */
    fun exportDiag() {
        try {
            val crashText = CrashLogWriter.readAll(
                File(context.filesDir, CrashLogWriter.FILE_NAME),
            )
            val text = LoginDiag.exportTextWithCrash(AppLogger.snapshot(), crashText)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "JX 追溯 APP 诊断日志")
                putExtra(Intent.EXTRA_TEXT, text)
            }
            context.startActivity(Intent.createChooser(send, "导出诊断日志"))
        } catch (e: Exception) {
            statusModel.onError("导出失败：${e.message ?: "请重试"}")
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
                    if (state !is PrinterUiState.Connected) {
                        statusModel.onError("打印机未连接 · 去连接")
                    } else {
                        // 自检打印（追加任务）：SDK 绘制+出纸均阻塞——移 IO 线程；
                        // 回调可能来自库内部线程，statusModel 为 StateFlow 线程安全。
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                KmPrinterBridge.printSelfTest(
                                    versionName = BuildConfig.VERSION_NAME,
                                    onSuccess = {
                                        AppLogger.i("Printer", "自检打印成功")
                                        // 成功不覆盖连接态——仅 log；真机看纸即可
                                    },
                                    onFail = { msg ->
                                        AppLogger.e("Printer", "自检打印失败：$msg")
                                        statusModel.onError(msg)
                                    },
                                )
                            }
                        }
                    }
                },
            )
            Text(
                text = "驱动通道 ${if (useLegacySpp) "自研 SPP（回退）" else "快麦官方 SDK（KM-118BT）"} · " +
                    "纸宽 ${PrinterProfile.SIZE_MM}mm · 自检出一张测试标签",
                color = Tokens.Color.Text.tertiary,
                style = MaterialTheme.typography.labelMedium,
            )
            BigButton(
                text = "打开系统蓝牙设置",
                style = BigButtonStyle.Quiet,
                onClick = {
                    try {
                        context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                    } catch (e: Exception) {
                        statusModel.onError("无法打开系统蓝牙设置，请手动前往设置")
                    }
                },
            )
            // P-2：诊断导出入口（之前只在登录失败卡有，真机排障找不到）
            BigButton(
                text = "导出诊断日志",
                style = BigButtonStyle.Quiet,
                onClick = { exportDiag() },
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
