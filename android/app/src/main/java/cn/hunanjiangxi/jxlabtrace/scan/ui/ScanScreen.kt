package cn.hunanjiangxi.jxlabtrace.scan.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cn.hunanjiangxi.jxlabtrace.scan.CameraGate
import cn.hunanjiangxi.jxlabtrace.scan.HidCodeAssembler
import cn.hunanjiangxi.jxlabtrace.scan.ScanEcho
import cn.hunanjiangxi.jxlabtrace.scan.ScanHint
import cn.hunanjiangxi.jxlabtrace.scan.ScanUiState
import cn.hunanjiangxi.jxlabtrace.scan.ScanViewModel
import cn.hunanjiangxi.jxlabtrace.scan.cameraGateOf
import cn.hunanjiangxi.jxlabtrace.ui.components.BigButton
import cn.hunanjiangxi.jxlabtrace.ui.components.JxPreviewTheme
import cn.hunanjiangxi.jxlabtrace.ui.components.ToastHaptic
import cn.hunanjiangxi.jxlabtrace.ui.components.ToastTone
import cn.hunanjiangxi.jxlabtrace.ui.icons.JxIcons
import cn.hunanjiangxi.jxlabtrace.ui.theme.Tokens
import kotlinx.coroutines.delay

/** 手输配置（宿主给出本层类型与父对象；null = 本业务不提供手输入口）。 */
data class ManualConfig(val childType: Char, val parents: List<ManualParent>)

/**
 * S03 扫码取景层（M04）——**业务页内嵌，无全局入口（D25）**。
 *
 * @param title 顶栏标题＝「业务名 · 对象」（如「投料 · 扫袋码」），标识这一扫归属谁
 * @param viewModel 由宿主业务构造（必传 confirmLabel + onConfirm，M4-4）
 * @param cameraPermission 相机权限结果；false ⇒ 取景层显示「去设置」而非白屏（M4-5）
 */
@Composable
fun ScanScreen(
    title: String,
    viewModel: ScanViewModel,
    onBack: () -> Unit,
    cameraPermission: Boolean,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    manual: ManualConfig? = null,
) {
    val state by viewModel.state.collectAsState()
    val echo by viewModel.echo.collectAsState()
    val hint by viewModel.hint.collectAsState()

    var torchOn by remember { mutableStateOf(false) }
    var manualOpen by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.notice.collect { notice = it }
    }
    LaunchedEffect(echo) {
        if (echo != null) {
            delay(ECHO_VISIBLE_MS) // 尾 6 位大字 + 绿边 ≥800ms（S03 ①）
            viewModel.clearEcho()
        }
    }

    ScanContent(
        state = state,
        echo = echo,
        hintMessage = hint?.message,
        title = title,
        confirmLabel = viewModel.confirmLabel,
        torchOn = torchOn,
        onTorchToggle = { torchOn = !torchOn },
        onBack = onBack,
        onConfirm = viewModel::confirm,
        onRescan = viewModel::rescan,
        onContinue = viewModel::rescan,
        onHidCode = viewModel::onCode,
        onHidRejected = viewModel::onHidRejected,
        camera = {
            when (cameraGateOf(cameraPermission)) {
                CameraGate.Preview -> ScanCamera(
                    onCode = viewModel::onCode,
                    torchOn = torchOn,
                    modifier = Modifier.fillMaxSize(),
                )
                CameraGate.Denied -> CameraDeniedPanel(
                    onOpenSettings = onOpenSettings,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        },
        manualEntry = manual?.let {
            { manualOpen = true }
        },
        modifier = modifier,
    )

    if (manualOpen && manual != null) {
        ManualSheet(
            childType = manual.childType,
            parents = manual.parents,
            onGenerated = { full ->
                manualOpen = false
                viewModel.onCode(full)
            },
            onDismiss = { manualOpen = false },
        )
    }

    ToastHaptic(
        message = notice,
        onDismiss = { notice = null },
        tone = ToastTone.Neutral,
    )
}

/**
 * S03 取景层纯内容（预览与 [ScanScreen] 共用）：顶栏 + 取景框 + 提示 + 底部卡片 + 回显。
 * HID 通道在此挂 KeyEvent 流（扫码枪＝键盘，Enter 提交，M04 三通道之一）。
 */
@Composable
fun ScanContent(
    state: ScanUiState,
    echo: ScanEcho?,
    hintMessage: String?,
    title: String,
    confirmLabel: String,
    torchOn: Boolean,
    onTorchToggle: () -> Unit,
    onBack: () -> Unit,
    onConfirm: () -> Unit,
    onRescan: () -> Unit,
    onContinue: () -> Unit,
    onHidCode: (String) -> Unit,
    onHidRejected: (String) -> Unit,
    camera: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    manualEntry: (() -> Unit)? = null,
) {
    val hid = remember { HidCodeAssembler() }
    val focusRequester = remember { FocusRequester() }
    val finderTone = when {
        echo != null -> Tokens.Color.Status.success
        hintMessage != null || state is ScanUiState.Failure -> Tokens.Color.Status.danger
        else -> Tokens.Color.Text.inverse
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Tokens.Color.Text.primary)
            .focusRequester(focusRequester)
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                val native = event.nativeKeyEvent
                when {
                    event.key == Key.Enter || event.key == Key.NumPadEnter -> {
                        val outcome = hid.submit(event.nativeKeyEvent.eventTime)
                        when (outcome) {
                            is HidCodeAssembler.Outcome.Accepted -> onHidCode(outcome.raw)
                            is HidCodeAssembler.Outcome.Rejected -> onHidRejected(outcome.reason)
                        }
                        true
                    }
                    else -> {
                        val ch = native.unicodeChar.toChar()
                        if (ch.code != 0) {
                            hid.feed(ch, native.eventTime)
                            true
                        } else {
                            false
                        }
                    }
                }
            }
            .focusable(),
    ) {
        camera()

        Column(Modifier.fillMaxSize()) {
            ScanTopBar(
                title = title,
                torchOn = torchOn,
                onTorchToggle = onTorchToggle,
                onBack = onBack,
            )
            Spacer(Modifier.weight(1f))
            FinderFrame(tone = finderTone, modifier = Modifier.align(Alignment.CenterHorizontally))
            ScanHints(
                hintMessage = hintMessage,
                onManualEntry = manualEntry,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = Tokens.Space.space4),
            )
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.height(Tokens.Space.space12))
        }

        // 尾 6 位大字回显（S03 ①）
        if (echo != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(Tokens.Radius.lg))
                    .background(Tokens.Color.Status.success)
                    .padding(horizontal = Tokens.Space.space5, vertical = Tokens.Space.space4),
            ) {
                Text(
                    text = echo.tail6,
                    color = Tokens.Color.Text.inverse,
                    fontSize = Tokens.Type.Display.size,
                    lineHeight = Tokens.Type.Display.lineHeight,
                )
            }
        }

        // 底部：确认卡 / 失败条 / 完成卡
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(Tokens.Space.space4),
        ) {
            when (state) {
                is ScanUiState.Confirm -> ConfirmCard(
                    info = state.info,
                    confirmLabel = confirmLabel,
                    onConfirm = onConfirm,
                    onRescan = onRescan,
                )
                is ScanUiState.Failure -> ErrorBand(reason = state.reason, onRescan = onRescan)
                is ScanUiState.Done -> DoneCard(onContinue = onContinue)
                ScanUiState.Submitting -> SubmittingRow(confirmLabel)
                else -> Unit
            }
        }
    }

    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }
}

