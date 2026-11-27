package io.eksoda.feature.scan

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.util.Size
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Hard receipt QR codes, drawn as a camera would see them. Real failing frames cannot be kept in
 * the repository (a receipt link works like a password), so each sample is a made-up link of a
 * real link's length, made hard to read in one way.
 *
 * A [Scene] is in units of the frame, so drawing it at two frame sizes is the same receipt seen
 * by the same camera at two resolutions: optical blur covers the same share of the code, and only
 * the pixels per module change.
 */
internal object QrSamples {
    /** As long as the longest receipt links (Entersoft, AADE): a version 10 code, 57 modules. */
    const val LONG_LINK = "https://receipts.example.gr/v/EX094019245-0001234567-" +
        "0123456789abcdef0123456789abcdef01234567?d=20261127&t=124005&n=0001234567" +
        "&s=00000000000000000000000000000000000000000000000000000000000000000000"

    /** As long as an Impact link: a version 6 code. */
    const val SHORT_LINK =
        "https://receipts.example.gr/v/EX094019245-1234567-0123456789abcdef0123456789abcdef01234567"

    val all: List<Scene> = listOf(
        Scene("clean"),
        Scene("small, held far", span = 0.15f),
        Scene("small, short link", text = SHORT_LINK, span = 0.11f),
        Scene("small and soft", span = 0.2f, defocus = 0.001f),
        Scene("rotated 35°", degrees = 35f),
        Scene("upside down", degrees = 180f),
        Scene("tilted away", degrees = 8f, tilt = 0.35f),
        Scene("out of focus", defocus = 0.0025f),
        Scene("motion blur", motion = 0.005f),
        Scene("faded print", ink = 140, paper = 178, noise = 6),
        Scene("glare", glare = true),
        Scene("held close, soft", span = 0.75f, defocus = 0.004f),
        // Gaps a quarter of a module wide: 640x480 blurs them away, 1280x960 sees them.
        Scene("worn print", worn = 0.15f),
        Scene("worn and small", span = 0.25f, worn = 0.15f),
        Scene("grainy, low light", span = 0.3f, noise = 40),
        Scene("on a screen", screen = true),
        Scene("on a screen, dark mode", screen = true, inverted = true),
    )

    /** What the camera sees with no code in view: lines of print on a receipt. */
    fun noCode(size: Size): Bitmap {
        val random = Random(SEED)
        val gray = IntArray(size.width * size.height) { PAPER }
        val glyph = (size.width * 0.012f).roundToInt().coerceAtLeast(2)
        var y = glyph
        while (y < size.height - glyph * 2) {
            var x = glyph
            while (x < size.width - glyph * 2) {
                if (random.nextInt(5) !=
                    0
                ) {
                    fillRect(gray, size.width, x, y, glyph - 1, glyph * 3 / 2)
                }
                x += glyph + random.nextInt(2)
            }
            y += glyph * 3
        }
        return toBitmap(gray, size)
    }

    /**
     * A receipt's QR code in a camera frame. [span] is the code's width as a share of the frame
     * width (how far away it is); [defocus] and [motion] are blur lengths, [glare] a reflection
     * blowing out part of the code, [worn] the share of the code covered in sub-module gaps
     * (thermal print), [screen] a display's pixel grid (moiré) with its dark surround.
     */
    data class Scene(
        val name: String,
        val text: String = LONG_LINK,
        val span: Float = 0.42f,
        val degrees: Float = 0f,
        val tilt: Float = 0f,
        val ink: Int = INK,
        val paper: Int = PAPER,
        val inverted: Boolean = false,
        val defocus: Float = 0f,
        val motion: Float = 0f,
        val glare: Boolean = false,
        val noise: Int = SENSOR_NOISE,
        val worn: Float = 0f,
        val screen: Boolean = false,
    ) {
        fun draw(size: Size): Bitmap {
            val w = size.width
            val h = size.height
            val dark = if (inverted) paper else ink
            val light = if (inverted) ink else paper
            val frame = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            Canvas(frame).apply {
                drawColor(argb(if (screen) SCREEN_SURROUND else paper))
                drawBitmap(code(dark, light), placement(w, h), Paint(Paint.FILTER_BITMAP_FLAG))
            }
            val gray = IntArray(w * h).also { frame.getPixels(it, 0, w, 0, 0, w, h) }
            for (i in gray.indices) gray[i] = gray[i] and 0xFF
            frame.recycle()
            if (screen) pixelGrid(gray, w)
            if (glare) glare(gray, w, h)
            blur(gray, w, h, radius = (defocus * w).roundToInt(), vertical = true)
            blur(gray, w, h, radius = (motion * w / 2).roundToInt(), vertical = false)
            // The lens and the sensor's color filter soften every frame by about a pixel.
            boxPass(gray, w, h, radius = 1, horizontal = true)
            boxPass(gray, w, h, radius = 1, horizontal = false)
            if (noise > 0) {
                val random = Random(SEED)
                for (i in gray.indices) gray[i] += random.nextInt(-noise, noise + 1)
            }
            return toBitmap(gray, size)
        }

        /** The code at [MODULE] pixels a module, quiet zone included. */
        private fun code(dark: Int, light: Int): Bitmap {
            val matrix = QRCodeWriter().encode(
                text,
                BarcodeFormat.QR_CODE,
                0,
                0,
                mapOf(
                    EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                    EncodeHintType.MARGIN to QUIET_ZONE,
                ),
            )
            val side = matrix.width * MODULE
            val pixels = IntArray(side * side) { i ->
                if (matrix[(i % side) / MODULE, (i / side) / MODULE]) dark else light
            }
            if (worn > 0f) {
                // Specks a quarter of a module wide, where the print head missed.
                val random = Random(SEED)
                repeat((side * side * worn / (WORN_SPECK * WORN_SPECK)).toInt()) {
                    fillRect(
                        pixels,
                        side,
                        random.nextInt(side - WORN_SPECK),
                        random.nextInt(side - WORN_SPECK),
                        WORN_SPECK,
                        WORN_SPECK,
                        light,
                    )
                }
            }
            return toBitmap(pixels, Size(side, side))
        }

        /** Centered, [span] of the frame wide, turned by [degrees], its top edge [tilt] narrower. */
        private fun placement(w: Int, h: Int): Matrix {
            val source = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0).width * MODULE
            val half = span * w / 2
            val inset = half * tilt
            val corners = floatArrayOf(
                -half + inset,
                -half,
                half - inset,
                -half,
                half,
                half,
                -half,
                half,
            )
            val angle = degrees * PI / 180
            val target = FloatArray(corners.size)
            for (i in corners.indices step 2) {
                val x = corners[i]
                val y = corners[i + 1]
                target[i] = (w / 2 + x * cos(angle) - y * sin(angle)).toFloat()
                target[i + 1] = (h / 2 + x * sin(angle) + y * cos(angle)).toFloat()
            }
            val s = source.toFloat()
            return Matrix().apply {
                setPolyToPoly(floatArrayOf(0f, 0f, s, 0f, s, s, 0f, s), 0, target, 0, 4)
            }
        }

