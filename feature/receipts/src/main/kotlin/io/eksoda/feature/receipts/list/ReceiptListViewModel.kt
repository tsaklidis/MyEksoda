package io.eksoda.feature.receipts.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.insertSeparators
import androidx.paging.map
import dagger.hilt.android.lifecycle.HiltViewModel
import io.eksoda.core.data.repository.ReceiptFilterStore
import io.eksoda.core.data.repository.ReceiptOptionsRepository
import io.eksoda.core.data.repository.ReceiptRepository
import io.eksoda.core.model.Option
import io.eksoda.core.model.Receipt
import io.eksoda.core.model.ReceiptFilter
import io.eksoda.core.model.ReceiptOrdering
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** A row of the list: a day header (date orderings only) or a receipt. */
sealed interface ReceiptListItem {
    data class Day(val date: LocalDate) : ReceiptListItem

    data class Row(val receipt: Receipt) : ReceiptListItem
}

/** Choices for the filter sheet. */
data class FilterOptions(
    val merchants: List<Option> = emptyList(),
    /** The stores of the merchant chosen in the sheet. */
    val stores: List<Option> = emptyList(),
    val categories: List<Option> = emptyList(),
    val products: List<Option> = emptyList(),
)

data class ReceiptListUiState(
    val filter: ReceiptFilter = ReceiptFilter(),
    val options: FilterOptions = FilterOptions(),
)

sealed interface ReceiptListEvent {
    data class QueryChanged(val query: String) : ReceiptListEvent

    data class OrderingChanged(val ordering: ReceiptOrdering) : ReceiptListEvent

    /** The filter sheet's Apply, or a removed chip. */
    data class FilterChanged(val filter: ReceiptFilter) : ReceiptListEvent

    data object FiltersCleared : ReceiptListEvent

    /** The merchant picked in the sheet, before Apply: its stores are listed. */
    data class SheetMerchantChanged(val merchantId: String?) : ReceiptListEvent

    data class ProductSearched(val query: String) : ReceiptListEvent

    /** A swipe right, or Undo: include or exclude the receipt from stats. */
    data class ExcludedChanged(val receiptId: String, val excluded: Boolean) : ReceiptListEvent

    /** A swipe left, after the user confirmed. */
    data class DeleteConfirmed(val receiptId: String) : ReceiptListEvent
}

sealed interface ReceiptListEffect {
    /** Shown with Undo, which changes it back. */
    data class ExcludedChanged(val receiptId: String, val excluded: Boolean) : ReceiptListEffect

    data object Deleted : ReceiptListEffect

    data object Failed : ReceiptListEffect
}

