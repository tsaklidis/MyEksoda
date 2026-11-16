package io.eksoda.feature.products.review

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
import io.eksoda.core.model.LineType
import io.eksoda.core.model.Product
import io.eksoda.core.model.ProductDraft
import io.eksoda.core.model.ProductFilter
import io.eksoda.core.model.ReviewItem
import io.eksoda.feature.products.common.catalogError
import io.eksoda.feature.products.common.debouncedQuery
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReviewUiState(
    val count: Int = 0,
    val categories: List<Category> = emptyList(),
    val saving: Boolean = false,
    val formFailed: Boolean = false,
    val formError: CatalogError? = null,
)

sealed interface ReviewEvent {
    data class PickerQueryChanged(val query: String) : ReviewEvent

    data class Link(val itemId: String, val productId: String) : ReviewEvent

    data class Create(val itemId: String, val draft: ProductDraft) : ReviewEvent

    data class Reclassify(val itemId: String, val lineType: LineType) : ReviewEvent

    data object FormClosed : ReviewEvent
}

sealed interface ReviewEffect {
    /** Linked or created: future receipts from the shop match the item automatically. */
    data object Matched : ReviewEffect

    data object Reclassified : ReviewEffect

    data class Failed(val error: CatalogError?) : ReviewEffect
}

/**
 * "Help us match these items" (`android-app.md` §6.7). The list follows the database, so a
 * resolved line leaves it by itself.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ReviewViewModel @Inject constructor(
    private val review: ReviewRepository,
    products: ProductRepository,
    catalog: CatalogRepository,
) : ViewModel() {
    private val pickerQuery = MutableStateFlow("")
    private val form = MutableStateFlow(FormState())

    val items: Flow<PagingData<ReviewItem>> = review.getReviewItems().cachedIn(viewModelScope)

    /** Products to link a line to, searched by name. */
    val pickerItems: Flow<PagingData<Product>> = pickerQuery.debouncedQuery()
        .flatMapLatest { products.getProducts(ProductFilter(query = it)) }
        .cachedIn(viewModelScope)

    val state: StateFlow<ReviewUiState> = combine(
        review.getReviewCount(),
        catalog.getCategories().map { list -> list.map { it.category } },
        form,
    ) { count, categories, formState ->
        ReviewUiState(count, categories, formState.saving, formState.failed, formState.error)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ReviewUiState())

    private val _effects = Channel<ReviewEffect>(Channel.BUFFERED)
    val effects: Flow<ReviewEffect> = _effects.receiveAsFlow()

    fun onEvent(event: ReviewEvent) {
        when (event) {
            is ReviewEvent.PickerQueryChanged -> pickerQuery.value = event.query

            is ReviewEvent.Link -> viewModelScope.launch {
                report(review.linkItem(event.itemId, event.productId), ReviewEffect.Matched)
            }

            is ReviewEvent.Create -> create(event.itemId, event.draft)

            is ReviewEvent.Reclassify -> viewModelScope.launch {
                val result = review.reclassifyItem(event.itemId, event.lineType)
                report(result, ReviewEffect.Reclassified)
            }

            ReviewEvent.FormClosed -> form.value = FormState()
        }
    }

    private fun create(itemId: String, draft: ProductDraft) {
        if (form.value.saving) return
        form.value = FormState(saving = true)
        viewModelScope.launch {
            review.createProductForItem(itemId, draft)
                .onSuccess {
                    form.value = FormState()
                    _effects.send(ReviewEffect.Matched)
                }
                .onFailure { form.value = FormState(failed = true, error = it.catalogError()) }
        }
    }

    private suspend fun report(result: Result<Unit>, success: ReviewEffect) {
        _effects.send(
            result.fold(
                onSuccess = { success },
                onFailure = { ReviewEffect.Failed(it.catalogError()) },
            ),
        )
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
