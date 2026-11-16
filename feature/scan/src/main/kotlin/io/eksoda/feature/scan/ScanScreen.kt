package io.eksoda.feature.scan

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.eksoda.core.designsystem.theme.EksodaTheme
import kotlinx.coroutines.delay
import zxingcpp.BarcodeReader
import java.util.concurrent.Executors

@Composable
fun ScanRoute(
    onClose: () -> Unit,
    onShowImports: () -> Unit,
    onViewReceipt: (String) -> Unit,
    viewModel: ScanViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    val sound = rememberScanSound()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                ScanEffect.Saved -> {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    sound.play()
                }
            }
        }
    }
    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> uri?.let { viewModel.onEvent(ScanEvent.PhotoPicked(it)) } }

    ScanScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onClose = onClose,
        onDone = if (state.sentCount > 0) onShowImports else onClose,
        onShowImports = onShowImports,
        onViewReceipt = onViewReceipt,
        onPickPhoto = {
            photoPicker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
        },
    )
}

/** The camera screen (android-app.md §6.3.1). */
@Composable
fun ScanScreen(
    state: ScanUiState,
    onEvent: (ScanEvent) -> Unit,
    onClose: () -> Unit,
    onDone: () -> Unit,
    onShowImports: () -> Unit,
    onViewReceipt: (String) -> Unit,
    onPickPhoto: () -> Unit,
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var asked by rememberSaveable { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasPermission = granted
        asked = true
    }
    // The user opened the scanner to scan: ask at once instead of after one more tap.
    LaunchedEffect(Unit) {
        if (!hasPermission && !asked) permissionLauncher.launch(Manifest.permission.CAMERA)
    }
    // Granted in the system settings meanwhile (or revoked): check again on return.
    LifecycleResumeEffect(Unit) {
        hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        onPauseOrDispose { }
    }
    var torchAvailable by remember { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (hasPermission) {
                CameraPreview(
                    torchOn = state.torchOn,
                    onTorchAvailable = { torchAvailable = it },
                    onQrCode = { onEvent(ScanEvent.CodeScanned(it)) },
                )
                Viewfinder(modifier = Modifier.align(Alignment.Center))
            } else {
                CameraRationale(
                    denied = asked,
                    onAllow = { permissionLauncher.launch(Manifest.permission.CAMERA) },
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
            Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
                TopBar(onClose = onClose, onShowImports = onShowImports)
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (state.readingPhoto) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                    FeedbackChip(
                        feedback = state.feedback,
                        onView = onViewReceipt,
                        onShown = { onEvent(ScanEvent.FeedbackShown) },
                        modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                    )
                }
                BottomBar(
                    state = state,
                    torchAvailable = hasPermission && torchAvailable,
                    onEvent = onEvent,
                    onPickPhoto = onPickPhoto,
                    onDone = onDone,
                )
            }
        }
    }

    if (state.pasteOpen) {
        PasteLinkDialog(
            onImport = { onEvent(ScanEvent.LinkPasted(it)) },
            onDismiss = { onEvent(ScanEvent.PasteDismissed) },
        )
    }
}

@Composable
private fun TopBar(onClose: () -> Unit, onShowImports: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(onClick = onClose) {
            Icon(Icons.Default.Close, stringResource(R.string.scan_close), tint = Color.White)
        }
        IconButton(onClick = onShowImports) {
            Icon(Icons.Default.History, stringResource(R.string.scan_imports), tint = Color.White)
        }
    }
}

@Composable
private fun Viewfinder(modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(260.dp)
                .border(BorderStroke(3.dp, Color.White), RoundedCornerShape(24.dp)),
        )
        Text(
            text = stringResource(R.string.scan_hint),
            color = Color.White,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(top = 16.dp)
                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

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
            text = stringResource(R.string.scan_camera_rationale),
            color = Color.White,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        if (denied) {
            Text(
                text = stringResource(R.string.scan_camera_denied),
                color = Color.White.copy(alpha = 0.8f),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
        Button(onClick = onAllow) { Text(stringResource(R.string.scan_camera_allow)) }
        // After "Don't allow" twice Android stops asking; the setting is the only way back.
        if (denied) {
            TextButton(onClick = onOpenSettings) {
                Text(stringResource(R.string.scan_camera_settings), color = Color.White)
            }
        }
    }
}

@Composable
private fun BottomBar(
    state: ScanUiState,
    torchAvailable: Boolean,
    onEvent: (ScanEvent) -> Unit,
    onPickPhoto: () -> Unit,
    onDone: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row {
            if (torchAvailable) {
                IconButton(onClick = { onEvent(ScanEvent.TorchToggled) }) {
                    Icon(
                        imageVector = if (state.torchOn) {
                            Icons.Default.FlashlightOff
                        } else {
                            Icons.Default.FlashlightOn
                        },
                        contentDescription = stringResource(
                            if (state.torchOn) R.string.scan_torch_off else R.string.scan_torch_on,
                        ),
                        tint = Color.White,
                    )
                }
            }
            IconButton(onClick = { onEvent(ScanEvent.PasteOpened) }) {
                Icon(
                    imageVector = Icons.Default.ContentPaste,
                    contentDescription = stringResource(R.string.scan_paste),
                    tint = Color.White,
                )
            }
            IconButton(onClick = onPickPhoto, enabled = !state.readingPhoto) {
                Icon(
                    imageVector = Icons.Default.PhotoLibrary,
                    contentDescription = stringResource(R.string.scan_photo),
                    tint = Color.White,
                )
            }
        }
        Button(onClick = onDone) {
            Text(
                if (state.sentCount > 0) {
                    stringResource(R.string.scan_done_count, state.sentCount)
                } else {
                    stringResource(R.string.scan_done)
                },
            )
        }
    }
}