/**
 * The receipt list with search and filters. Only typing is
 * debounced (300 ms), so each key press is not a query; sorting, chips and Apply take effect at
 * once. Room re-runs the query when a receipt changes, so an import shows up by itself.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class ReceiptListViewModel @Inject constructor(
    private val receipts: ReceiptRepository,
    options: ReceiptOptionsRepository,
    private val filterStore: ReceiptFilterStore,
) : ViewModel() {
    /** Everything but the search text, which the screen keeps while it is typed. */
    private val filter = MutableStateFlow(ReceiptFilter())

    private val _effects = Channel<ReceiptListEffect>(Channel.BUFFERED)
    val effects: Flow<ReceiptListEffect> = _effects.receiveAsFlow()

    /** The saved filters are back (or there were none): the list may query. */
    private val restored = MutableStateFlow(false)
    private val query = MutableStateFlow("")
    private val sheetMerchant = MutableStateFlow<String?>(null)
    private val productQuery = MutableStateFlow("")

    /** The typed text after a pause; clearing it applies at once. */
    private val appliedQuery: Flow<String?> = query
        .debounce { if (it.isBlank()) 0L else SEARCH_DEBOUNCE_MS }
        .map { it.trim().ifEmpty { null } }
        .distinctUntilChanged()

    /**
     * The filter the list shows: the sheet's and chips' filter with the applied search text. It
     * waits for the saved filters, so the list does not load twice at start.
     */
    private val appliedFilter: Flow<ReceiptFilter> =
        combine(filter, appliedQuery, restored) { current, text, ready ->
            current.copy(query = text).takeIf { ready }
        }
            .filterNotNull()
            .distinctUntilChanged()

    init {
        viewModelScope.launch {
            val saved = runCatching { filterStore.load() }.getOrNull()
            // A change made while loading wins over the saved filters.
            if (saved != null) filter.compareAndSet(ReceiptFilter(), saved)
            restored.value = true
            filter.drop(1).collect { runCatching { filterStore.save(it) } }
        }
    }

    /**
     * Product suggestions for the filter sheet. A newer query cancels an older one, so a slow
     * search can never replace the results of what is typed now.
     */
    private val products: Flow<List<Option>> = productQuery
        .debounce { if (it.isBlank()) 0L else PRODUCT_DEBOUNCE_MS }
        .distinctUntilChanged()
        .mapLatest { text ->
            if (text.isBlank()) {
                emptyList()
            } else {
                runCatching { options.searchProducts(text) }.getOrDefault(emptyList())
            }
        }

    val items: Flow<PagingData<ReceiptListItem>> = appliedFilter
        .flatMapLatest { current ->
            receipts.getReceipts(current).map { page -> page.withDayHeaders(current.ordering) }
        }
        .cachedIn(viewModelScope)

    private val stores = sheetMerchant.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else options.storeOptions(id)
    }

    val state: StateFlow<ReceiptListUiState> = combine(
        appliedFilter,
        options.merchantOptions(),
        stores,
        options.categoryOptions(),
        products,
    ) { current, merchants, storeList, categories, productList ->
        ReceiptListUiState(
            filter = current,
            options = FilterOptions(merchants, storeList, categories, productList),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ReceiptListUiState())

    fun onEvent(event: ReceiptListEvent) {
        when (event) {
            is ReceiptListEvent.QueryChanged -> query.value = event.query

            is ReceiptListEvent.OrderingChanged ->
                filter.update { it.copy(ordering = event.ordering) }

            // The search text is not part of the sheet's or chips' filter: it stays as typed.
            is ReceiptListEvent.FilterChanged -> filter.value = event.filter.copy(query = null)

            ReceiptListEvent.FiltersCleared ->
                filter.update { ReceiptFilter(ordering = it.ordering) }

            is ReceiptListEvent.SheetMerchantChanged -> sheetMerchant.value = event.merchantId

            is ReceiptListEvent.ProductSearched -> productQuery.value = event.query

            is ReceiptListEvent.ExcludedChanged -> viewModelScope.launch {
                val result = receipts.setExcludedFromAnalytics(event.receiptId, event.excluded)
                _effects.send(
                    if (result.isSuccess) {
                        ReceiptListEffect.ExcludedChanged(event.receiptId, event.excluded)
                    } else {
                        ReceiptListEffect.Failed
                    },
                )
            }

            is ReceiptListEvent.DeleteConfirmed -> viewModelScope.launch {
                val result = receipts.deleteReceipt(event.receiptId)
                _effects.send(
                    if (result.isSuccess) ReceiptListEffect.Deleted else ReceiptListEffect.Failed,
                )
            }
        }
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 300L
        const val PRODUCT_DEBOUNCE_MS = 200L
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

/** Day headers when the list is ordered by date; none when it is ordered by total. */
private fun PagingData<Receipt>.withDayHeaders(
    ordering: ReceiptOrdering,
): PagingData<ReceiptListItem> {
    val rows = map<Receipt, ReceiptListItem> { ReceiptListItem.Row(it) }
    if (ordering != ReceiptOrdering.NEWEST && ordering != ReceiptOrdering.OLDEST) return rows
    return rows.insertSeparators { before, after ->
        val next = (after as? ReceiptListItem.Row)?.receipt ?: return@insertSeparators null
        val previous = (before as? ReceiptListItem.Row)?.receipt
        if (previous?.issuedDate == next.issuedDate) null else ReceiptListItem.Day(next.issuedDate)
    }
}
