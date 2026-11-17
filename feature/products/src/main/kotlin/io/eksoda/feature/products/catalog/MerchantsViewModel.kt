package io.eksoda.feature.products.catalog

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.eksoda.core.data.repository.CatalogRepository
import io.eksoda.core.model.CatalogError
import io.eksoda.core.model.Category
import io.eksoda.core.model.MerchantSummary
import io.eksoda.core.model.Store
import io.eksoda.feature.products.common.catalogError
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val STOP_TIMEOUT_MS = 5_000L

/** Every merchant, the most frequent first. The screen filters by name on the device. */
@HiltViewModel
class MerchantsViewModel @Inject constructor(catalog: CatalogRepository) : ViewModel() {
    /** Null while loading. */
    val merchants: StateFlow<List<MerchantSummary>?> = catalog.getMerchants()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)
}

sealed interface MerchantDetailUiState {
    data object Loading : MerchantDetailUiState

    data object Missing : MerchantDetailUiState

    data class Loaded(
        val merchant: MerchantSummary,
        val stores: List<Store>,
        val categories: List<Category>,
    ) : MerchantDetailUiState
}

sealed interface MerchantDetailEvent {
    /** A blank name goes back to the name derived from the legal name. */
    data class Rename(val name: String) : MerchantDetailEvent

    data class DefaultCategoryChanged(val categoryId: String?) : MerchantDetailEvent
}

sealed interface MerchantDetailEffect {
    data class Failed(val error: CatalogError?) : MerchantDetailEffect
}

/**
 * Merchant detail: rename, and the default category, which the receipt category filter and
 * Insights use for the merchant's receipts.
 */
@HiltViewModel
class MerchantDetailViewModel @Inject constructor(
    private val catalog: CatalogRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val merchantId: String = checkNotNull(savedStateHandle[MERCHANT_ID])

    val state: StateFlow<MerchantDetailUiState> = combine(
        catalog.getMerchant(merchantId),
        catalog.getStores(merchantId),
        catalog.getCategories().map { list -> list.map { it.category } },
    ) { merchant, stores, categories ->
        if (merchant == null) {
            MerchantDetailUiState.Missing
        } else {
            MerchantDetailUiState.Loaded(merchant, stores, categories)
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        MerchantDetailUiState.Loading,
    )

    private val _effects = Channel<MerchantDetailEffect>(Channel.BUFFERED)
    val effects: Flow<MerchantDetailEffect> = _effects.receiveAsFlow()

    fun onEvent(event: MerchantDetailEvent) {
        viewModelScope.launch {
            val result = when (event) {
                is MerchantDetailEvent.Rename -> catalog.renameMerchant(merchantId, event.name)

                is MerchantDetailEvent.DefaultCategoryChanged ->
                    catalog.setDefaultCategory(merchantId, event.categoryId)
            }
            result.exceptionOrNull()?.let {
                _effects.send(MerchantDetailEffect.Failed(it.catalogError()))
            }
        }
    }

    private companion object {
        /** The navigation argument (the property name of the app's MerchantDetailDestination). */
        const val MERCHANT_ID = "merchantId"
    }
}
