package cn.hunanjiangxi.jxlabtrace

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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

/** 占位主页：仅展示应用名与版本号（导航图明确不做）；无会话时先去 S01 登录。 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as JxLabTraceApp
        if (app.sidStore.read().isNullOrBlank()) {
            startActivity(Intent(this, LoginWebViewActivity::class.java))
        }
        setContent {
            MaterialTheme(
                colorScheme = JxLabTheme.lightColorScheme(),
                typography = JxLabTheme.typography,
                shapes = JxLabTheme.shapes,
            ) {
                AppShell(versionName = BuildConfig.VERSION_NAME)
            }
        }
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
