package io.eksoda.feature.scan

import android.util.Size
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import zxingcpp.BarcodeReader

/**
 * How receipt QR codes are read, from the camera and from photos, on the device with zxing-cpp.
 * Its Kotlin options start with every extra effort off (its C++ library starts with them on), so
 * each one is turned on here on purpose. `QrSampleSetTest` (androidTest, `make qr_samples`)
 * measures these choices on 17 hard samples (small, tilted, blurred, faded, glared and worn codes,
 * codes on a screen): the camera's old settings read 11, these read all 17.
 *
 * `tryRotate` is left off: zxing-cpp finds a QR code at any angle, and the option only turns the
 * image for 1D barcodes.
 */
internal object QrReading {
    /**
     * The camera frame size asked for. Unasked, CameraX gives the analyzer 640x480: about two
     * pixels a module for a long receipt link (a 57-module code) printed small or held far, too
     * few to read. 4:3 is the sensor's own shape, so the frames show all the camera sees. A frame
     * has four times the pixels and takes about four times as long with no code in view; the
     * analyzer drops the frames it has no time for (`STRATEGY_KEEP_ONLY_LATEST`).
     */
    val analysisSize = Size(1280, 960)

    /**
     * Every camera frame: receipts carry QR codes only (each other format makes a frame slower).
     * `tryHarder` (finder patterns looked for on more lines) and `tryDownscale` (a smaller copy
     * of the frame read too) each read an out-of-focus code the plain reading misses, and
     * `tryDownscale` a small worn one; together they add about a tenth to a frame's time.
     */
    fun quickOptions(): BarcodeReader.Options = BarcodeReader.Options(
        formats = setOf(BarcodeReader.Format.QR_CODE),
        tryHarder = true,
        tryDownscale = true,
    )

    /**
     * Photos, and every third camera frame: also light-on-dark codes (`tryInvert`, a phone in
     * dark mode) and worn thermal print with gaps in its modules (`tryDenoise`; the larger frame
     * shows the gaps that 640x480 blurred away). About half again a frame's time, so the camera
     * does not pay it on every frame.
     */
    fun thoroughOptions(): BarcodeReader.Options =
        quickOptions().copy(tryInvert = true, tryDenoise = true)

    /**
     * CameraX's analyzer frame size: [analysisSize], or the closest the camera has. A request, not
     * a demand: CameraX gives less when the camera cannot stream it beside the preview (the
     * emulator's camera, whose largest size is 1280x960, falls back to 640x480). Logcat shows the
     * size given: `ImageAnalysis: onSuggestedStreamSpecUpdated`.
     */
    fun analysisResolution(): ResolutionSelector = ResolutionSelector.Builder()
        .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
        .setResolutionStrategy(
            ResolutionStrategy(
                analysisSize,
                ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
            ),
        )
        .build()

    /** The camera's reader: [quickOptions] on most frames, [thoroughOptions] on some. */
    fun cameraReader(): FrameQrReader<ImageProxy> {
        val quick = BarcodeReader(quickOptions())
        val thorough = BarcodeReader(thoroughOptions())
        return FrameQrReader(
            quick = { frame -> quick.read(frame).firstNotNullOfOrNull { it.text } },
            thorough = { frame -> thorough.read(frame).firstNotNullOfOrNull { it.text } },
        )
    }
}