@Composable
private fun ScanTopBar(
    title: String,
    torchOn: Boolean,
    onTorchToggle: () -> Unit,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(Tokens.Size.topbar)
            .padding(horizontal = Tokens.Space.space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(Tokens.Size.touchMin)
                .clip(RoundedCornerShape(Tokens.Radius.sm))
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = JxIcons.Back,
                contentDescription = "返回",
                tint = Tokens.Color.Text.inverse,
            )
        }
        Text(
            text = title,
            color = Tokens.Color.Text.inverse,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = Tokens.Space.space2),
        )
        Box(
            modifier = Modifier
                .size(Tokens.Size.touchMin)
                .clip(RoundedCornerShape(Tokens.Radius.sm))
                .background(
                    if (torchOn) Tokens.Color.Brand.primarySoft else Tokens.Color.Surface.scrim,
                )
                .clickable(onClick = onTorchToggle),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = JxIcons.Torch,
                contentDescription = "手电筒",
                tint = if (torchOn) Tokens.Color.Brand.primary else Tokens.Color.Text.inverse,
            )
        }
    }
}

/** 取景框：四角边框，识别成功绿边、失败红边（双编码之色通道）。 */
@Composable
private fun FinderFrame(tone: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(Tokens.Size.traceScanHero * 2f)
            .border(3.dp, tone, RoundedCornerShape(Tokens.Radius.lg)),
    )
}

