package io.eksoda.feature.receipts.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import dagger.hilt.android.lifecycle.HiltViewModel
import io.eksoda.core.data.repository.ReceiptRepository
import io.eksoda.core.model.Receipt
import io.eksoda.core.model.ReceiptFilter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class ReceiptListViewModel @Inject constructor(receiptRepository: ReceiptRepository) :
    ViewModel() {

    private val _filter = MutableStateFlow(ReceiptFilter())
    val filter: StateFlow<ReceiptFilter> = _filter.asStateFlow()

    /** Reloads when the filter changes. Typing is debounced, so each key press is not a query. */
    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    val receipts: Flow<PagingData<Receipt>> = _filter
        .debounce(SEARCH_DEBOUNCE_MS)
        .distinctUntilChanged()
        .flatMapLatest { receiptRepository.getReceipts(it) }
        .cachedIn(viewModelScope)

    fun onQueryChange(query: String) {
        _filter.update { it.copy(query = query.ifBlank { null }) }
    }

    fun onFilterChange(newFilter: ReceiptFilter) {
        _filter.value = newFilter
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 300L
    }
}