/** Slides up after each submission; stays longer when it offers "View". */
@Composable
private fun FeedbackChip(
    feedback: ImportFeedback?,
    onView: (String) -> Unit,
    onShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(feedback) {
        if (feedback != null) {
            delay(feedback.visibleMillis())
            onShown()
        }
    }
    // The last feedback stays drawn while the chip fades out; without it the chip would be
    // empty during the exit animation and vanish at once.
    var shown by remember { mutableStateOf(feedback) }
    if (feedback != null) shown = feedback
    AnimatedVisibility(
        visible = feedback != null,
        enter = slideInVertically { it },
        exit = fadeOut(),
        modifier = modifier,
    ) {
        val current = shown ?: return@AnimatedVisibility
        // Errors in Material's error colors; everything else on the neutral inset color.
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = if (current.isError) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                EksodaTheme.colors.inset
            },
            contentColor = if (current.isError) {
                MaterialTheme.colorScheme.onErrorContainer
            } else {
                EksodaTheme.colors.ink
            },
            border = BorderStroke(
                1.dp,
                if (current.isError) Color.Transparent else EksodaTheme.colors.line,
            ),
        ) {
            Row(
                modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = feedbackText(current),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 8.dp).weight(1f, fill = false),
                )
                if (current is ImportFeedback.AlreadyImported) {
                    TextButton(onClick = { onView(current.receiptId) }) {
                        Text(stringResource(R.string.feedback_view))
                    }
                }
            }
        }
    }
}

/** "That QR code isn't a receipt" shows for 2 seconds (android-app.md §6.3.1). */
private const val SHORT_FEEDBACK_MILLIS = 2_000L
private const val FEEDBACK_MILLIS = 4_000L
private const val FEEDBACK_WITH_ACTION_MILLIS = 6_000L

private fun ImportFeedback.visibleMillis(): Long = when (this) {
    ImportFeedback.NotAReceipt -> SHORT_FEEDBACK_MILLIS
    is ImportFeedback.AlreadyImported -> FEEDBACK_WITH_ACTION_MILLIS
    else -> FEEDBACK_MILLIS
}

@Composable
private fun PasteLinkDialog(onImport: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.paste_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.paste_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onImport(text) }, enabled = text.isNotBlank()) {
                Text(stringResource(R.string.paste_import))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.scan_cancel)) }
        },
    )
}

/**
 * The camera preview with a QR analyzer. zxing-cpp decodes on the device with no network
 * (docs/android-standalone.md §1.3). The analyzer thread is released with the composable.
 */
@Composable
private fun CameraPreview(
    torchOn: Boolean,
    onTorchAvailable: (Boolean) -> Unit,
    onQrCode: (String) -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnQrCode by rememberUpdatedState(onQrCode)
    val currentOnTorchAvailable by rememberUpdatedState(onTorchAvailable)
    val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }
    var camera by remember { mutableStateOf<Camera?>(null) }
    val reader = remember {
        // Receipts carry QR codes only. Looking for other formats makes each frame slower.
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

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            val previewView = PreviewView(context)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener(
                {
                    // The screen may be gone before the camera provider is ready.
                    if (!active) return@addListener
                    preview.surfaceProvider = previewView.surfaceProvider
                    analysis.setAnalyzer(analyzerExecutor) { image ->
                        val text = image.use { reader.read(it) }.firstNotNullOfOrNull { it.text }
                        if (text != null) {
                            ContextCompat.getMainExecutor(context).execute { currentOnQrCode(text) }
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
            previewView
        },
    )
}
