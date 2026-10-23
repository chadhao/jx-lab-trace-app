package cn.hunanjiangxi.jxlabtrace

import android.app.Application
import cn.hunanjiangxi.jxlabtrace.auth.DataStoreMeStore
import cn.hunanjiangxi.jxlabtrace.auth.FileSidStore
import cn.hunanjiangxi.jxlabtrace.auth.KeystoreSidCipher
import cn.hunanjiangxi.jxlabtrace.auth.MeRepository
import cn.hunanjiangxi.jxlabtrace.auth.ReauthBridge
import cn.hunanjiangxi.jxlabtrace.log.AppLogger
import cn.hunanjiangxi.jxlabtrace.log.CrashLogWriter
import cn.hunanjiangxi.jxlabtrace.net.ApiClient
import cn.hunanjiangxi.jxlabtrace.net.JxCookieJar
import cn.hunanjiangxi.jxlabtrace.net.NetworkMonitor
import cn.hunanjiangxi.jxlabtrace.net.SessionCoordinator
import cn.hunanjiangxi.jxlabtrace.print.AndroidBluetoothSpp
import cn.hunanjiangxi.jxlabtrace.print.KmPrinterBridge
import cn.hunanjiangxi.jxlabtrace.print.PrinterStatusModel
import cn.hunanjiangxi.jxlabtrace.print.PrinterUiState
import cn.hunanjiangxi.jxlabtrace.print.shouldAutoConnect
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** 应用壳：装配全局单例（网络监听、会话、当前用户缓存、打印机共享状态）。 */
class JxLabTraceApp : Application() {

    lateinit var networkMonitor: NetworkMonitor
        private set

    lateinit var sidStore: FileSidStore
        private set

    lateinit var apiClient: ApiClient
        private set

    lateinit var session: SessionCoordinator
        private set

    lateinit var meRepository: MeRepository
        private set

    /** 打印机连接状态（App 级共享 · MIMO-TASK-AUTO-CONNECT）：自动/手动连接与各屏读同一实例。 */
    val printerStatus: PrinterStatusModel = PrinterStatusModel()

    /** 自动连接专用 scope（IO 常驻；无 UI 生命周期依赖）。 */
    private val printerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 自动连接去重：一次启动最多尝试一次（幂等防护④）。 */
    @Volatile
    private var autoConnectStarted = false

    override fun onCreate() {
        super.onCreate()
        instance = this
        // 全局崩溃留栈（追加任务 2026-10-22）：未捕获异常追加写 files/crash-log.txt，
        // 写完交还系统默认 handler（保持崩溃行为）。诊断导出（LoginDiag）一并读取该文件。
        CrashLogWriter.install(
            file = File(filesDir, CrashLogWriter.FILE_NAME),
            versionName = BuildConfig.VERSION_NAME,
        )
        networkMonitor = NetworkMonitor(this).also { it.start() }

        // M03：jx_sid 文件持久化（Keystore 加密），注入 OkHttp CookieJar。
        sidStore = FileSidStore(File(filesDir, SID_FILE), KeystoreSidCipher())
        // M12：online 门禁接 NetworkMonitor —— 离线时读写请求一律不发出。
        apiClient = ApiClient(
            baseUrl = BuildConfig.BASE_URL,
            cookieJar = JxCookieJar(sidStore),
            online = { networkMonitor.online.value },
        )
        // M03：401 ⇒ SessionExpired 事件 ⇒ 全屏原位重授权 ⇒ 重放原请求。
        session = SessionCoordinator(reauthorize = { ReauthBridge.awaitReauth(this) })
        meRepository = MeRepository(apiClient, DataStoreMeStore(this))

        // 启动自动连接已保存打印机（MIMO-TASK-AUTO-CONNECT）：条件不满足/失败均静默。
        tryAutoConnectPrinter()
    }

    /**
     * 自动连接已保存打印机（IO 线程，静默语义）：
     * 条件（[shouldAutoConnect] 三与）：printer_memory 有地址 && 蓝牙权限已授予 && 当前未连接。
     * - 缺权限 ⇒ 静默跳过（不弹窗——用户下次进打印机屏走正常授权流程）；
     * - 找不到已配对设备/连接失败 ⇒ 静默回落 Disconnected（不 onError——打印机可能没开机）；
     * - 成功 ⇒ 更新 [printerStatus]（打印机屏打开直接显示已连接）。
     * 幂等：[autoConnectStarted] 一次启动仅一次；已连接态由 shouldAutoConnect 拦截。
     */
    private fun tryAutoConnectPrinter() {
        if (autoConnectStarted) return
        autoConnectStarted = true
        printerScope.launch {
            try {
                val saved = runCatching {
                    getSharedPreferences(PRINTER_MEMORY_FILE, MODE_PRIVATE)
                        .getString(PRINTER_MEMORY_KEY, null)
                }.getOrNull()
                // 已被手动/上次连接占用 ⇒ 不碰
                val currentState = printerStatus.state.value
                if (currentState is PrinterUiState.Connected || currentState is PrinterUiState.Connecting) {
                    return@launch
                }
                val spp = AndroidBluetoothSpp(this@JxLabTraceApp)
                val hasPerm = runCatching { spp.hasPermission() }.getOrDefault(false)
                val connected = runCatching { KmPrinterBridge.isConnected() }.getOrDefault(false)
                if (!shouldAutoConnect(saved, hasPerm, connected)) {
                    AppLogger.d("Printer", "自动连接跳过：saved=${!saved.isNullOrBlank()} perm=$hasPerm connected=$connected")
                    return@launch
                }
                // 权限已授予（上面三与），可安全读已配对设备
                val addr = saved!!
                val name = runCatching {
                    spp.bondedDevices().find { it.address == addr }?.name
                }.getOrNull()
                if (name == null) {
                    // 已取消配对 ⇒ 静默（用户进屏手动处理）
                    AppLogger.d("Printer", "自动连接跳过：地址 $addr 不在已配对列表")
                    return@launch
                }
                printerStatus.onConnecting()
                val ok = runCatching { KmPrinterBridge.connect(name, addr) }.getOrDefault(false)
                if (ok) {
                    AppLogger.i("Printer", "启动自动连接成功：$name ($addr)")
                    printerStatus.onConnected(name)
                } else {
                    // 静默失败：回落未连接，不弹错误（打印机可能没开机）
                    AppLogger.d("Printer", "自动连接失败（静默）：$name ($addr)")
                    printerStatus.onDisconnected()
                }
            } catch (e: Exception) {
                // 兜底：自动连接任何异常都不允许影响启动
                AppLogger.d("Printer", "自动连接异常（静默）：${e.message}")
                runCatching { printerStatus.onDisconnected() }
            }
        }
    }

    companion object {
        private const val SID_FILE = "jx_sid.bin"

        /** 与 PrinterScreen 手动连接共用的记忆文件（同名同 key ⇒ 同一地址）。 */
        private const val PRINTER_MEMORY_FILE = "printer_memory"
        private const val PRINTER_MEMORY_KEY = "address"

        lateinit var instance: JxLabTraceApp
            private set
    }
}
