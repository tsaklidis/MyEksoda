package io.eksoda.feature.products.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import dagger.hilt.android.lifecycle.HiltViewModel
import io.eksoda.core.data.repository.CatalogRepository
import io.eksoda.core.data.repository.ProductRepository
import io.eksoda.core.data.repository.ReviewRepository
import io.eksoda.core.model.CatalogError
import io.eksoda.core.model.Category
import io.eksoda.core.model.Merchant
import io.eksoda.core.model.Product
import io.eksoda.core.model.ProductDraft
import io.eksoda.core.model.ProductFilter
import io.eksoda.core.model.ProductOrdering
import io.eksoda.feature.products.common.catalogError
import io.eksoda.feature.products.common.debouncedQuery
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProductListUiState(
    val filter: ProductFilter = ProductFilter(),
    val categories: List<Category> = emptyList(),
    val merchants: List<Merchant> = emptyList(),
    val reviewCount: Int = 0,
    val saving: Boolean = false,
    /** The new-product form's last save failed, with this reason when one is known. */
    val formFailed: Boolean = false,
    val formError: CatalogError? = null,
)

sealed interface ProductListEvent {
    data class QueryChanged(val query: String) : ProductListEvent

    data class CategoryChanged(val category: Category?) : ProductListEvent

    data class MerchantChanged(val merchant: Merchant?) : ProductListEvent

    data class OrderingChanged(val ordering: ProductOrdering) : ProductListEvent

    data class CreateRequested(val draft: ProductDraft) : ProductListEvent

    data object FormClosed : ProductListEvent
}

sealed interface ProductListEffect {
    data class Created(val productId: String) : ProductListEffect
}

/**
 * The product list: search, category and "bought at" filters, and
 * New product. Room re-runs the query when a product changes, so edits show by themselves.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ProductListViewModel @Inject constructor(
    private val products: ProductRepository,
    catalog: CatalogRepository,
    review: ReviewRepository,
) : ViewModel() {
    /** Everything but the search text, which is debounced. */
    private val filter = MutableStateFlow(ProductFilter())
    private val query = MutableStateFlow("")
    private val form = MutableStateFlow(FormState())

    val items: Flow<PagingData<Product>> =
        combine(filter, query.debouncedQuery()) { current, text -> current.copy(query = text) }
            .distinctUntilChanged()
            .flatMapLatest { products.getProducts(it) }
            .cachedIn(viewModelScope)

    val state: StateFlow<ProductListUiState> = combine(
        filter,
        catalog.getCategories().map { list -> list.map { it.category } },
        catalog.getMerchants().map { list -> list.map { Merchant(it.id, it.name) } },
        review.getReviewCount(),
        form,
    ) { current, categories, merchants, count, formState ->
        ProductListUiState(
            filter = current,
            categories = categories,
            merchants = merchants,
            reviewCount = count,
            saving = formState.saving,
            formFailed = formState.failed,
            formError = formState.error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ProductListUiState())

    private val _effects = Channel<ProductListEffect>(Channel.BUFFERED)
    val effects: Flow<ProductListEffect> = _effects.receiveAsFlow()

    fun onEvent(event: ProductListEvent) {
        when (event) {
            is ProductListEvent.QueryChanged -> query.value = event.query

            is ProductListEvent.CategoryChanged ->
                filter.update { it.copy(category = event.category) }

            is ProductListEvent.MerchantChanged ->
                filter.update { it.copy(merchant = event.merchant) }

            is ProductListEvent.OrderingChanged ->
                filter.update { it.copy(ordering = event.ordering) }

            is ProductListEvent.CreateRequested -> create(event.draft)

            ProductListEvent.FormClosed -> form.value = FormState()
        }
    }

    private fun create(draft: ProductDraft) {
        if (form.value.saving) return
        form.value = FormState(saving = true)
        viewModelScope.launch {
            products.createProduct(draft)
                .onSuccess {
                    form.value = FormState()
                    _effects.send(ProductListEffect.Created(it))
                }
                .onFailure { form.value = FormState(failed = true, error = it.catalogError()) }
        }
    }

    private data class FormState(
        val saving: Boolean = false,
        val failed: Boolean = false,
        val error: CatalogError? = null,
    )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
