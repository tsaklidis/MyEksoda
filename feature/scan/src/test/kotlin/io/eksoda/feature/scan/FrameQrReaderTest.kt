package io.eksoda.feature.scan

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isFailure
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import assertk.assertions.isTrue
import org.junit.Test

class FrameQrReaderTest {
    private class Frame(val text: String? = null, val broken: Boolean = false) : AutoCloseable {
        var closed = false

        override fun close() {
            closed = true
        }
    }

    private val readings = mutableListOf<String>()

    private fun reader(thoroughEvery: Int = 3) = FrameQrReader<Frame>(
        quick = { frame ->
            readings += "quick"
            check(!frame.broken) { "the reader cannot take this frame" }
            frame.text
        },
        thorough = { frame ->
            readings += "thorough"
            frame.text
        },
        thoroughEvery = thoroughEvery,
    )

    @Test
    fun `a code in the frame is read, and the frame closed`() {
        val frame = Frame("https://receipts.example.gr/v/1")

        assertThat(reader().read(frame)).isEqualTo("https://receipts.example.gr/v/1")
        assertThat(frame.closed).isTrue()
    }

    @Test
    fun `a frame with no code reads as null, and is closed`() {
        val frame = Frame()

        assertThat(reader().read(frame)).isNull()
        assertThat(frame.closed).isTrue()
    }

    @Test
    fun `a frame the decoder throws on is skipped and closed, and the next frame is still read`() {
        val reader = reader()
        val broken = Frame("https://receipts.example.gr/v/1", broken = true)

        assertThat(reader.read(broken)).isNull()
        assertThat(broken.closed).isTrue()
        assertThat(reader.read(Frame("https://receipts.example.gr/v/2")))
            .isEqualTo("https://receipts.example.gr/v/2")
    }

    @Test
    fun `every third frame gets the thorough reading`() {
        val reader = reader()

        repeat(6) { reader.read(Frame()) }

        assertThat(readings).containsExactly(
            "quick",
            "quick",
            "thorough",
            "quick",
            "quick",
            "thorough",
        )
    }

    @Test
    fun `thorough on every frame when asked`() {
        val reader = reader(thoroughEvery = 1)

        repeat(2) { reader.read(Frame()) }

        assertThat(readings).containsExactly("thorough", "thorough")
    }

    @Test
    fun `a round of no frames is refused`() {
        assertThat(runCatching { reader(thoroughEvery = 0) })
            .isFailure()
            .isInstanceOf(IllegalArgumentException::class)
    }
}
