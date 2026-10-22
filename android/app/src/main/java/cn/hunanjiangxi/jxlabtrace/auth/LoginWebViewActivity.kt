package cn.hunanjiangxi.jxlabtrace.auth

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import cn.hunanjiangxi.jxlabtrace.log.AppLogger
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import cn.hunanjiangxi.jxlabtrace.BuildConfig
import cn.hunanjiangxi.jxlabtrace.JxLabTraceApp
import cn.hunanjiangxi.jxlabtrace.net.ApiResult
import cn.hunanjiangxi.jxlabtrace.ui.login.LoginScreen
import cn.hunanjiangxi.jxlabtrace.ui.login.LoginUiState
import cn.hunanjiangxi.jxlabtrace.ui.theme.JxLabTheme
import com.ss.android.larksso.CallBackData
import com.ss.android.larksso.IGetDataCallback
import com.ss.android.larksso.LarkSSO
import java.net.URI
import kotlinx.coroutines.launch

/**
 * S01 登录 · LarkSSO 授权（M03 → MIMO-TASK-LARKSSO）。
 *
 * - 首选 [LarkSSO] SDK 拉起飞书 App 授权（`setServer("Feishu")` 国内版）；
 *   未装飞书 / SDK 跳转失败（-4）/ 5s 无响应 ⇒ 回落原 WebView 流程（保留不删）。
 * - SDK 回 `code` ⇒ [FeishuAuthRepo.exchange]（POST /api/auth/feishu/exchange {code,state}）⇒
 *   Set-Cookie 由 OkHttp CookieJar 持有 ⇒ `GET /api/me` ⇒ 成功。
 * - 三处 `parseIntent`（官方 SDK Guide：onResume / onNewIntent / onActivityResult）。
 * - 四态：初始 / 授权中（5s 超时给手动打开）/ 失败卡（三类分因）/ 成功过渡（1.2s 自动进占位主页）。
 * - EXTRA_REAUTH=true 时为会话过期原位重授权：结束时回填 [ReauthBridge]。
 */
class LoginWebViewActivity : ComponentActivity() {

    private val app: JxLabTraceApp get() = application as JxLabTraceApp

    private val isReauth: Boolean by lazy { intent.getBooleanExtra(EXTRA_REAUTH, false) }

    private var state: LoginUiState by mutableStateOf(LoginUiState.Initial)

    private lateinit var webView: WebView

    private val handler = Handler(Looper.getMainLooper())
    private var manualRunnable: Runnable? = null
    private var successRunnable: Runnable? = null
    private var meFetched = false
    private var failed = false

    /** SSO / WebView 互斥（同一时间只跑一条流程）。 */
    private enum class FlowMode { Idle, Sso, Webview }

    private var flowMode: FlowMode = FlowMode.Idle

    /** 会话随机 state（内存持有；SDK 回 code 后随 exchange 原样回传，回调即弃）。 */
    private var pendingState: String? = null

    private val feishuRepo: FeishuAuthRepo by lazy { FeishuAuthRepo(app.apiClient) }

