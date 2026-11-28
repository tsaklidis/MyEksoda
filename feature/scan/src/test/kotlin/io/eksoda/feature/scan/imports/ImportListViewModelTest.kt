package io.eksoda.feature.scan.imports

import androidx.paging.testing.asSnapshot
import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import io.eksoda.core.model.ImportJob
import io.eksoda.core.model.ImportStatus
import io.eksoda.core.testing.FakeReceiptRepository
import io.eksoda.core.testing.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.time.Instant

class ImportListViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val receipts = FakeReceiptRepository().apply {
        jobs.value = listOf(
            job("pending", ImportStatus.PENDING),
            job("failed", ImportStatus.FAILED),
            job("done", ImportStatus.COMPLETED),
        )
    }
    private val viewModel = ImportListViewModel(receipts)

    @Test
    fun `every import is listed until a filter narrows the list by status`() = runTest {
        assertThat(viewModel.filter.value).isEqualTo(ImportFilter.ALL)
        assertThat(viewModel.items.asSnapshot().map { it.id })
            .containsExactly("pending", "failed", "done")
        val expected = mapOf(
            ImportFilter.IMPORTING to "pending",
            ImportFilter.FAILED to "failed",
            ImportFilter.DONE to "done",
        )
        for ((filter, id) in expected) {
            viewModel.onEvent(ImportListEvent.FilterChanged(filter))
            assertThat(viewModel.filter.value).isEqualTo(filter)
            assertThat(viewModel.items.asSnapshot().map { it.id }, name = "$filter")
                .containsExactly(id)
        }
    }

    @Test
    fun `a retry goes to the repository, and a refused one says so`() = runTest {
        viewModel.effects.test {
            viewModel.onEvent(ImportListEvent.Retry("failed"))
            expectNoEvents()
            receipts.failure = IllegalStateException("only a failed import can be retried")
            viewModel.onEvent(ImportListEvent.Retry("done"))
            assertThat(awaitItem()).isEqualTo(ImportListEffect.RetryFailed)
        }
        assertThat(receipts.calls).containsExactly("retryImport(failed)", "retryImport(done)")
    }

    private fun job(id: String, status: ImportStatus) = ImportJob(
        id = id,
        status = status,
        outcome = null,
        providerCode = "impact",
        sourceHost = "receipts.example.gr",
        createdAt = Instant.EPOCH,
        finishedAt = null,
        receiptId = null,
        errorCode = null,
        nextAttemptAt = null,
        merchantName = null,
        total = null,
    )
}
