package io.eksoda.feature.scan

import android.app.Instrumentation
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.Size
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import zxingcpp.BarcodeReader
import java.io.File

/**
 * The hard samples ([QrSamples]) through the real zxing-cpp, at the frame size CameraX gave the
 * analyzer before (640x480) and the one it asks for now ([QrReading.analysisSize]).
 *
 * `make qr_samples` runs it on the emulator or device and prints the report: which settings read
 * which sample, and the time per frame with no code in view (the cost paid on every frame while
 * the user aims). Times compare settings on one device; an emulator runs on the computer's
 * processor, so a phone's are several times longer.
 */
@RunWith(AndroidJUnit4::class)
class QrSampleSetTest {
    private val before = Size(640, 480)
    private val now = QrReading.analysisSize

    private val settings = listOf(
        "camera before (QR only)" to BarcodeReader.Options(
            formats = setOf(BarcodeReader.Format.QR_CODE),
        ),
        "photo before (+harder, rotate, downscale)" to BarcodeReader.Options(
            formats = setOf(BarcodeReader.Format.QR_CODE),
            tryHarder = true,
            tryRotate = true,
            tryDownscale = true,
        ),
        "+harder" to BarcodeReader.Options(
            formats = setOf(BarcodeReader.Format.QR_CODE),
            tryHarder = true,
        ),
        "+downscale" to BarcodeReader.Options(
            formats = setOf(BarcodeReader.Format.QR_CODE),
            tryDownscale = true,
        ),
        "quick (harder, downscale)" to QrReading.quickOptions(),
        "quick +invert" to QrReading.quickOptions().copy(tryInvert = true),
        "quick +denoise" to QrReading.quickOptions().copy(tryDenoise = true),
        "thorough (+invert, denoise)" to QrReading.thoroughOptions(),
    )

    @Test
    fun theCameraReadsEverySampleWithinOneRoundOfFrames() {
        val quick = BarcodeReader(QrReading.quickOptions())
        val thorough = BarcodeReader(QrReading.thoroughOptions())
        val unread = QrSamples.all.filter { scene ->
            val frame = scene.draw(now)
            // The camera tries the quick reading, then the thorough one a few frames later.
            val read = quick.text(frame) ?: thorough.text(frame)
            frame.recycle()
            read != scene.text
        }
        assertThat(unread.map { it.name }).isEmpty()
    }

    @Test
    fun aPhotoOfEverySampleIsRead() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val decoder = QrImageDecoder(context, Dispatchers.IO, Dispatchers.Default)
        val unread = QrSamples.all.filter { scene ->
            val file = File(context.cacheDir, "qr-sample.png")
            val frame = scene.draw(now)
            file.outputStream().use { frame.compress(Bitmap.CompressFormat.PNG, 100, it) }
            frame.recycle()
            decoder.decode(Uri.fromFile(file)) != scene.text
        }
        assertThat(unread.map { it.name }).isEmpty()
    }

    @Test
    fun aFrameWithNoCodeReadsAsNothing() {
        val thorough = BarcodeReader(QrReading.thoroughOptions())
        val frame = QrSamples.noCode(now)
        assertThat(thorough.text(frame)).isEqualTo(null)
        frame.recycle()
    }

    /**
     * Not a check: the numbers behind [QrReading]'s choices. One column per setting, each cell
     * "640x480 1280x960": ✓ read, · not read.
     */
    @Test
    fun report() {
        val sizes = listOf(before, now)
        val readers = settings.map { (_, options) -> BarcodeReader(options) }
        val report = StringBuilder("\nQR sample set at ${sizes.joinToString(" ") { it.label }}\n")
        settings.forEachIndexed { i, (name, _) -> report.append("  ${i + 1} $name\n") }
        report.append("%-28s".format(""))
        readers.indices.forEach { report.append("%-7s".format("${it + 1}")) }
        report.append('\n')
        val counts = Array(readers.size) { IntArray(sizes.size) }
        for (scene in QrSamples.all) {
            val frames = sizes.map { scene.draw(it) }
            report.append("%-28s".format(scene.name))
            readers.forEachIndexed { r, reader ->
                val marks = frames.mapIndexed { s, frame ->
                    val read = reader.text(frame) == scene.text
                    if (read) counts[r][s]++
                    if (read) "✓" else "·"
                }
                report.append("%-7s".format(marks.joinToString(" ")))
            }
            report.append('\n')
            frames.forEach(Bitmap::recycle)
        }
        report.append("%-28s".format("read of ${QrSamples.all.size}"))
        counts.forEach { report.append("%-7s".format(it.joinToString("/"))) }
        report.append('\n')
        for (size in sizes) {
            val empty = QrSamples.noCode(size)
            report.append("%-28s".format("ms, no code, ${size.label}"))
            readers.forEach { report.append("%-7s".format(millis(it, empty))) }
            report.append('\n')
            empty.recycle()
        }
        InstrumentationRegistry.getInstrumentation().sendStatus(
            0,
            Bundle().apply {
                putString(Instrumentation.REPORT_KEY_STREAMRESULT, report.toString())
            },
        )
    }

    private fun BarcodeReader.text(frame: Bitmap): String? =
        read(frame).firstNotNullOfOrNull { it.text }

    /** The median of a few reads, after one to warm up. */
    private fun millis(reader: BarcodeReader, frame: Bitmap): String {
        reader.read(frame)
        val times = List(TIMED_READS) {
            val start = System.nanoTime()
            reader.read(frame)
            (System.nanoTime() - start) / NANOS_PER_MILLI
        }
        return "%.1f".format(times.sorted()[TIMED_READS / 2])
    }

    private val Size.label get() = "${width}x$height"

    private companion object {
        const val TIMED_READS = 9
        const val NANOS_PER_MILLI = 1_000_000.0
    }
}
