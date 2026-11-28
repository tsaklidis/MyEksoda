package io.eksoda.feature.products.detail

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import io.eksoda.core.model.CatalogError
import io.eksoda.core.model.CatalogException
import io.eksoda.core.model.ProductDraft
import io.eksoda.core.testing.FakeCatalogRepository
import io.eksoda.core.testing.FakeProductRepository
import io.eksoda.core.testing.MainDispatcherRule
import io.eksoda.core.testing.keepCollecting
import io.eksoda.core.testing.testProduct
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneOffset

class ProductDetailViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val products = FakeProductRepository().apply {
        this.products.value = listOf(testProduct("p1", "Milk"))
    }
    private val catalog = FakeCatalogRepository()
    private val clock = Clock.fixed(Instant.parse("2026-11-20T10:00:00Z"), ZoneOffset.UTC)

    private fun viewModel(id: String = "p1") =
        ProductDetailViewModel(products, catalog, clock, SavedStateHandle(mapOf("productId" to id)))

    @Test
    fun `the product is shown with its prices, ending this month`() = runTest {
        val viewModel = viewModel()
        keepCollecting(viewModel.state)
        val loaded = viewModel.state.value as ProductDetailUiState.Loaded
        assertThat(loaded.product.name).isEqualTo("Milk")
        assertThat(loaded.thisMonth).isEqualTo(YearMonth.parse("2026-11"))
        // The unit with the most entries until the user picks one.
        viewModel.onEvent(ProductDetailEvent.UnitSelected("kg"))
        assertThat(products.units).containsExactly(null, "kg")
    }

    @Test
    fun `a merged, missing or unreadable product is missing`() = runTest {
        val missing = viewModel("p9")
        keepCollecting(missing.state)
        assertThat(missing.state.value).isEqualTo(ProductDetailUiState.Missing)

        products.readFailure = IllegalStateException("bad")
        val broken = viewModel()
        keepCollecting(broken.state)
        assertThat(broken.state.value).isEqualTo(ProductDetailUiState.Missing)
    }

    @Test
    fun `an edit is saved, and a refused one keeps the form with its reason`() = runTest {
        val viewModel = viewModel()
        keepCollecting(viewModel.state)
        viewModel.effects.test {
            viewModel.onEvent(ProductDetailEvent.SaveRequested(ProductDraft("Milk 1L")))
            assertThat(awaitItem()).isEqualTo(ProductDetailEffect.Saved)
        }
        products.failure = CatalogException(CatalogError.PACKAGE_INCOMPLETE)
        viewModel.onEvent(ProductDetailEvent.SaveRequested(ProductDraft("Milk", packageUnit = "l")))
        val form = (viewModel.state.value as ProductDetailUiState.Loaded).form
        assertThat(form.failed).isTrue()
        assertThat(form.error).isEqualTo(CatalogError.PACKAGE_INCOMPLETE)
        viewModel.onEvent(ProductDetailEvent.FormClosed)
        assertThat(
            (viewModel.state.value as ProductDetailUiState.Loaded).form,
        ).isEqualTo(FormState())
    }

    @Test
    fun `products are merged into this one, and a refused merge says why`() = runTest {
        val viewModel = viewModel()
        viewModel.effects.test {
            viewModel.onEvent(ProductDetailEvent.MergeConfirmed(setOf("p3", "p2")))
            assertThat(awaitItem()).isEqualTo(ProductDetailEffect.Merged(2))
            products.failure = CatalogException(CatalogError.TOO_MANY_PRODUCTS)
            viewModel.onEvent(ProductDetailEvent.MergeConfirmed(setOf("p4")))
            assertThat(awaitItem())
                .isEqualTo(ProductDetailEffect.Failed(CatalogError.TOO_MANY_PRODUCTS))
        }
        assertThat(products.calls)
            .containsExactly("mergeProducts(p1, [p2, p3])", "mergeProducts(p1, [p4])")
    }
}
