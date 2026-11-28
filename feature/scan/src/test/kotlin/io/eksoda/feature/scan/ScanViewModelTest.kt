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
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScanViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val receipts = FakeReceiptRepository()

    // A photo without a code; ScanPhotoTest reads photos with one.
    private val viewModel = ScanViewModel(receipts) { null }
    private val state get() = viewModel.state.value

    private fun scan(text: String) = viewModel.onEvent(ScanEvent.CodeScanned(text))

    @Test
    fun `a scanned receipt link is submitted, counted and confirmed`() = runTest {
        viewModel.effects.test {
            scan(LINK)
            assertThat(awaitItem()).isEqualTo(ScanEffect.Saved)
        }
        assertThat(receipts.submitted).containsExactly(LINK)
        assertThat(state.feedback).isEqualTo(ImportFeedback.Importing(1))
        assertThat(state.sentCount).isEqualTo(1)
    }

    @Test
    fun `the same code again in a session is ignored`() = runTest {
        scan(LINK)
        scan(LINK)
        scan(OTHER_LINK)
        assertThat(receipts.submitted).containsExactly(LINK, OTHER_LINK)
        // Each saved receipt has its number in the session.
        assertThat(state.feedback).isEqualTo(ImportFeedback.Importing(2))
        assertThat(state.sentCount).isEqualTo(2)
    }

    @Test
    fun `a code that is not an https link is not submitted`() = runTest {
        for (text in listOf("hello", "http://receipts.example.gr/1", "https://a b", "")) {
            scan(text)
            assertThat(state.feedback, name = text).isEqualTo(ImportFeedback.NotAReceipt)
        }
        assertThat(receipts.submitted).isEqualTo(emptyList())
        assertThat(state.sentCount).isEqualTo(0)
    }

    @Test
    fun `a receipt saved offline is counted and imports later`() = runTest {
        receipts.submission = { ImportSubmission.Queued("job", offline = true) }
        viewModel.effects.test {
            scan(LINK)
            assertThat(awaitItem()).isEqualTo(ScanEffect.Saved)
        }
        assertThat(state.feedback).isEqualTo(ImportFeedback.SavedOffline(1))
        assertThat(state.sentCount).isEqualTo(1)
    }

    @Test
    fun `a receipt already imported or pending, or a refused link, is not counted`() = runTest {
        val answers = mapOf(
            LINK to ImportSubmission.AlreadyImported("receipt-1"),
            OTHER_LINK to ImportSubmission.AlreadyPending("job-1"),
            THIRD_LINK to ImportSubmission.Rejected("unsupported_provider"),
        )
        receipts.submission = { answers.getValue(it) }
        viewModel.effects.test {
            scan(LINK)
            assertThat(state.feedback).isEqualTo(ImportFeedback.AlreadyImported("receipt-1"))
            scan(OTHER_LINK)
            assertThat(state.feedback).isEqualTo(ImportFeedback.AlreadyPending)
            scan(THIRD_LINK)
            assertThat(state.feedback)
                .isEqualTo(ImportFeedback.Rejected("unsupported_provider"))
            expectNoEvents()
        }
        assertThat(state.sentCount).isEqualTo(0)
    }

    @Test
    fun `after an unexpected error, scanning the code again retries it`() = runTest {
        receipts.failure = IllegalStateException("database closed")
        scan(LINK)
        assertThat(state.feedback).isEqualTo(ImportFeedback.Failed)
        assertThat(state.sentCount).isEqualTo(0)

        receipts.failure = null
        scan(LINK)
        assertThat(receipts.submitted).containsExactly(LINK, LINK)
        assertThat(state.feedback).isEqualTo(ImportFeedback.Importing(1))
    }

    @Test
    fun `the link in pasted text is submitted, without the punctuation around it`() = runTest {
        viewModel.onEvent(ScanEvent.PasteOpened)
        assertThat(state.pasteOpen).isTrue()
        viewModel.onEvent(ScanEvent.LinkPasted("My receipt ($LINK)."))
        assertThat(state.pasteOpen).isFalse()
        assertThat(receipts.submitted).containsExactly(LINK)
        assertThat(state.feedback).isEqualTo(ImportFeedback.Importing(1))
        // Pasted, the link is also seen: the camera does not submit it again.
        scan(LINK)
        assertThat(receipts.submitted).containsExactly(LINK)
    }

    @Test
    fun `pasted text without a link is refused as an invalid link`() = runTest {
        viewModel.onEvent(ScanEvent.LinkPasted("no link here"))
        assertThat(state.feedback).isEqualTo(ImportFeedback.Rejected("invalid_url"))
        assertThat(state.pasteOpen).isFalse()
        assertThat(receipts.submitted).isEqualTo(emptyList())
    }

    @Test
    fun `a pasted link that fails can be pasted again`() = runTest {
        receipts.failure = IllegalStateException("database closed")
        viewModel.onEvent(ScanEvent.LinkPasted(LINK))
        assertThat(state.feedback).isEqualTo(ImportFeedback.Failed)
        receipts.failure = null
        viewModel.onEvent(ScanEvent.LinkPasted(LINK))
        assertThat(receipts.submitted).containsExactly(LINK, LINK)
        assertThat(state.feedback).isEqualTo(ImportFeedback.Importing(1))
    }

    @Test
    fun `a photo without a readable code says so`() = runTest {
        viewModel.onEvent(ScanEvent.PhotoPicked(Uri.parse("content://media/missing/1")))
        assertThat(state.feedback).isEqualTo(ImportFeedback.NoCodeInPhoto)
        assertThat(state.readingPhoto).isFalse()
        assertThat(receipts.submitted).isEqualTo(emptyList())
    }

    @Test
    fun `the torch, the paste sheet and the feedback chip follow the user`() = runTest {
        viewModel.onEvent(ScanEvent.TorchToggled)
        assertThat(state.torchOn).isTrue()
        viewModel.onEvent(ScanEvent.TorchToggled)
        assertThat(state.torchOn).isFalse()
        viewModel.onEvent(ScanEvent.PasteOpened)
        viewModel.onEvent(ScanEvent.PasteDismissed)
        assertThat(state.pasteOpen).isFalse()
        scan("hello")
        viewModel.onEvent(ScanEvent.FeedbackShown)
        assertThat(state.feedback).isNull()
    }

    private companion object {
        const val LINK = "https://receipts.example.gr/v/1"
        const val OTHER_LINK = "https://receipts.example.gr/v/2"
        const val THIRD_LINK = "https://receipts.example.gr/v/3"
    }
}
