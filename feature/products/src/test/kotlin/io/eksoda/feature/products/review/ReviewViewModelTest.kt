package io.eksoda.feature.products.review

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import io.eksoda.core.model.CatalogError
import io.eksoda.core.model.CatalogException
import io.eksoda.core.model.LineType
import io.eksoda.core.model.ProductDraft
import io.eksoda.core.testing.FakeCatalogRepository
import io.eksoda.core.testing.FakeProductRepository
import io.eksoda.core.testing.FakeReviewRepository
import io.eksoda.core.testing.MainDispatcherRule
import io.eksoda.core.testing.keepCollecting
import io.eksoda.core.testing.testCategorySummary
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class ReviewViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val review = FakeReviewRepository().apply { count.value = 2 }
    private val products = FakeProductRepository()
    private val catalog = FakeCatalogRepository().apply {
        categories.value = listOf(testCategorySummary("dairy"))
    }
    private val viewModel = ReviewViewModel(review, products, catalog)

    @Test
    fun `the queue shows how many lines wait, with the categories for a new product`() = runTest {
        keepCollecting(viewModel.state)
        assertThat(viewModel.state.value.count).isEqualTo(2)
        assertThat(viewModel.state.value.categories.map { it.id }).containsExactly("dairy")
    }

    @Test
    fun `a line is linked or reclassified, and a refusal says why`() = runTest {
        viewModel.effects.test {
            viewModel.onEvent(ReviewEvent.Link("item-1", "p1"))
            assertThat(awaitItem()).isEqualTo(ReviewEffect.Matched)
            viewModel.onEvent(ReviewEvent.Reclassify("item-2", LineType.FEE))
            assertThat(awaitItem()).isEqualTo(ReviewEffect.Reclassified)
            review.failure = CatalogException(CatalogError.NOT_A_PRODUCT_LINE)
            viewModel.onEvent(ReviewEvent.Link("item-3", "p1"))
            assertThat(awaitItem()).isEqualTo(ReviewEffect.Failed(CatalogError.NOT_A_PRODUCT_LINE))
        }
        assertThat(review.calls).containsExactly(
            "linkItem(item-1, p1)",
            "reclassifyItem(item-2, FEE)",
            "linkItem(item-3, p1)",
        )
    }

    @Test
    fun `a product made for a line matches it, and a refused one keeps the form`() = runTest {
        keepCollecting(viewModel.state)
        viewModel.effects.test {
            viewModel.onEvent(ReviewEvent.Create("item-1", ProductDraft("Milk")))
            assertThat(awaitItem()).isEqualTo(ReviewEffect.Matched)
        }
        review.failure = CatalogException(CatalogError.BLANK_NAME)
        viewModel.onEvent(ReviewEvent.Create("item-2", ProductDraft(" ")))
        assertThat(viewModel.state.value.formFailed).isTrue()
        assertThat(viewModel.state.value.formError).isEqualTo(CatalogError.BLANK_NAME)
        viewModel.onEvent(ReviewEvent.FormClosed)
        assertThat(viewModel.state.value.formFailed).isFalse()
    }

    @Test
    fun `the product picker searches by the typed name after a pause`() = runTest {
        keepCollecting(viewModel.pickerItems)
        viewModel.onEvent(ReviewEvent.PickerQueryChanged(" mil"))
        advanceTimeBy(1_000.milliseconds)
        assertThat(products.filters.map { it.query }).containsExactly("", "mil")
    }
}
