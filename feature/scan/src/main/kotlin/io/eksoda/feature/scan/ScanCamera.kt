package io.eksoda.feature.scan

import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * The camera preview with a QR analyzer ([QrReading]): zxing-cpp decodes on the device with no
 * network, over the whole frame (the viewfinder only guides the eye). A tap focuses where it
 * lands and two fingers zoom, for a small or distant code. The analyzer thread is released with
 * the composable.
 */
@Composable
internal fun ScanCamera(
    torchOn: Boolean,
    onTorchAvailable: (Boolean) -> Unit,
    onQrCode: (String) -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnQrCode by rememberUpdatedState(onQrCode)
    val currentOnTorchAvailable by rememberUpdatedState(onTorchAvailable)
    val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var focusAt by remember { mutableStateOf<Offset?>(null) }
    val reader = remember { QrReading.cameraReader() }
    val preview = remember { Preview.Builder().build() }
    val analysis = remember {
        ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setResolutionSelector(QrReading.analysisResolution())
            .build()
    }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var active by remember { mutableStateOf(true) }
    DisposableEffect(Unit) {
        // Stop the frames before their thread goes: unbinding first means CameraX never hands a
        // frame to a shut-down executor, and the camera is released as soon as the screen goes.
        onDispose {
            active = false
            analysis.clearAnalyzer()
            cameraProvider?.unbind(preview, analysis)
            analyzerExecutor.shutdown()
        }
    }
    LaunchedEffect(camera, torchOn) {
        camera?.cameraControl?.enableTorch(torchOn)
    }
    LaunchedEffect(focusAt) {
        if (focusAt != null) {
            delay(FOCUS_RING_MILLIS)
            focusAt = null
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                val view = PreviewView(context)
                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                cameraProviderFuture.addListener(
                    {
                        // The screen may be gone before the camera provider is ready.
                        if (!active) return@addListener
                        previewView = view
                        preview.surfaceProvider = view.surfaceProvider
                        analysis.setAnalyzer(analyzerExecutor) { image ->
                            val text = reader.read(image)
                            if (text != null) {
                                ContextCompat.getMainExecutor(context).execute {
                                    currentOnQrCode(text)
                                }
                            }
                        }
                        val provider = cameraProviderFuture.get()
                        cameraProvider = provider
                        val bound = provider.run {
                            unbindAll()
                            bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                analysis,
                            )
                        }
                        camera = bound
                        currentOnTorchAvailable(bound.cameraInfo.hasFlashUnit())
                    },
                    ContextCompat.getMainExecutor(context),
                )
                view
            },
        )
        // Over the preview, under the scan screen's controls: their buttons take their own taps.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val view = previewView ?: return@detectTapGestures
                        val control = camera?.cameraControl ?: return@detectTapGestures
                        val point = view.meteringPointFactory.createPoint(offset.x, offset.y)
                        // Back to continuous focus after a while, for the next receipt.
                        control.startFocusAndMetering(
                            FocusMeteringAction.Builder(point)
                                .setAutoCancelDuration(FOCUS_HOLD_SECONDS, TimeUnit.SECONDS)
                                .build(),
                        )
                        focusAt = offset
                    }
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, zoom, _ ->
                        val bound = camera ?: return@detectTransformGestures
                        val state = bound.cameraInfo.zoomState.value
                        if (state != null && zoom != 1f) {
                            bound.cameraControl.setZoomRatio(
                                (state.zoomRatio * zoom)
                                    .coerceIn(state.minZoomRatio, state.maxZoomRatio),
                            )
                        }
                    }
                },
        )
        focusAt?.let { FocusRing(at = it) }
    }
}

/** Where the tap asked the camera to focus. */
@Composable
private fun FocusRing(at: Offset) {
    Box(
        modifier = Modifier
            .offset {
                val half = FocusRingSize.toPx() / 2
                IntOffset((at.x - half).roundToInt(), (at.y - half).roundToInt())
            }
            .size(FocusRingSize)
            .border(2.dp, Color.White, CircleShape),
    )
}

private val FocusRingSize = 64.dp
private const val FOCUS_RING_MILLIS = 800L
private const val FOCUS_HOLD_SECONDS = 5L
