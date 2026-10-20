package cn.hunanjiangxi.jxlabtrace.auth

import android.annotation.SuppressLint
import android.content.Intent
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
import java.net.URI
import kotlinx.coroutines.launch

/**
 * S01 登录 · 飞书授权（M03）。
 *
 * - 加载 `GET /api/auth/feishu/start`（302 → 飞书）；授权完成回落站内后，
 *   经 `CookieManager` 读取 `jx_sid` 写入 [cn.hunanjiangxi.jxlabtrace.net.SidStore]。
 * - 登录成功判定：回调 URL 回到站内且拿得到 jx_sid ⇒ 拉 `GET /api/me` 得 200 ⇒ 成功。
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
                        onManualOpen = { beginAuthorize() },
                        onCancel = ::handleCancel,
                        onExportDiag = ::handleExportDiag,
                        onRetry = { beginAuthorize() },
                        onAcknowledge = ::handleCancel,
                        onCallSupport = ::handleSupportCall,
                    )
                }
            }
        }
    }

    private fun beginAuthorize() {
        if (!app.networkMonitor.online.value) {
            failWith(LoginSignal.NetworkDown)
            return
        }
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
        handler.removeCallbacksAndMessages(null)
        webView.stopLoading()
        state = LoginUiState.Failed(failure)
    }

    private fun handleCancel() {
        handler.removeCallbacksAndMessages(null)
        webView.stopLoading()
        meFetched = false
        failed = false
        if (isReauth) {
            ReauthBridge.complete(false)
            finish()
        } else {
            state = LoginUiState.Initial
        }
    }

    private fun handleSupportCall() {
        // 排查指引 = 一键拨号综合运营部（拦截三要素口径；号码待 WorkBuddy 定稿）。
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$SUPPORT_PHONE"))
        runCatching { startActivity(intent) }
    }

    /** Authorizing 态「导出诊断日志」：全量缓冲 → 系统分享面板（text/plain）。 */
    private fun handleExportDiag() {
        val text = LoginDiag.exportText(AppLogger.snapshot())
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
    }
}
