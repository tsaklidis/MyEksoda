package io.eksoda.feature.scan.share

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import io.eksoda.core.data.repository.ReceiptRepository
import io.eksoda.core.model.ImportSubmission
import io.eksoda.core.testing.FakeReceiptRepository
import io.eksoda.core.testing.MainDispatcherRule
import io.eksoda.feature.scan.ImportFeedback
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class ShareImportViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val receipts = FakeReceiptRepository()
    private val viewModel = ShareImportViewModel(receipts)

    @Test
    fun `a shared link is saved only after the user confirms`() = runTest {
        assertThat(viewModel.state.value).isEqualTo(ShareImportUiState.Confirm)
        assertThat(receipts.submitted).isEqualTo(emptyList())
        viewModel.import(LINK)
        assertThat(receipts.submitted).containsExactly(LINK)
        assertThat(viewModel.state.value)
            .isEqualTo(ShareImportUiState.Done(ImportFeedback.Importing(1)))
    }

    @Test
    fun `each submission's answer is what the user is told`() = runTest {
        val answers = listOf(
            ImportSubmission.Queued("job", offline = true) to ImportFeedback.SavedOffline(1),
            ImportSubmission.AlreadyImported(
                "receipt",
            ) to ImportFeedback.AlreadyImported("receipt"),
            ImportSubmission.AlreadyPending("job") to ImportFeedback.AlreadyPending,
            ImportSubmission.Rejected("invalid_url") to ImportFeedback.Rejected("invalid_url"),
        )
        for ((submission, feedback) in answers) {
            receipts.submission = { submission }
            viewModel.reset()
            viewModel.import(LINK)
            assertThat(viewModel.state.value, name = "$submission")
                .isEqualTo(ShareImportUiState.Done(feedback))
        }
    }

    @Test
    fun `an unexpected error saves nothing and says so`() = runTest {
        receipts.failure = IllegalStateException("database closed")
        viewModel.import(LINK)
        assertThat(viewModel.state.value)
            .isEqualTo(ShareImportUiState.Done(ImportFeedback.Failed))
    }

    @Test
    fun `a second tap while saving or after is not a second import`() = runTest {
        val gate = CompletableDeferred<Unit>()
        receipts.submission = { ImportSubmission.Queued("job") }
        val slow = object : ReceiptRepository by receipts {
            override suspend fun importReceipt(url: String): Result<ImportSubmission> {
                gate.await()
                return receipts.importReceipt(url)
            }
        }
        val saving = ShareImportViewModel(slow)
        saving.import(LINK)
        assertThat(saving.state.value).isEqualTo(ShareImportUiState.Saving)
        saving.import(LINK)
        gate.complete(Unit)
        saving.import(LINK)
        assertThat(receipts.submitted).containsExactly(LINK)
        // Reset, the next shared link can be saved.
        saving.reset()
        saving.import(LINK)
        assertThat(receipts.submitted).containsExactly(LINK, LINK)
    }

    private companion object {
        const val LINK = "https://receipts.example.gr/v/1"
    }
}
