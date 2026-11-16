package io.eksoda.feature.products.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import dagger.hilt.android.lifecycle.HiltViewModel
import io.eksoda.core.data.repository.CatalogRepository
import io.eksoda.core.data.repository.ProductRepository
import io.eksoda.core.model.CatalogError
import io.eksoda.core.model.Category
import io.eksoda.core.model.PriceComparison
import io.eksoda.core.model.PriceHistory
import io.eksoda.core.model.PriceStats
import io.eksoda.core.model.Product
import io.eksoda.core.model.ProductDraft
import io.eksoda.core.model.ProductFilter
import io.eksoda.feature.products.common.catalogError
import io.eksoda.feature.products.common.debouncedQuery
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.YearMonth
import javax.inject.Inject

sealed interface ProductDetailUiState {
    data object Loading : ProductDetailUiState

    /** Merged into another product, or never existed. */
    data object Missing : ProductDetailUiState

    data class Loaded(
        val product: Product,
        val history: PriceHistory,
        val comparison: PriceComparison,
        val categories: List<Category>,
        val form: FormState,
        /** The month the price chart ends with: this month. */
        val thisMonth: YearMonth,
    ) : ProductDetailUiState {
        /** The unit the statistics and chart show: the comparison's, else the first one. */
        val unit: String?
            get() = comparison.normalizedUnit ?: history.stats.firstOrNull()?.normalizedUnit

        val stats: PriceStats?
            get() = history.stats.firstOrNull {
                it.normalizedUnit == unit &&
                    (comparison.currency == null || it.currency == comparison.currency)
            }
    }
}

data class FormState(
    val saving: Boolean = false,
    val failed: Boolean = false,
    val error: CatalogError? = null,
)

sealed interface ProductDetailEvent {
    data class UnitSelected(val unit: String) : ProductDetailEvent

    data class SaveRequested(val draft: ProductDraft) : ProductDetailEvent

    data object FormClosed : ProductDetailEvent

    data class PickerQueryChanged(val query: String) : ProductDetailEvent

    data class MergeConfirmed(val sourceIds: Set<String>) : ProductDetailEvent
}

sealed interface ProductDetailEffect {
    data object Saved : ProductDetailEffect

    data class Merged(val count: Int) : ProductDetailEffect

    data class Failed(val error: CatalogError?) : ProductDetailEffect
}

/**
 * Product detail (`android-app.md` §6.6.2): statistics, the price chart, where it is cheapest,
 * the purchase history, Edit and Merge. Everything follows the database, so an edit or a merge
 * shows by itself.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ProductDetailViewModel @Inject constructor(
    private val products: ProductRepository,
    catalog: CatalogRepository,
    private val clock: Clock,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val productId: String = checkNotNull(savedStateHandle[PRODUCT_ID])
    private val unit = MutableStateFlow<String?>(null)
    private val form = MutableStateFlow(FormState())
    private val pickerQuery = MutableStateFlow("")

    val state: StateFlow<ProductDetailUiState> = combine(
        products.getProduct(productId),
        products.getPriceHistory(productId),
        unit.flatMapLatest { products.getComparison(productId, it) },
        catalog.getCategories().map { list -> list.map { it.category } },
        form,
    ) { product, history, comparison, categories, formState ->
        if (product == null) {
            ProductDetailUiState.Missing
        } else {
            ProductDetailUiState.Loaded(
                product = product,
                history = history,
                comparison = comparison,
                categories = categories,
                form = formState,
                thisMonth = YearMonth.now(clock),
            )
        }
    }
        .catch { emit(ProductDetailUiState.Missing) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            ProductDetailUiState.Loading,
        )

    /** Products to merge into this one. */
    val pickerItems: Flow<PagingData<Product>> = pickerQuery.debouncedQuery()
        .flatMapLatest { products.getProducts(ProductFilter(query = it)) }
        .cachedIn(viewModelScope)

    private val _effects = Channel<ProductDetailEffect>(Channel.BUFFERED)
    val effects: Flow<ProductDetailEffect> = _effects.receiveAsFlow()

    fun onEvent(event: ProductDetailEvent) {
        when (event) {
            is ProductDetailEvent.UnitSelected -> unit.value = event.unit
            is ProductDetailEvent.SaveRequested -> save(event.draft)
            ProductDetailEvent.FormClosed -> form.value = FormState()
            is ProductDetailEvent.PickerQueryChanged -> pickerQuery.value = event.query
            is ProductDetailEvent.MergeConfirmed -> merge(event.sourceIds)
        }
    }

    private fun save(draft: ProductDraft) {
        if (form.value.saving) return
        form.value = FormState(saving = true)
        viewModelScope.launch {
            products.updateProduct(productId, draft)
                .onSuccess {
                    form.value = FormState()
                    _effects.send(ProductDetailEffect.Saved)
                }
                .onFailure { form.value = FormState(failed = true, error = it.catalogError()) }
        }
    }

    private fun merge(sourceIds: Set<String>) {
        viewModelScope.launch {
            val result = products.mergeProducts(productId, sourceIds)
            _effects.send(
                result.fold(
                    onSuccess = { ProductDetailEffect.Merged(sourceIds.size) },
                    onFailure = { ProductDetailEffect.Failed(it.catalogError()) },
                ),
            )
        }
    }

    private companion object {
        /** The navigation argument (the property name of the app's ProductDetailDestination). */
        const val PRODUCT_ID = "productId"
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