    private val baseHost: String = URI(BuildConfig.BASE_URL).host.orEmpty()
    private val startUrl: String = BuildConfig.BASE_URL.trimEnd('/') + START_PATH

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 远程调试（chrome://inspect）：真机排查登录卡点用，须在 WebView 创建前开启。
        WebView.setWebContentsDebuggingEnabled(true)

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            // accounts.feishu.cn 识别 WebView 默认 UA 会降级渲染（蓝底不出登录 UI）——改用系统标准 Chrome UA
            settings.userAgentString = android.webkit.WebSettings.getDefaultUserAgent(context)
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                    consoleMessage?.let {
                        AppLogger.i(
                            "LoginWeb",
                            "console[${it.messageLevel()}] ${it.message()} (${it.sourceId()}:${it.lineNumber()})",
                        )
                    }
                    return true
                }
            }
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest,
                ): Boolean = false // 全部在应用内 WebView 完成，不外跳浏览器

                @Deprecated("Deprecated in Java")
                override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean = false

                override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                    AppLogger.i("LoginWeb", "onPageStarted: $url")
                    (state as? LoginUiState.Authorizing)?.let {
                        state = it.copy(diagUrl = url)
                    }
                }

                override fun onPageFinished(view: WebView, url: String) {
                    AppLogger.i("LoginWeb", "onPageFinished: $url")
                    handlePageFinished(url)
                }

                override fun onReceivedError(
                    view: WebView,
                    request: WebResourceRequest,
                    error: WebResourceError,
                ) {
                    AppLogger.e("LoginWeb", "onReceivedError: ${request.url} ${error.description}")
                    if (request.isForMainFrame) failWith(LoginSignal.NetworkDown)
                }
            }
        }

        if (isReauth) {
            state = LoginUiState.Authorizing()
            beginAuthorize()
        }

        setContent {
            MaterialTheme(
                colorScheme = JxLabTheme.lightColorScheme(),
                typography = JxLabTheme.typography,
                shapes = JxLabTheme.shapes,
            ) {
                Box(Modifier.fillMaxSize()) {
                    // 底层始终挂 WebView（授权中且在远端页时裸露），上层按四态盖 UI。
                    AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())
                    LoginScreen(
                        state = state,
                        versionName = BuildConfig.VERSION_NAME,
                        serverHost = baseHost,
                        onLogin = { beginAuthorize() },
                        onManualOpen = { beginWebView() },
                        onCancel = ::handleCancel,
                        onExportDiag = ::handleExportDiag,
                        onRetry = { beginAuthorize() },
                        onAcknowledge = ::handleAcknowledge,
                        onCallSupport = ::handleSupportCall,
                    )
                }
            }
        }
    }

    /** 官方 SDK Guide：onResume / onNewIntent / onActivityResult 三处 parseIntent。 */
    override fun onResume() {
        super.onResume()
        LarkSSO.inst().parseIntent(this, intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        LarkSSO.inst().parseIntent(this, intent)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        LarkSSO.inst().parseIntent(this, data)
    }

    /**
     * 登录/重试入口：装了飞书走 LarkSSO 拉起 App 授权；
     * 未装飞书 ⇒ 直接回落原 WebView 流程（兜底保留不删）。
     */
    private fun beginAuthorize() {
        if (!app.networkMonitor.online.value) {
            failWith(LoginSignal.NetworkDown)
            return
        }
        if (flowMode != FlowMode.Idle) return // 防重入：同一时间只跑一条流程
        if (!isFeishuInstalled()) {
            AppLogger.w("LoginWeb", "未安装飞书 App，回落 WebView 授权")
            beginWebView()
            return
        }
        startSso()
    }

    /** `lark://ssoclient` 可解析 ⇒ 设备装有飞书（<queries> 已声明可见性，官方 Demo 口径）。 */
    private fun isFeishuInstalled(): Boolean = runCatching {
        val probe = Intent(Intent.ACTION_VIEW, Uri.parse(LARK_CLIENT_PREFIX))
        val matches = if (android.os.Build.VERSION.SDK_INT >= 33) {
            packageManager.queryIntentActivities(probe, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(probe, 0)
        }
        matches.isNotEmpty()
    }.getOrDefault(false)

    /** LarkSSO 发起授权（国内版 Feishu），回调在 [handleSsoCode] / [handleSsoError]。 */
    private fun startSso() {
        failed = false
        meFetched = false
        state = LoginUiState.Authorizing()
        handler.removeCallbacksAndMessages(null)
        flowMode = FlowMode.Sso
        val stateValue = FeishuSsoLogin.newState()
        pendingState = stateValue
        val builder = LarkSSO.Builder()
            .setAppId(BuildConfig.FEISHU_CLIENT_ID)
            .setServer(SERVER_FEISHU)
            .setChallengeMode(false) // 后端 exchange 按 {code,state} 换会话，未启用挑战码
            .setScopeList(arrayListOf(SCOPE_USER_BASE))
            .setContext(this)
        AppLogger.i("LoginWeb", "LarkSSO 拉起飞书授权（state=$stateValue）")
        LarkSSO.inst().startSSOVerify(builder, object : IGetDataCallback {
            override fun onSuccess(callBackData: CallBackData) {
                AppLogger.i("LoginWeb", "LarkSSO onSuccess")
                handleSsoCode(callBackData.code)
            }

            override fun onError(callBackData: CallBackData) {
                AppLogger.w("LoginWeb", "LarkSSO onError code=${callBackData.code}")
                handleSsoError(callBackData.code)
            }
        })
    }

    /** SDK 回 code ⇒ 复用 FeishuAuthRepo.exchange ⇒ 2xx 再拉 /api/me。 */
    private fun handleSsoCode(code: String?) {
        val stateValue = pendingState
        if (code.isNullOrBlank() || stateValue == null) {
            AppLogger.w("LoginWeb", "LarkSSO 回调缺 code/state，拒绝处理")
            failWith(LoginSignal.StateMismatch)
            return
        }
        meFetched = true
        lifecycleScope.launch {
            when (val outcome = feishuRepo.exchange(code, stateValue)) {
                FeishuAuthRepo.ExchangeOutcome.Ok -> {
                    pendingState = null
                    fetchMe()
                }

                is FeishuAuthRepo.ExchangeOutcome.Rejected -> {
                    pendingState = null
                    failWith(LoginSignal.ExchangeRejected(outcome.message))
                }

                FeishuAuthRepo.ExchangeOutcome.Offline -> failWith(LoginSignal.NetworkDown)
            }
        }
        // 与 WebView 同口径 5s 超时：给「手动打开」（= WebView 兜底入口），pendingState 保留接晚到回调
        val manual = Runnable {
            if (flowMode == FlowMode.Sso) {
                val current = state as? LoginUiState.Authorizing ?: return@Runnable
                if (!current.onRemotePage && !meFetched) {
                    flowMode = FlowMode.Idle
                    state = current.copy(manualAvailable = true)
                }
            }
        }
        manualRunnable = manual
        handler.postDelayed(manual, MANUAL_TIMEOUT_MS)
    }

    /** SDK onError：-4 回落 WebView 兜底；-3/-1/其余进失败卡。 */
    private fun handleSsoError(errorCode: String?) {
        val signal = FeishuSsoLogin.signalForError(errorCode)
        if (signal == null) {
            AppLogger.w("LoginWeb", "LarkSSO 跳转飞书失败（code=$errorCode），回落 WebView 授权")
            beginWebView()
            return
        }
        failWith(signal)
    }

    /** 原 WebView 授权流程（SSO 兜底路径；逻辑与 M03 一致）。 */
    private fun beginWebView() {
        flowMode = FlowMode.Webview
        failed = false
        meFetched = false
        state = LoginUiState.Authorizing()
        handler.removeCallbacksAndMessages(null)
        webView.loadUrl(startUrl)
        val manual = Runnable {
            val current = state as? LoginUiState.Authorizing ?: return@Runnable
            if (!current.onRemotePage && !meFetched) {
                state = current.copy(manualAvailable = true)
            }
        }
        manualRunnable = manual
        handler.postDelayed(manual, MANUAL_TIMEOUT_MS)
    }

    private fun handlePageFinished(url: String) {
        if (failed) return
        val stateNow = state as? LoginUiState.Authorizing ?: return
        val sid = readSidCookie()
        if (sid != null) {
            manualRunnable?.let { handler.removeCallbacks(it) }
            if (meFetched) return
            meFetched = true
            app.sidStore.write(sid)
            state = stateNow.copy(manualAvailable = false, onRemotePage = false)
            fetchMe()
            return
        }
        val host = runCatching { URI(url).host }.getOrNull()
        when {
            host != baseHost -> state = stateNow.copy(onRemotePage = true)
            url != startUrl -> failWith(LoginSignal.AuthorizationRejected)
            else -> Unit // 仍在起点等待 302
        }
    }

    private fun readSidCookie(): String? =
        CookieManager.getInstance().getCookie(BuildConfig.BASE_URL)
            ?.split(';')
            ?.map { it.trim() }
            ?.firstOrNull { it.startsWith("$SID_COOKIE=") }
            ?.substringAfter('=')
            ?.takeIf { it.isNotBlank() }
            ?.also { CookieManager.getInstance().flush() }

    private fun fetchMe() {
        lifecycleScope.launch {
            when (val result = app.meRepository.refresh()) {
                is ApiResult.Success -> applyOutcome(
                    loginOutcomeOf(LoginSignal.MeLoaded(result.data)),
                )
                is ApiResult.Failure -> {
                    if (result.error is cn.hunanjiangxi.jxlabtrace.net.ApiError.Unauthorized) {
                        // 陈旧 sid：清本地与 WebView Cookie，避免「旧凭据 → 401 → 死循环」
                        app.sidStore.write(null)
                        runCatching {
                            // CookieManager 无单 Cookie 删除 API，用过期指令清 jx_sid
                            CookieManager.getInstance().setCookie(baseHost, "$SID_COOKIE=; Max-Age=0")
                            CookieManager.getInstance().flush()
                        }
                        applyOutcome(loginOutcomeOf(LoginSignal.AuthorizationRejected))
                    } else {
                        applyOutcome(loginOutcomeOf(LoginSignal.MeError(result.error)))
                    }
                }
            }
        }
    }

    private fun applyOutcome(outcome: LoginOutcome) {
        if (outcome is LoginOutcome.Success) {
            state = LoginUiState.Success(outcome.me)
            val proceed = Runnable {
                if (isReauth) ReauthBridge.complete(true)
                finish()
            }
            successRunnable = proceed
            handler.postDelayed(proceed, SUCCESS_AUTO_MS)
        } else {
            showFailure((outcome as LoginOutcome.Failed).failure)
        }
    }

    private fun failWith(signal: LoginSignal) {
        val failure = (loginOutcomeOf(signal) as? LoginOutcome.Failed)?.failure ?: return
        showFailure(failure)
    }

    private fun showFailure(failure: LoginFailure) {
        failed = true
        flowMode = FlowMode.Idle // 失败卡可直接重试（重入需回到 Idle）
        handler.removeCallbacksAndMessages(null)
        webView.stopLoading()
        state = LoginUiState.Failed(failure)
    }

    private fun handleCancel() {
        handler.removeCallbacksAndMessages(null)
        webView.stopLoading()
        flowMode = FlowMode.Idle
        pendingState = null
        meFetched = false
        failed = false
        if (isReauth) {
            ReauthBridge.complete(false)
            finish()
        } else {
            state = LoginUiState.Initial
        }
    }

    /**
     * 失败卡「我知道了」（LOGIN-FLOW-FIX ③）：清会话后 finish 返回主屏——
     * 主屏 onResume 见空 sid 自动再拉起登录屏，形成可恢复循环。
     * AUTH_NO_ROLE 路径 exchange 成功时 sid 已被 CookieJar 落盘：不清则主屏判非空，
     * 永远停在白屏占位页（真机首验 bug 根因）。
     */
    private fun handleAcknowledge() {
        handler.removeCallbacksAndMessages(null)
        webView.stopLoading()
        flowMode = FlowMode.Idle
        pendingState = null
        meFetched = false
        failed = false
        app.sidStore.write(null)
        runCatching {
            // CookieManager 无单 Cookie 删除 API，用过期指令清 jx_sid（与 fetchMe 陈旧 sid 同法）
            CookieManager.getInstance().setCookie(baseHost, "$SID_COOKIE=; Max-Age=0")
            CookieManager.getInstance().flush()
        }
        if (isReauth) {
            ReauthBridge.complete(false)
        }
        finish()
    }

    private fun handleSupportCall() {
        // 排查指引 = 一键拨号综合运营部（拦截三要素口径；号码待 WorkBuddy 定稿）。
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$SUPPORT_PHONE"))
        runCatching { startActivity(intent) }
    }

    /** Authorizing 态「导出诊断日志」：全量缓冲 + crash-log.txt → 系统分享面板（text/plain）。 */
    private fun handleExportDiag() {
        val crashText = cn.hunanjiangxi.jxlabtrace.log.CrashLogWriter.readAll(
            java.io.File(filesDir, cn.hunanjiangxi.jxlabtrace.log.CrashLogWriter.FILE_NAME),
        )
        val text = LoginDiag.exportTextWithCrash(AppLogger.snapshot(), crashText)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "登录诊断日志")
            putExtra(Intent.EXTRA_TEXT, text)
        }
        runCatching { startActivity(Intent.createChooser(send, "登录诊断日志")) }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        webView.destroy()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_REAUTH = "reauth"
        private const val START_PATH = "/api/auth/feishu/start"
        private const val SID_COOKIE = "jx_sid"
        private const val MANUAL_TIMEOUT_MS = 5_000L
        private const val SUCCESS_AUTO_MS = 1_200L
        private const val SUPPORT_PHONE = "0730-xxxxxxx"

        /** LarkSSO：国内版服务器标识（官方 SDK Guide 要求字面 Feishu）。 */
        private const val SERVER_FEISHU = "Feishu"

        /** 必授权限（在应用已申请的权限范围内，超出的会被忽略）。 */
        private const val SCOPE_USER_BASE = "contact:user.base:readonly"

        /** 飞书客户端探测入口（与 <queries> 中声明的 scheme/host 一致）。 */
        private const val LARK_CLIENT_PREFIX = "lark://ssoclient"
    }
}
