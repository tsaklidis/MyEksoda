package io.eksoda.feature.scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import io.eksoda.core.data.di.DefaultDispatcher
import io.eksoda.core.data.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import zxingcpp.BarcodeReader
import javax.inject.Inject

/**
 * Reads a receipt QR code from a gallery photo with zxing-cpp, on the device. The photo
 * is scaled down while decoding, so a large picture does not exhaust memory.
 */
class QrImageDecoder @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) {
    /** A photo is decoded once, so the slower, more thorough options are worth it. */
    private val reader by lazy {
        BarcodeReader(
            BarcodeReader.Options(
                formats = setOf(BarcodeReader.Format.QR_CODE),
                tryHarder = true,
                tryRotate = true,
                tryDownscale = true,
            ),
        )
    }

    /** The text of the first QR code in the photo, or null. */
    suspend fun decode(uri: Uri): String? {
        val bitmap = withContext(ioDispatcher) { load(uri) } ?: return null
        return withContext(defaultDispatcher) {
            try {
                reader.read(bitmap).firstNotNullOfOrNull { it.text }
            } finally {
                bitmap.recycle()
            }
        }
    }

    private fun load(uri: Uri): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_SIDE) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }

    private companion object {
        /** Large enough for a receipt QR code in a photo of the whole receipt. */
        const val MAX_SIDE = 2048
    }
}
