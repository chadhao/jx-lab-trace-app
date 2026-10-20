package cn.hunanjiangxi.jxlabtrace

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import cn.hunanjiangxi.jxlabtrace.auth.LoginWebViewActivity
import cn.hunanjiangxi.jxlabtrace.ui.theme.JxLabTheme
import cn.hunanjiangxi.jxlabtrace.ui.theme.Tokens

/**
 * 登录路由纯函数：sid 空白 ⇒ 应去登录屏（MIMO-TASK-LOGIN-FLOW-FIX F-2）。
 * null / 空串 / 纯空白均视为无会话。
 */
fun shouldRouteToLogin(sid: String?): Boolean = sid.isNullOrBlank()

/**
 * 占位主页：仅展示应用名与版本号（导航图明确不做）；无会话时先去 S01 登录。
 *
 * MIMO-TASK-LOGIN-FLOW-FIX：sid 判断由仅 onCreate 改为 onCreate + onResume 双检查——
 * 从 LoginWebViewActivity 返回（back / 失败卡「我知道了」finish）时按最新 sid 重新路由，
 * 形成「拒绝 → 回登录屏 → 可重试」的可恢复循环。防抖：Login 在前台期间不重复 startActivity
 * （resultLauncher 结果回填清位，onResume 见位跳过）。
 */
class MainActivity : ComponentActivity() {

    /** 防抖位：登录屏已拉起、结果未回期间，onResume 不再重复 launch。 */
    private var awaitingLoginResult = false

    private val loginLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        // 登录屏已关（成功 / 失败确认 / back）：清防抖位，由 onResume 按 sid 决定是否再拉起
        awaitingLoginResult = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = JxLabTheme.lightColorScheme(),
                typography = JxLabTheme.typography,
                shapes = JxLabTheme.shapes,
            ) {
                AppShell(versionName = BuildConfig.VERSION_NAME)
            }
        }
        routeToLoginIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        routeToLoginIfNeeded()
    }

    private fun routeToLoginIfNeeded() {
        if (awaitingLoginResult) return
        val app = application as JxLabTraceApp
        if (!shouldRouteToLogin(app.sidStore.read())) return
        awaitingLoginResult = true
        loginLauncher.launch(Intent(this, LoginWebViewActivity::class.java))
    }
}

@Composable
private fun AppShell(versionName: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Surface(modifier = modifier.fillMaxSize(), color = Tokens.Color.Surface.canvas) {
        Box(contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = context.getString(R.string.app_name),
                    color = Tokens.Color.Text.primary,
                    style = MaterialTheme.typography.headlineLarge,
                )
                Text(
                    text = "v$versionName",
                    color = Tokens.Color.Text.tertiary,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = Tokens.Space.space2),
                )
            }
        }
    }
}