@Composable
private fun ScanHints(
    hintMessage: String?,
    onManualEntry: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (hintMessage != null) {
            Text(
                text = "识别失败 · $hintMessage",
                color = Tokens.Color.Status.danger,
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            Text(
                text = "对准袋上二维码，自动识别",
                color = Tokens.Color.Text.inverse,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "识别成功：震动 + 蜂鸣 + 尾 6 位大字回显",
                color = Tokens.Color.Text.inverse,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        if (onManualEntry != null) {
            Text(
                text = "手输：先选父对象 → 输序号",
                color = Tokens.Color.Brand.primarySoft,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .padding(top = Tokens.Space.space3)
                    .clip(RoundedCornerShape(Tokens.Radius.sm))
                    .clickable(onClick = onManualEntry)
                    .padding(horizontal = Tokens.Space.space4, vertical = Tokens.Space.space2),
            )
        }
        Text(
            text = "扫码枪对准即扫，无需按键",
            color = Tokens.Color.Text.inverse,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = Tokens.Space.space3),
        )
    }
}

@Composable
private fun SubmittingRow(confirmLabel: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Tokens.Radius.lg))
            .background(Tokens.Color.Surface.card)
            .padding(Tokens.Space.space4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        androidx.compose.material3.CircularProgressIndicator(
            modifier = Modifier.size(Tokens.Size.iconSm),
            color = Tokens.Color.Brand.primary,
        )
        Text(
            text = "$confirmLabel…",
            color = Tokens.Color.Text.primary,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = Tokens.Space.space3),
        )
    }
}

/**
 * 相机权限被拒（M4-5）：给「去设置」而非白屏——拦截三要素之「下一步」。
 */
@Composable
fun CameraDeniedPanel(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.background(Tokens.Color.Text.primary),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = JxIcons.Scan,
            contentDescription = null,
            tint = Tokens.Color.Status.danger,
            modifier = Modifier.size(Tokens.Size.iconLg * 1.5f),
        )
        Text(
            text = "相机权限未开启，扫码不可用",
            color = Tokens.Color.Text.inverse,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = Tokens.Space.space4),
        )
        Text(
            text = "开启相机后即可扫码；破损标签也可走「手输」（若本业务提供）。",
            color = Tokens.Color.Text.inverse,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(
                top = Tokens.Space.space2,
                start = Tokens.Space.space6,
                end = Tokens.Space.space6,
            ),
        )
        BigButton(
            text = "去设置",
            onClick = onOpenSettings,
            modifier = Modifier
                .padding(top = Tokens.Space.space5)
                .width(Tokens.Space.space12 * 4f),
        )
    }
}

/** 默认「去设置」动作：跳应用详情页（宿主可传自己的实现）。 */
fun openAppSettings(context: android.content.Context) {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null),
    )
    runCatching { context.startActivity(intent) }
}

private const val ECHO_VISIBLE_MS = 1_100L

// —— 预览 ——

@Preview(name = "w360", widthDp = 360, showBackground = true)
@Preview(name = "w390", widthDp = 390, showBackground = true)
@Preview(name = "w411", widthDp = 411, showBackground = true)
@Composable
private fun ScanScanningPreview() {
    JxPreviewTheme {
        ScanPreviewContent(state = ScanUiState.Scanning)
    }
}

@Preview(name = "w390", widthDp = 390, showBackground = true)
@Composable
private fun ScanConfirmPreview() {
    JxPreviewTheme {
        ScanPreviewContent(state = previewConfirmState)
    }
}

@Preview(name = "w390", widthDp = 390, showBackground = true)
@Composable
private fun ScanFailurePreview() {
    JxPreviewTheme {
        ScanPreviewContent(
            state = ScanUiState.Failure(
                cn.hunanjiangxi.jxlabtrace.scan.ScanFailure.UnknownCode("格式正确但系统内不存在"),
            ),
        )
    }
}

@Preview(name = "w390", widthDp = 390, showBackground = true)
@Composable
private fun CameraDeniedPreview() {
    JxPreviewTheme {
        CameraDeniedPanel(onOpenSettings = {})
    }
}

private val previewConfirmState = ScanUiState.Confirm(
    cn.hunanjiangxi.jxlabtrace.scan.ScanInfo(
        kind = "bag",
        code = "1BCG00010002261007030070007",
        human = "1B-CG-0001-0002-261007-03-007-000-7",
        typeLabel = "原料吨袋",
        fields = listOf(
            cn.hunanjiangxi.jxlabtrace.scan.ScanField("车牌", "湘F·A1234"),
            cn.hunanjiangxi.jxlabtrace.scan.ScanField("物料", "石油焦"),
            cn.hunanjiangxi.jxlabtrace.scan.ScanField("袋序", "7"),
            cn.hunanjiangxi.jxlabtrace.scan.ScanField("状态", "待投料"),
        ),
        voided = false,
    ),
)

@Composable
private fun ScanPreviewContent(state: ScanUiState) {
    ScanContent(
        state = state,
        echo = null,
        hintMessage = null,
        title = "投料 · 扫袋码",
        confirmLabel = "确认投料",
        torchOn = false,
        onTorchToggle = {},
        onBack = {},
        onConfirm = {},
        onRescan = {},
        onContinue = {},
        onHidCode = {},
        onHidRejected = {},
        camera = {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Tokens.Color.Text.primary),
            )
        },
        manualEntry = {},
    )
}
