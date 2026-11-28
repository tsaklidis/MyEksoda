package io.eksoda.feature.receipts.detail

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.prop
import io.eksoda.core.model.IssueTime
import io.eksoda.core.model.Option
import io.eksoda.core.model.ReceiptDetail
import io.eksoda.core.testing.FakeReceiptOptionsRepository
import io.eksoda.core.testing.FakeReceiptRepository
import io.eksoda.core.testing.MainDispatcherRule
import io.eksoda.core.testing.keepCollecting
import io.eksoda.core.testing.testReceipt
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class ReceiptDetailViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val receipts = FakeReceiptRepository().apply {
        details.value = mapOf(ID to detail())
        links = mapOf(ID to LINK)
    }
    private val options = FakeReceiptOptionsRepository().apply {
        stores.value = mapOf("merchant-Shop" to listOf(Option("store-1", "Centre")))
    }
    private val viewModel =
        ReceiptDetailViewModel(receipts, options, SavedStateHandle(mapOf("receiptId" to ID)))

    @Test
    fun `the receipt is shown with its merchant's stores to choose from`() = runTest {
        keepCollecting(viewModel.state)
        val loaded = viewModel.state.value as ReceiptDetailUiState.Loaded
        assertThat(loaded.detail.receipt.id).isEqualTo(ID)
        assertThat(loaded.stores).containsExactly(Option("store-1", "Centre"))
    }

    @Test
    fun `a receipt that is gone, or cannot be read, is missing`() = runTest {
        keepCollecting(viewModel.state)
        receipts.details.value = emptyMap()
        assertThat(viewModel.state.value).isEqualTo(ReceiptDetailUiState.Missing)

        val broken = FakeReceiptRepository().apply { readFailure = IllegalStateException("bad") }
        val other =
            ReceiptDetailViewModel(broken, options, SavedStateHandle(mapOf("receiptId" to ID)))
        keepCollecting(other.state)
        assertThat(other.state.value).isEqualTo(ReceiptDetailUiState.Missing)
    }

    @Test
    fun `edits go to the repository for this receipt`() = runTest {
        viewModel.effects.test {
            viewModel.onEvent(ReceiptDetailEvent.NoteSaved("for the office"))
            viewModel.onEvent(ReceiptDetailEvent.IncludedChanged(included = false))
            viewModel.onEvent(ReceiptDetailEvent.StoreChanged("store-1"))
            viewModel.onEvent(ReceiptDetailEvent.StoreChanged(null))
            expectNoEvents()
        }
        assertThat(receipts.calls).containsExactly(
            "setNote($ID, for the office)",
            // "Include in stats" off is excluded from analytics.
            "setExcludedFromAnalytics($ID, true)",
            "setStore($ID, store-1)",
            "setStore($ID, null)",
        )
    }

    @Test
    fun `a failed edit says so`() = runTest {
        receipts.failure = IllegalStateException("not a store of this merchant")
        viewModel.effects.test {
            viewModel.onEvent(ReceiptDetailEvent.StoreChanged("elsewhere"))
            assertThat(awaitItem()).isEqualTo(ReceiptDetailEffect.SaveFailed)
            viewModel.onEvent(ReceiptDetailEvent.NoteSaved("x"))
            assertThat(awaitItem()).isEqualTo(ReceiptDetailEffect.SaveFailed)
        }
    }

    @Test
    fun `a deleted receipt closes the screen, and a failed delete stays`() = runTest {
        viewModel.effects.test {
            viewModel.onEvent(ReceiptDetailEvent.DeleteConfirmed)
            assertThat(awaitItem()).isEqualTo(ReceiptDetailEffect.Deleted)
            receipts.failure = IllegalStateException("no such receipt")
            viewModel.onEvent(ReceiptDetailEvent.DeleteConfirmed)
            assertThat(awaitItem()).isEqualTo(ReceiptDetailEffect.SaveFailed)
        }
    }

    @Test
    fun `the original opens only with a link, which never shows in a log line`() = runTest {
        viewModel.effects.test {
            viewModel.onEvent(ReceiptDetailEvent.OpenOriginalConfirmed)
            val effect = awaitItem()
            assertThat(effect).isInstanceOf<ReceiptDetailEffect.OpenLink>()
                .prop(ReceiptDetailEffect.OpenLink::link).isEqualTo(LINK)
            assertThat(effect.toString()).isEqualTo("OpenLink(<link>)")
            // A receipt from another phone has no link here: nothing opens.
            receipts.links = emptyMap()
            viewModel.onEvent(ReceiptDetailEvent.OpenOriginalConfirmed)
            expectNoEvents()
        }
    }

    private fun detail() = ReceiptDetail(
        receipt = testReceipt(ID),
        issueTime = IssueTime.EXACT,
        hasLineItems = true,
        items = emptyList(),
        payments = emptyList(),
        vatBreakdown = emptyList(),
        warnings = emptyList(),
        userNote = "",
        documentNumber = "1",
        documentTypeCode = null,
        mark = null,
        uid = null,
        cancellationMark = null,
        providerCode = "impact",
        parserVersion = "impact/1",
    )

    private companion object {
        const val ID = "receipt-1"
        const val LINK = "https://receipts.example.gr/v/1"
    }
}
