package cn.hunanjiangxi.jxlabtrace.scan.ui

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import cn.hunanjiangxi.jxlabtrace.scan.ScanFeedback
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

/**
 * CameraX Preview + ML Kit 条码识别（M04 相机层）。
 * 只识别 Code128 / QR（标签与码制）；识别成功把裸串回调给 [onCode]。
 */
@Composable
fun ScanCamera(
    onCode: (String) -> Unit,
    torchOn: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    val cameraRef = remember { AtomicReference<Camera?>(null) }
    val latestOnCode = rememberUpdatedState(onCode)
    val latestTorch = rememberUpdatedState(torchOn)

    DisposableEffect(lifecycleOwner) {
        val analyzerExecutor = Executors.newSingleThreadExecutor()
        val mainExecutor = ContextCompat.getMainExecutor(context)
        var boundProvider: ProcessCameraProvider? = null
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            val provider = future.get()
            boundProvider = provider
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(analyzerExecutor, BarcodeAnalyzer { raw -> latestOnCode.value(raw) })
                }
            val selector = CameraSelector.DEFAULT_BACK_CAMERA
            val camera = provider.bindToLifecycle(lifecycleOwner, selector, preview, analysis)
            cameraRef.set(camera)
            if (latestTorch.value) camera.cameraControl.enableTorch(true)
        }, mainExecutor)

        onDispose {
            cameraRef.getAndSet(null)?.cameraControl?.enableTorch(false)
            runCatching { boundProvider?.unbindAll() }
            analyzerExecutor.shutdown()
        }
    }

    LaunchedEffect(torchOn) {
        cameraRef.get()?.cameraControl?.enableTorch(torchOn)
    }

    AndroidView(factory = { previewView }, modifier = modifier.fillMaxSize())
}

/** ML Kit 分析器：QR + Code128，取首个带内容的条码回调裸串。 */
private class BarcodeAnalyzer(
    private val onRaw: (String) -> Unit,
) : ImageAnalysis.Analyzer {

    private val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE, Barcode.FORMAT_CODE_128)
            .build(),
    )

    // androidx.annotation.OptIn（markerClass）——lint UnsafeOptInUsageError 只认该形态；
    // kotlin.OptIn 对 CameraX 实验标记不被 lint 放行（B5 lint 门禁首跑实证）。
    @androidx.annotation.OptIn(markerClass = [ExperimentalGetImage::class])
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }
        val input = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        scanner.process(input)
            .addOnSuccessListener { barcodes ->
                barcodes.firstOrNull()?.rawValue?.let { onRaw(it) }
            }
            .addOnCompleteListener { imageProxy.close() }
    }
}

/** 识别成功反馈：40ms 短震 + 双音蜂鸣（S03 ① 三通道中的声/触）。 */
class DeviceScanFeedback(private val context: Context) : ScanFeedback {

    override fun onRecognized(tail6: String) {
        vibrate()
        beep()
    }

    private fun vibrate() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        } ?: return
        if (!vibrator.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(40L, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(40L)
        }
    }

    private fun beep() {
        runCatching {
            val tone = ToneGenerator(AudioManager.STREAM_SYSTEM, 60)
            tone.startTone(ToneGenerator.TONE_PROP_BEEP, 60)
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                tone.startTone(ToneGenerator.TONE_PROP_BEEP, 60)
                runCatching { tone.release() }
            }, 120)
        }
    }
}
