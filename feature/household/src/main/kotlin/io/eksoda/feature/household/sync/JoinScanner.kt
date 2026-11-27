package io.eksoda.feature.household.sync

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.eksoda.feature.household.R
import zxingcpp.BarcodeReader
import java.util.concurrent.Executors

/**
 * Reads the household code on another phone's screen. The camera is asked for at once (the user
 * tapped "Join"), and nothing it sees is kept: only the decoded text goes to [onCode].
 */
@Composable
internal fun JoinScanner(onCode: (String) -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    var hasPermission by remember { mutableStateOf(cameraGranted(context)) }
    var asked by rememberSaveable { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasPermission = granted
        asked = true
    }
    LaunchedEffect(Unit) {
        if (!hasPermission && !asked) permission.launch(Manifest.permission.CAMERA)
    }
    // Granted in the system settings meanwhile: check again on return.
    LifecycleResumeEffect(Unit) {
        hasPermission = cameraGranted(context)
        onPauseOrDispose { }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color.Black, contentColor = Color.White) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (hasPermission) {
                CameraPreview(onCode = onCode)
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(240.dp)
                        .border(2.dp, Color.White, RoundedCornerShape(24.dp)),
                )
            } else {
                CameraRationale(
                    denied = asked,
                    onAllow = { permission.launch(Manifest.permission.CAMERA) },
                    onOpenSettings = {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", context.packageName, null),
                            ),
                        )
                    },
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    stringResource(R.string.sync_join_title),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
                Text(
                    stringResource(R.string.sync_join_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                TextButton(onClick = onCancel) {
                    Text(stringResource(R.string.sync_cancel), color = Color.White)
                }
            }
        }
    }
}

private fun cameraGranted(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED

@Composable
private fun CameraRationale(
    denied: Boolean,
    onAllow: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            stringResource(R.string.sync_camera_rationale),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onAllow) { Text(stringResource(R.string.sync_camera_allow)) }
        if (denied) {
            TextButton(onClick = onOpenSettings) {
                Text(stringResource(R.string.sync_camera_settings), color = Color.White)
            }
        }
    }
}

/** The back camera with a QR analyzer (zxing-cpp, on the device), released with the screen. */
@Composable
private fun CameraPreview(onCode: (String) -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnCode by rememberUpdatedState(onCode)
    val executor = remember { Executors.newSingleThreadExecutor() }
    val reader = remember {
        BarcodeReader(BarcodeReader.Options(formats = setOf(BarcodeReader.Format.QR_CODE)))
    }
    val preview = remember { Preview.Builder().build() }
    val analysis = remember {
        ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
    }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var active by remember { mutableStateOf(true) }
    DisposableEffect(Unit) {
        // Unbind before the analyzer thread goes, so CameraX never hands it another frame.
        onDispose {
            active = false
            analysis.clearAnalyzer()
            cameraProvider?.unbind(preview, analysis)
            executor.shutdown()
        }
    }
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            val previewView = PreviewView(context)
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener(
                {
                    if (!active) return@addListener
                    preview.surfaceProvider = previewView.surfaceProvider
                    analysis.setAnalyzer(executor) { image ->
                        // The frame is closed either way.
                        val text = image.use { frame -> reader.textOrNull(frame) }
                        if (text != null) {
                            ContextCompat.getMainExecutor(context).execute { currentOnCode(text) }
                        }
                    }
                    val provider = future.get()
                    cameraProvider = provider
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis,
                    )
                },
                ContextCompat.getMainExecutor(context),
            )
            previewView
        },
    )
}

/**
 * The text of the first QR code in [frame], or null. A frame the reader cannot take is skipped:
 * an exception here would end the analyzer thread, and the app with it. zxing-cpp reports such a
 * frame with a [RuntimeException]; an [Error] such as [OutOfMemoryError] is not a bad frame, so it
 * is not caught.
 */
@Suppress("TooGenericExceptionCaught")
private fun BarcodeReader.textOrNull(frame: ImageProxy): String? = try {
    read(frame).firstNotNullOfOrNull { it.text }
} catch (ignored: RuntimeException) {
    null
}