        /** A display's pixel rows, finer than the camera's pixels: they beat into moiré. */
        private fun pixelGrid(gray: IntArray, w: Int) {
            val period = w * SCREEN_PITCH
            for (i in gray.indices) {
                val wave = sin(2 * PI * ((i % w) * 0.94 + (i / w) * 0.34) / period)
                gray[i] = (gray[i] * (1 - SCREEN_DEPTH * (0.5 + 0.5 * wave))).roundToInt()
            }
        }

        /** A lamp's reflection: a bright spot over a corner of the code, blown out at its center. */
        private fun glare(gray: IntArray, w: Int, h: Int) {
            val cx = w / 2 + span * w * 0.15f
            val cy = h / 2 - span * w * 0.12f
            val sigma = span * w * 0.12f
            for (i in gray.indices) {
                val dx = (i % w) - cx
                val dy = (i / w) - cy
                gray[i] += (GLARE * exp(-(dx * dx + dy * dy) / (2 * sigma * sigma))).roundToInt()
            }
        }
    }

    /** Three box passes, close to a Gaussian of the same width; one direction for motion. */
    private fun blur(gray: IntArray, w: Int, h: Int, radius: Int, vertical: Boolean) {
        if (radius <= 0) return
        repeat(BLUR_PASSES) {
            boxPass(gray, w, h, radius, horizontal = true)
            if (vertical) boxPass(gray, w, h, radius, horizontal = false)
        }
    }

    private fun boxPass(gray: IntArray, w: Int, h: Int, radius: Int, horizontal: Boolean) {
        val lines = if (horizontal) h else w
        val length = if (horizontal) w else h
        val line = IntArray(length)
        for (l in 0 until lines) {
            fun index(p: Int) = if (horizontal) l * w + p else p * w + l
            for (p in 0 until length) line[p] = gray[index(p)]
            var sum = 0
            for (p in -radius..radius) sum += line[p.coerceIn(0, length - 1)]
            for (p in 0 until length) {
                gray[index(p)] = sum / (2 * radius + 1)
                sum += line[(p + radius + 1).coerceAtMost(length - 1)] -
                    line[(p - radius).coerceAtLeast(0)]
            }
        }
    }

    private fun fillRect(
        pixels: IntArray,
        width: Int,
        x: Int,
        y: Int,
        w: Int,
        h: Int,
        value: Int = INK,
    ) {
        for (row in y until y + h) for (col in x until x + w) pixels[row * width + col] = value
    }

    private fun toBitmap(gray: IntArray, size: Size): Bitmap {
        val argb = IntArray(gray.size) { argb(gray[it].coerceIn(0, MAX_GRAY)) }
        return Bitmap.createBitmap(argb, size.width, size.height, Bitmap.Config.ARGB_8888)
    }

    private fun argb(gray: Int): Int = (0xFF shl 24) or (gray shl 16) or (gray shl 8) or gray

    private const val SEED = 31
    private const val SENSOR_NOISE = 8
    private const val INK = 35
    private const val PAPER = 222
    private const val MAX_GRAY = 255
    private const val SCREEN_SURROUND = 18
    private const val MODULE = 8
    private const val QUIET_ZONE = 4
    private const val WORN_SPECK = 2
    private const val BLUR_PASSES = 3
    private const val GLARE = 260
    private const val SCREEN_PITCH = 0.0021
    private const val SCREEN_DEPTH = 0.35
}
