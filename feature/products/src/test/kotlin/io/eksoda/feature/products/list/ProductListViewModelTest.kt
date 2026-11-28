package io.eksoda.feature.products.list

import androidx.paging.testing.asSnapshot
import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import io.eksoda.core.model.CatalogError
import io.eksoda.core.model.CatalogException
import io.eksoda.core.model.Merchant
import io.eksoda.core.model.Option
import io.eksoda.core.model.ProductDraft
import io.eksoda.core.model.ProductFilter
import io.eksoda.core.model.ProductOrdering
import io.eksoda.core.testing.FakeCatalogRepository
import io.eksoda.core.testing.FakeProductRepository
import io.eksoda.core.testing.FakeReceiptOptionsRepository
import io.eksoda.core.testing.FakeReviewRepository
import io.eksoda.core.testing.MainDispatcherRule
import io.eksoda.core.testing.keepCollecting
import io.eksoda.core.testing.testCategory
import io.eksoda.core.testing.testCategorySummary
import io.eksoda.core.testing.testProduct
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class ProductListViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val products = FakeProductRepository().apply {
        this.products.value = listOf(testProduct("p1", "Milk"))
    }
    private val catalog = FakeCatalogRepository().apply {
        categories.value = listOf(testCategorySummary("dairy"))
    }
    private val options = FakeReceiptOptionsRepository().apply {
        merchants.value = listOf(Option("m1", "Shop"))
    }
    private val review = FakeReviewRepository().apply { count.value = 4 }
    private val viewModel = ProductListViewModel(products, catalog, options, review)

    @Test
    fun `the list shows the products, the filter choices and how many lines wait`() = runTest {
        keepCollecting(viewModel.state)
        assertThat(viewModel.items.asSnapshot().map { it.id }).containsExactly("p1")
        val state = viewModel.state.value
        assertThat(state.categories.map { it.id }).containsExactly("dairy")
        assertThat(state.merchants).containsExactly(Merchant("m1", "Shop"))
        assertThat(state.reviewCount).isEqualTo(4)
    }

    @Test
    fun `filters apply at once, typed text after a pause`() = runTest {
        keepCollecting(viewModel.state)
        keepCollecting(viewModel.items)
        viewModel.onEvent(ProductListEvent.CategoryChanged(testCategory("dairy")))
        viewModel.onEvent(ProductListEvent.MerchantChanged(Merchant("m1", "Shop")))
        viewModel.onEvent(ProductListEvent.OrderingChanged(ProductOrdering.MOST_BOUGHT))
        viewModel.onEvent(ProductListEvent.QueryChanged(" milk "))
        advanceTimeBy(1_000.milliseconds)
        assertThat(products.filters.last()).isEqualTo(
            ProductFilter(
                query = "milk",
                category = testCategory("dairy"),
                merchant = Merchant("m1", "Shop"),
                ordering = ProductOrdering.MOST_BOUGHT,
            ),
        )
        assertThat(viewModel.state.value.filter.ordering).isEqualTo(ProductOrdering.MOST_BOUGHT)
    }

    @Test
    fun `a new product opens once created, and a refused one keeps the form with its reason`() =
        runTest {
            keepCollecting(viewModel.state)
            viewModel.effects.test {
                viewModel.onEvent(ProductListEvent.CreateRequested(ProductDraft("Bread")))
                assertThat(awaitItem()).isEqualTo(ProductListEffect.Created("product-Bread"))
            }
            assertThat(viewModel.state.value.formFailed).isFalse()

            products.failure = CatalogException(CatalogError.NAME_TAKEN)
            viewModel.onEvent(ProductListEvent.CreateRequested(ProductDraft("Milk")))
            assertThat(viewModel.state.value.formFailed).isTrue()
            assertThat(viewModel.state.value.formError).isEqualTo(CatalogError.NAME_TAKEN)
            assertThat(viewModel.state.value.saving).isFalse()
            viewModel.onEvent(ProductListEvent.FormClosed)
            assertThat(viewModel.state.value.formFailed).isFalse()
        }
}
