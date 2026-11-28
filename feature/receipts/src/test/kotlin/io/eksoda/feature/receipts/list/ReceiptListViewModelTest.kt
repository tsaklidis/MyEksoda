package io.eksoda.feature.receipts.list

import androidx.paging.testing.asSnapshot
import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import io.eksoda.core.model.Option
import io.eksoda.core.model.ReceiptFilter
import io.eksoda.core.model.ReceiptOrdering
import io.eksoda.core.testing.FakeReceiptFilterStore
import io.eksoda.core.testing.FakeReceiptOptionsRepository
import io.eksoda.core.testing.FakeReceiptRepository
import io.eksoda.core.testing.MainDispatcherRule
import io.eksoda.core.testing.keepCollecting
import io.eksoda.core.testing.testReceipt
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class ReceiptListViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val receipts = FakeReceiptRepository().apply {
        receipts.value = listOf(
            testReceipt("a", date = "2026-11-20"),
            testReceipt("b", date = "2026-11-20"),
            testReceipt("c", date = "2026-11-19"),
        )
    }
    private val options = FakeReceiptOptionsRepository().apply {
        merchants.value = listOf(Option("m1", "Shop"))
        stores.value = mapOf("m1" to listOf(Option("s1", "Centre")))
        products = listOf(Option("p1", "Milk"), Option("p2", "Bread"))
    }
    private val store = FakeReceiptFilterStore()

    private fun viewModel() = ReceiptListViewModel(receipts, options, store)

    @Test
    fun `the list opens with the saved filters`() = runTest {
        val saved = ReceiptFilter(needsReview = true, ordering = ReceiptOrdering.OLDEST)
        store.saved = saved
        val viewModel = viewModel()
        keepCollecting(viewModel.state)
        assertThat(viewModel.state.value.filter).isEqualTo(saved)
        viewModel.items.asSnapshot()
        // It queried once, with the saved filters, not first with the defaults.
        assertThat(receipts.filters).containsExactly(saved)
        assertThat(viewModel.state.value.options.merchants).containsExactly(Option("m1", "Shop"))
    }

    @Test
    fun `saved filters that cannot be read are the defaults`() = runTest {
        store.loadFailure = IllegalStateException("corrupt preferences")
        val viewModel = viewModel()
        keepCollecting(viewModel.state)
        assertThat(viewModel.state.value.filter).isEqualTo(ReceiptFilter())
    }

    @Test
    fun `a filter change is applied at once and saved, without the search text`() = runTest {
        val viewModel = viewModel()
        keepCollecting(viewModel.state)
        viewModel.onEvent(
            ReceiptListEvent.FilterChanged(ReceiptFilter(query = "x", hasWarnings = true)),
        )
        assertThat(viewModel.state.value.filter).isEqualTo(ReceiptFilter(hasWarnings = true))
        assertThat(store.saves.last()).isEqualTo(ReceiptFilter(hasWarnings = true))
        viewModel.onEvent(ReceiptListEvent.OrderingChanged(ReceiptOrdering.HIGHEST_TOTAL))
        viewModel.onEvent(ReceiptListEvent.FiltersCleared)
        // Clearing keeps the ordering.
        assertThat(viewModel.state.value.filter)
            .isEqualTo(ReceiptFilter(ordering = ReceiptOrdering.HIGHEST_TOTAL))
    }

    @Test
    fun `typing searches after a pause, and clearing the text applies at once`() = runTest {
        val viewModel = viewModel()
        keepCollecting(viewModel.state)
        viewModel.onEvent(ReceiptListEvent.QueryChanged("mi"))
        viewModel.onEvent(ReceiptListEvent.QueryChanged("milk "))
        advanceTimeBy(SEARCH_PAUSE - 1.milliseconds)
        assertThat(viewModel.state.value.filter.query).isNull()
        advanceTimeBy(2.milliseconds)
        // Only the last text, trimmed; the half-typed one never ran.
        assertThat(viewModel.state.value.filter.query).isEqualTo("milk")
        viewModel.onEvent(ReceiptListEvent.QueryChanged(""))
        advanceTimeBy(1.milliseconds)
        assertThat(viewModel.state.value.filter.query).isNull()
    }

    @Test
    fun `receipts by date have a header for each day, by total none`() = runTest {
        val viewModel = viewModel()
        keepCollecting(viewModel.state)
        assertThat(viewModel.items.asSnapshot()).containsExactly(
            ReceiptListItem.Day(LocalDate.parse("2026-11-20")),
            ReceiptListItem.Row(receipts.receipts.value[0]),
            ReceiptListItem.Row(receipts.receipts.value[1]),
            ReceiptListItem.Day(LocalDate.parse("2026-11-19")),
            ReceiptListItem.Row(receipts.receipts.value[2]),
        )
        viewModel.onEvent(ReceiptListEvent.OrderingChanged(ReceiptOrdering.HIGHEST_TOTAL))
        assertThat(viewModel.items.asSnapshot().map { (it as ReceiptListItem.Row).receipt.id })
            .containsExactly("a", "b", "c")
    }

    @Test
    fun `the sheet lists the chosen merchant's stores and the products searched`() = runTest {
        val viewModel = viewModel()
        keepCollecting(viewModel.state)
        viewModel.onEvent(ReceiptListEvent.SheetMerchantChanged("m1"))
        assertThat(viewModel.state.value.options.stores).containsExactly(Option("s1", "Centre"))
        viewModel.onEvent(ReceiptListEvent.SheetMerchantChanged(null))
        assertThat(viewModel.state.value.options.stores).isEqualTo(emptyList())

        viewModel.onEvent(ReceiptListEvent.ProductSearched("mil"))
        advanceTimeBy(PRODUCT_PAUSE + 1.milliseconds)
        assertThat(viewModel.state.value.options.products).containsExactly(Option("p1", "Milk"))
        viewModel.onEvent(ReceiptListEvent.ProductSearched(" "))
        advanceTimeBy(1.milliseconds)
        assertThat(viewModel.state.value.options.products).isEqualTo(emptyList())
        // A blank query is never searched.
        assertThat(options.searches).containsExactly("mil")
    }

    @Test
    fun `swipes exclude or delete a receipt, and say whether it worked`() = runTest {
        val viewModel = viewModel()
        viewModel.effects.test {
            viewModel.onEvent(ReceiptListEvent.ExcludedChanged("a", excluded = true))
            assertThat(awaitItem()).isEqualTo(ReceiptListEffect.ExcludedChanged("a", true))
            viewModel.onEvent(ReceiptListEvent.DeleteConfirmed("b"))
            assertThat(awaitItem()).isEqualTo(ReceiptListEffect.Deleted)
            receipts.failure = IllegalStateException("no such receipt")
            viewModel.onEvent(ReceiptListEvent.ExcludedChanged("a", excluded = false))
            assertThat(awaitItem()).isEqualTo(ReceiptListEffect.Failed)
            viewModel.onEvent(ReceiptListEvent.DeleteConfirmed("b"))
            assertThat(awaitItem()).isEqualTo(ReceiptListEffect.Failed)
        }
        assertThat(receipts.calls).containsExactly(
            "setExcludedFromAnalytics(a, true)",
            "deleteReceipt(b)",
            "setExcludedFromAnalytics(a, false)",
            "deleteReceipt(b)",
        )
    }

    private companion object {
        val SEARCH_PAUSE = 300.milliseconds
        val PRODUCT_PAUSE = 200.milliseconds
    }
}
