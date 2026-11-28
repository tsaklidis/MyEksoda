package io.eksoda.feature.scan

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import io.eksoda.core.model.ImportSubmission
import io.eksoda.core.testing.FakeReceiptRepository
import io.eksoda.core.testing.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Photos, and the contract the three ways in share: the camera skips a link already sent in the
 * session, a photo or a paste always sends it (the user asked), and a link whose submission
 * failed unexpectedly (nothing was saved) can be tried again by any of the three. On a standard
 * dispatcher, so the steps in between (reading the photo) are seen.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class ScanPhotoTest {
    @get:Rule
    val main = MainDispatcherRule(StandardTestDispatcher())

    private val receipts = FakeReceiptRepository()

    /** What each photo holds: its code's text, or an error reading it. */
    private val photos = mutableMapOf<Uri, Result<String?>>()
    private val viewModel = ScanViewModel(receipts) { uri -> photos.getValue(uri).getOrThrow() }
    private val state get() = viewModel.state.value

    private fun photo(content: Result<String?>): Uri =
        Uri.parse("content://media/photo/${photos.size}").also { photos[it] = content }

    private fun pick(content: Result<String?>) =
        viewModel.onEvent(ScanEvent.PhotoPicked(photo(content)))

    private fun scan(text: String) = viewModel.onEvent(ScanEvent.CodeScanned(text))

    @Test
    fun `a photo with a receipt code submits its link, and says so while reading`() = runTest {
        val gate = CompletableDeferred<String?>()
        val slow = ScanViewModel(receipts) { gate.await() }
        slow.effects.test {
            slow.onEvent(ScanEvent.PhotoPicked(Uri.parse("content://media/photo/slow")))
            runCurrent()
            assertThat(slow.state.value.readingPhoto).isTrue()
            assertThat(receipts.submitted).isEqualTo(emptyList())
            gate.complete(LINK)
            advanceUntilIdle()
            assertThat(awaitItem()).isEqualTo(ScanEffect.Saved)
        }
        assertThat(slow.state.value.readingPhoto).isFalse()
        assertThat(slow.state.value.feedback).isEqualTo(ImportFeedback.Importing(1))
        assertThat(receipts.submitted).containsExactly(LINK)
    }

    @Test
    fun `a photo's receipt saved offline is counted like a scanned one`() = runTest {
        receipts.submission = { ImportSubmission.Queued("job", offline = true) }
        viewModel.effects.test {
            pick(Result.success(LINK))
            advanceUntilIdle()
            assertThat(awaitItem()).isEqualTo(ScanEffect.Saved)
        }
        assertThat(state.feedback).isEqualTo(ImportFeedback.SavedOffline(1))
        assertThat(state.sentCount).isEqualTo(1)
    }

    @Test
    fun `a photo whose code is not an https link is not submitted`() = runTest {
        for (text in listOf("http://receipts.example.gr/v/1", "WIFI:S:home;;", "https://a b")) {
            pick(Result.success(text))
            advanceUntilIdle()
            assertThat(state.feedback, name = text).isEqualTo(ImportFeedback.NotAReceipt)
        }
        assertThat(receipts.submitted).isEqualTo(emptyList())
        assertThat(state.readingPhoto).isFalse()
    }

    @Test
    fun `a photo that cannot be read is a photo without a code, not a crash`() = runTest {
        pick(Result.failure(IllegalStateException("the decoder cannot take this image")))
        advanceUntilIdle()
        assertThat(state.feedback).isEqualTo(ImportFeedback.NoCodeInPhoto)
        assertThat(state.readingPhoto).isFalse()
        // The screen still works: the next photo is read.
        pick(Result.success(LINK))
        advanceUntilIdle()
        assertThat(receipts.submitted).containsExactly(LINK)
    }

    @Test
    fun `a photo whose import failed can be tried again by photo or camera`() = runTest {
        receipts.failNext("importReceipt")
        pick(Result.success(LINK))
        advanceUntilIdle()
        assertThat(state.feedback).isEqualTo(ImportFeedback.Failed)
        assertThat(state.sentCount).isEqualTo(0)

        // Nothing was saved, so the camera does not skip the code.
        scan(LINK)
        advanceUntilIdle()
        assertThat(state.feedback).isEqualTo(ImportFeedback.Importing(1))
        assertThat(receipts.calls).containsExactly("importReceipt($LINK)", "importReceipt($LINK)")

        // A failed photo can also be picked again.
        receipts.failNext("importReceipt")
        pick(Result.success(OTHER_LINK))
        advanceUntilIdle()
        pick(Result.success(OTHER_LINK))
        advanceUntilIdle()
        assertThat(state.feedback).isEqualTo(ImportFeedback.Importing(2))
    }

    @Test
    fun `a pasted link whose import failed can be tried again by the camera`() = runTest {
        receipts.failNext("importReceipt")
        viewModel.onEvent(ScanEvent.LinkPasted(LINK))
        advanceUntilIdle()
        assertThat(state.feedback).isEqualTo(ImportFeedback.Failed)
        scan(LINK)
        advanceUntilIdle()
        assertThat(state.feedback).isEqualTo(ImportFeedback.Importing(1))
        assertThat(receipts.submitted).containsExactly(LINK, LINK)
    }

    @Test
    fun `the camera skips a link already sent, a photo or a paste sends it again`() = runTest {
        val sent = mutableSetOf<String>()
        receipts.submission = { link ->
            if (sent.add(
                    link,
                )
            ) {
                ImportSubmission.Queued(
                    "job-$link",
                )
            } else {
                ImportSubmission.AlreadyPending("job-$link")
            }
        }
        viewModel.onEvent(ScanEvent.LinkPasted(LINK))
        advanceUntilIdle()
        scan(LINK)
        advanceUntilIdle()
        assertThat(receipts.submitted).containsExactly(LINK)

        // Asked for again by the user: sent, and the repository says it is on its way.
        pick(Result.success(LINK))
        advanceUntilIdle()
        assertThat(state.feedback).isEqualTo(ImportFeedback.AlreadyPending)
        viewModel.onEvent(ScanEvent.LinkPasted("again: $LINK"))
        advanceUntilIdle()
        assertThat(receipts.submitted).containsExactly(LINK, LINK, LINK)
        // Counted once.
        assertThat(state.sentCount).isEqualTo(1)
    }

    @Test
    fun `a refused link is not retried by the camera, the engine's answer would not change`() =
        runTest {
            receipts.submission = { ImportSubmission.Rejected("unsupported_provider") }
            pick(Result.success(LINK))
            advanceUntilIdle()
            assertThat(state.feedback).isEqualTo(ImportFeedback.Rejected("unsupported_provider"))
            scan(LINK)
            advanceUntilIdle()
            assertThat(receipts.submitted).containsExactly(LINK)
            viewModel.onEvent(ScanEvent.FeedbackShown)
            assertThat(state.feedback).isNull()
        }

    private companion object {
        const val LINK = "https://receipts.example.gr/v/1"
        const val OTHER_LINK = "https://receipts.example.gr/v/2"
    }
}
