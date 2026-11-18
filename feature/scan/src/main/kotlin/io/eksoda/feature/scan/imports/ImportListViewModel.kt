package io.eksoda.feature.scan.imports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import dagger.hilt.android.lifecycle.HiltViewModel
import io.eksoda.core.data.repository.ReceiptRepository
import io.eksoda.core.model.ImportJob
import io.eksoda.core.model.ImportStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** All · Importing · Failed · Done. */
enum class ImportFilter(val status: ImportStatus?) {
    ALL(null),
    IMPORTING(ImportStatus.PENDING),
    FAILED(ImportStatus.FAILED),
    DONE(ImportStatus.COMPLETED),
}

sealed interface ImportListEvent {
    data class FilterChanged(val filter: ImportFilter) : ImportListEvent

    data class Retry(val jobId: String) : ImportListEvent
}

sealed interface ImportListEffect {
    data object RetryFailed : ImportListEffect
}

/**
 * The local import jobs, paged and filtered by status in the query. Room updates the list when
 * a job changes, so nothing polls.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ImportListViewModel @Inject constructor(private val receipts: ReceiptRepository) :
    ViewModel() {
    private val _filter = MutableStateFlow(ImportFilter.ALL)
    val filter: StateFlow<ImportFilter> = _filter.asStateFlow()

    val items: Flow<PagingData<ImportJob>> = _filter
        .flatMapLatest { receipts.getImportJobs(it.status) }
        .cachedIn(viewModelScope)

    private val _effects = Channel<ImportListEffect>(Channel.BUFFERED)
    val effects: Flow<ImportListEffect> = _effects.receiveAsFlow()

    fun onEvent(event: ImportListEvent) {
        when (event) {
            is ImportListEvent.FilterChanged -> _filter.value = event.filter

            is ImportListEvent.Retry -> viewModelScope.launch {
                if (receipts.retryImport(event.jobId).isFailure) {
                    _effects.send(ImportListEffect.RetryFailed)
                }
            }
        }
    }
}
