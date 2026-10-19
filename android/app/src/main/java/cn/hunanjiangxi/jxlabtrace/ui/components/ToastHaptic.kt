package cn.hunanjiangxi.jxlabtrace.ui.components

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import cn.hunanjiangxi.jxlabtrace.ui.icons.JxIcons
import cn.hunanjiangxi.jxlabtrace.ui.theme.Tokens
import kotlinx.coroutines.delay

/** 提示语气：成功短震 / 失败长震（双编码：图标 + 色 + 文案）。 */
enum class ToastTone { Success, Fail, Neutral }

/**
 * ToastHaptic：Toast + 振动反馈封装。
 * - 成功：短震动 40ms；失败：长震动 200ms（tokens.feedback 口径）；
 * - 消息非空时展示胶囊提示，[onDismiss] 由调用方清空消息。
 * - 可预览（previewMode）便于设计走查。
 */
@Composable
fun ToastHaptic(
    message: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    tone: ToastTone = ToastTone.Neutral,
    durationMillis: Long = 1_500,
    previewMode: Boolean = false,
) {
    val context = LocalContext.current
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(message) {
        if (message.isNullOrEmpty()) {
            visible = false
        } else if (previewMode) {
            // 预览模式：静态展示，不振动、不自动消失
            visible = true
        } else {
            visible = true
            vibrate(context, tone)
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            delay(durationMillis)
            visible = false
            onDismiss()
        }
    }

    val (accent, soft) = when (tone) {
        ToastTone.Success -> Tokens.Color.Status.success to Tokens.Color.Status.successSoft
        ToastTone.Fail -> Tokens.Color.Status.danger to Tokens.Color.Status.dangerSoft
        ToastTone.Neutral -> Tokens.Color.Status.neutral to Tokens.Color.Status.neutralSoft
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible = visible && !message.isNullOrEmpty(),
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
        ) {
            androidx.compose.foundation.layout.Row(
                modifier = Modifier
                    .padding(bottom = Tokens.Space.space12)
                    .clip(RoundedCornerShape(Tokens.Radius.pill))
                    .background(soft)
                    .padding(horizontal = Tokens.Space.space4, vertical = Tokens.Space.space3),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = when (tone) {
                        ToastTone.Success -> JxIcons.Success
                        ToastTone.Fail -> JxIcons.Exception
                        ToastTone.Neutral -> JxIcons.Info
                    },
                    contentDescription = null,
                    tint = accent,
                )
                Text(
                    text = message.orEmpty(),
                    color = accent,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = Tokens.Space.space2),
                )
            }
        }
    }
}

private fun vibrate(context: Context, tone: ToastTone) {
    val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    } ?: return
    if (!vibrator.hasVibrator()) return
    val durationMs = when (tone) {
        ToastTone.Success -> 40L
        ToastTone.Fail -> 200L
        ToastTone.Neutral -> 40L
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
    } else {
        @Suppress("DEPRECATION")
        vibrator.vibrate(durationMs)
    }
}

@Preview(name = "w360", widthDp = 360, showBackground = true)
@Preview(name = "w390", widthDp = 390, showBackground = true)
@Preview(name = "w411", widthDp = 411, showBackground = true)
@Composable
private fun ToastHapticPreview() {
    JxPreviewTheme {
        Box(Modifier.background(Color.Transparent)) {
            ToastHaptic(
                message = "已建 12 袋 · 失败 0 袋",
                onDismiss = {},
                tone = ToastTone.Success,
                previewMode = true,
            )
        }
    }
}
