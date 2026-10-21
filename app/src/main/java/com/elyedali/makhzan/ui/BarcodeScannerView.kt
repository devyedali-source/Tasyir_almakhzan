package com.elyedali.makhzan.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.camera.core.Camera
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import com.elyedali.makhzan.i18n.tr

/**
 * كاميرا تقرأ الكود بار باستمرار. نفس الكود لا يُقرأ مرة ثانية إلا بعد إبعاده عن الكاميرا قليلًا.
 * paused = true يوقف القراءة مؤقتًا (مثلًا أثناء فتح نافذة).
 */
@androidx.annotation.OptIn(ExperimentalGetImage::class)
@Composable
fun BarcodeScannerView(
    modifier: Modifier = Modifier,
    paused: Boolean = false,
    onCode: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var denied by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
        denied = !it
    }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }
    // إذا منح المستخدم الإذن من إعدادات الهاتف ثم عاد إلى التطبيق
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) {
                granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    val shape = RoundedCornerShape(16.dp)
    if (!granted) {
        Box(modifier.background(Color(0xFF1B1B1B), shape), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    tr("يحتاج التطبيق إلى الكاميرا لقراءة الكود بار"),
                    color = Color.White, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = {
                    if (denied) {
                        // بعد الرفض يجب منح الإذن من إعدادات الهاتف
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                        )
                    } else launcher.launch(Manifest.permission.CAMERA)
                }) {
                    Text(if (denied) tr("فتح إعدادات الهاتف") else tr("السماح باستعمال الكاميرا"))
                }
            }
        }
        return
    }

    val view = LocalView.current
    var camera by remember { mutableStateOf<Camera?>(null) }
    var torchOn by remember { mutableStateOf(false) }
    val currentOnCode by rememberUpdatedState(onCode)
    val currentPaused by rememberUpdatedState(paused)
    val beeper = remember { runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 90) }.getOrNull() }
    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }

    DisposableEffect(lifecycleOwner) {
        val mainExecutor = ContextCompat.getMainExecutor(context)
        val analysisExecutor = Executors.newSingleThreadExecutor()
        val scanner = BarcodeScanning.getClient()
        var provider: ProcessCameraProvider? = null
        var lastCode = ""
        var lastSeen = 0L

        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            val p = future.get()
            provider = p
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(analysisExecutor) { proxy ->
                val media = proxy.image
                if (media == null || currentPaused) {
                    proxy.close()
                    return@setAnalyzer
                }
                val input = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
                scanner.process(input)
                    .addOnSuccessListener(mainExecutor) { codes ->
                        val value = codes.firstOrNull { !it.rawValue.isNullOrBlank() }?.rawValue?.trim()
                        if (value != null && !currentPaused) {
                            val now = SystemClock.elapsedRealtime()
                            val isNew = value != lastCode || now - lastSeen > 1200
                            lastCode = value
                            lastSeen = now
                            if (isNew) {
                                beeper?.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                currentOnCode(value)
                            }
                        }
                    }
                    .addOnCompleteListener { proxy.close() }
            }
            runCatching {
                p.unbindAll()
                camera = p.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                torchOn = false
            }
        }, mainExecutor)

        onDispose {
            provider?.unbindAll()
            scanner.close()
            analysisExecutor.shutdown()
        }
    }
    DisposableEffect(Unit) { onDispose { beeper?.release() } }

    Box(modifier.clip(shape).border(2.dp, Color(0xFF0B6E4F), shape)) {
        AndroidView(factory = { previewView }, modifier = Modifier.matchParentSize())
        Box(
            Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.7f)
                .height(2.dp)
                .background(Color.Red.copy(alpha = 0.8f))
        )
        if (paused) {
            Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.5f)))
        }
        // زر المصباح للمحلات قليلة الإضاءة
        if (camera?.cameraInfo?.hasFlashUnit() == true) {
            IconButton(
                onClick = {
                    torchOn = !torchOn
                    camera?.cameraControl?.enableTorch(torchOn)
                },
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)
                    .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(50)),
            ) {
                Icon(
                    if (torchOn) Icons.Default.FlashlightOn else Icons.Default.FlashlightOff,
                    contentDescription = tr("المصباح"), tint = Color.White,
                )
            }
        }
    }
}
