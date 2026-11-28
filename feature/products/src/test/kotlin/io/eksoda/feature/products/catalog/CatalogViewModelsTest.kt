package io.eksoda.feature.products.catalog

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import io.eksoda.core.model.CatalogError
import io.eksoda.core.model.CatalogException
import io.eksoda.core.model.Store
import io.eksoda.core.testing.FakeCatalogRepository
import io.eksoda.core.testing.MainDispatcherRule
import io.eksoda.core.testing.keepCollecting
import io.eksoda.core.testing.testCategorySummary
import io.eksoda.core.testing.testMerchantSummary
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/** The categories screen, the merchant list and the merchant detail. */
class CatalogViewModelsTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val catalog = FakeCatalogRepository().apply {
        categories.value = listOf(testCategorySummary("groceries", products = 3))
        merchants.value = listOf(testMerchantSummary("m1", "Shop"), testMerchantSummary("m2"))
        stores.value = mapOf("m1" to listOf(Store("s1", "Centre", null, null)))
    }

    @Test
    fun `categories are created, renamed and deleted, and only a delete is confirmed`() = runTest {
        val viewModel = CategoriesViewModel(catalog)
        keepCollecting(viewModel.categories)
        assertThat(viewModel.categories.value?.single()?.productCount).isEqualTo(3)
        viewModel.effects.test {
            viewModel.onEvent(CategoriesEvent.Create("Pets"))
            viewModel.onEvent(CategoriesEvent.Rename("pets", "Pet food"))
            expectNoEvents()
            viewModel.onEvent(CategoriesEvent.Delete("pets"))
            assertThat(awaitItem()).isEqualTo(CategoriesEffect.Deleted)
        }
        assertThat(catalog.calls).containsExactly(
            "createCategory(Pets)",
            "renameCategory(pets, Pet food)",
            "deleteCategory(pets)",
        )
    }

    @Test
    fun `a refused category change says why, or nothing when the reason is unknown`() = runTest {
        val viewModel = CategoriesViewModel(catalog)
        viewModel.effects.test {
            catalog.failure = CatalogException(CatalogError.CATEGORY_IN_USE)
            viewModel.onEvent(CategoriesEvent.Delete("groceries"))
            assertThat(awaitItem()).isEqualTo(CategoriesEffect.Failed(CatalogError.CATEGORY_IN_USE))
            catalog.failure = CatalogException(CatalogError.SYSTEM_CATEGORY)
            viewModel.onEvent(CategoriesEvent.Rename("groceries", "Food"))
            assertThat(awaitItem()).isEqualTo(CategoriesEffect.Failed(CatalogError.SYSTEM_CATEGORY))
            catalog.failure = IllegalStateException("disk full")
            viewModel.onEvent(CategoriesEvent.Create("Pets"))
            assertThat(awaitItem()).isEqualTo(CategoriesEffect.Failed(null))
        }
    }

    @Test
    fun `the merchant list is null while loading, then every merchant`() = runTest {
        val viewModel = MerchantsViewModel(catalog)
        assertThat(viewModel.merchants.value).isNull()
        keepCollecting(viewModel.merchants)
        assertThat(viewModel.merchants.value?.map { it.id }).isEqualTo(listOf("m1", "m2"))
    }

    @Test
    fun `a merchant's detail shows its stores and the categories to default to`() = runTest {
        val viewModel = merchantDetail("m1")
        keepCollecting(viewModel.state)
        val loaded = viewModel.state.value as MerchantDetailUiState.Loaded
        assertThat(loaded.merchant.name).isEqualTo("Shop")
        assertThat(loaded.stores.map { it.id }).containsExactly("s1")
        assertThat(loaded.categories.map { it.id }).containsExactly("groceries")

        val gone = merchantDetail("m9")
        keepCollecting(gone.state)
        assertThat(gone.state.value).isEqualTo(MerchantDetailUiState.Missing)
    }

    @Test
    fun `a merchant is renamed and given a default category, and a refusal says why`() = runTest {
        val viewModel = merchantDetail("m1")
        viewModel.effects.test {
            viewModel.onEvent(MerchantDetailEvent.Rename("Corner shop"))
            viewModel.onEvent(MerchantDetailEvent.DefaultCategoryChanged("groceries"))
            viewModel.onEvent(MerchantDetailEvent.DefaultCategoryChanged(null))
            expectNoEvents()
            catalog.failure = CatalogException(CatalogError.NOT_FOUND)
            viewModel.onEvent(MerchantDetailEvent.Rename("x"))
            assertThat(awaitItem()).isInstanceOf<MerchantDetailEffect.Failed>()
                .transform { it.error }.isEqualTo(CatalogError.NOT_FOUND)
        }
        assertThat(catalog.calls).containsExactly(
            "renameMerchant(m1, Corner shop)",
            "setDefaultCategory(m1, groceries)",
            "setDefaultCategory(m1, null)",
            "renameMerchant(m1, x)",
        )
    }

    private fun merchantDetail(id: String) =
        MerchantDetailViewModel(catalog, SavedStateHandle(mapOf("merchantId" to id)))
}
