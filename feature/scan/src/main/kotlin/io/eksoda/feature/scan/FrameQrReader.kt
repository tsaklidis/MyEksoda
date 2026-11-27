package io.eksoda.feature.scan

/**
 * Reads the QR text in camera frames, one frame at a time, and closes every frame it is given.
 * Most frames get the [quick] reading; every [thoroughEvery]-th frame gets the [thorough] one,
 * which also finds light-on-dark codes and worn print but costs more per frame
 * (`QrSampleSetTest` measures both).
 *
 * A frame the reader cannot take counts as a frame with no code: an exception thrown on the
 * analyzer thread would end it, and the app with it. zxing-cpp reports such a frame with a
 * [RuntimeException] (its native code throws only that; its Kotlin wrapper an
 * [IllegalStateException] for an unsupported image format). An [Error] such as
 * [OutOfMemoryError] is not a bad frame, so it is not caught. Not thread safe: the camera
 * analyzer calls it from its one thread.
 */
internal class FrameQrReader<F : AutoCloseable>(
    private val quick: (F) -> String?,
    private val thorough: (F) -> String?,
    private val thoroughEvery: Int = THOROUGH_EVERY,
) {
    private var count = 0

    init {
        require(thoroughEvery >= 1) { "thoroughEvery must be at least 1" }
    }

    /** The text of the first QR code in [frame], or null. The frame is closed either way. */
    @Suppress("TooGenericExceptionCaught")
    fun read(frame: F): String? = frame.use {
        count = (count + 1) % thoroughEvery
        val reading = if (count == 0) thorough else quick
        try {
            reading(it)
        } catch (ignored: RuntimeException) {
            null
        }
    }

    companion object {
        /** At 30 frames a second, the thorough reading runs ten times a second. */
        const val THOROUGH_EVERY = 3
    }
}
