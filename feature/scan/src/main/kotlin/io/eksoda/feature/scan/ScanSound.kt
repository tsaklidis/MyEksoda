package io.eksoda.feature.scan

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * A short beep for each saved receipt (android-app.md §6.3.1), only when the ringer is on:
 * silent and vibrate modes stay quiet. The tone generator is made on first use and released
 * with the screen.
 */
internal class ScanSound(private val audio: AudioManager?) {
    private var tone: ToneGenerator? = null

    fun play() {
        if (audio?.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        val generator = tone ?: newGenerator()?.also { tone = it } ?: return
        generator.startTone(ToneGenerator.TONE_PROP_BEEP, DURATION_MS)
    }

    fun release() {
        tone?.release()
        tone = null
    }

    /** Null when the device has no audio output to give (the constructor throws then). */
    private fun newGenerator(): ToneGenerator? =
        runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, VOLUME) }.getOrNull()

    private companion object {
        const val VOLUME = 60
        const val DURATION_MS = 120
    }
}

@Composable
internal fun rememberScanSound(): ScanSound {
    val context = LocalContext.current
    val sound = remember { ScanSound(context.getSystemService(AudioManager::class.java)) }
    DisposableEffect(sound) {
        onDispose { sound.release() }
    }
    return sound
}
