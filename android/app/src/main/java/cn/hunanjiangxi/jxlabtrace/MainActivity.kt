package cn.hunanjiangxi.jxlabtrace

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
import cn.hunanjiangxi.jxlabtrace.ui.theme.JxLabTheme
import cn.hunanjiangxi.jxlabtrace.ui.theme.Tokens

/** 工程壳 Activity：仅展示应用名与版本号，不做任何业务页面。 */
class MainActivity : ComponentActivity() {
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
