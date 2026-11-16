package io.eksoda.feature.scan.imports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.eksoda.core.data.repository.ReceiptRepository
import io.eksoda.core.model.ImportJob
import io.eksoda.core.model.ImportStatus
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** All · Importing · Failed · Done (android-app.md §6.3.5). */
enum class ImportFilter(val status: ImportStatus?) {
    ALL(null),
    IMPORTING(ImportStatus.PENDING),
    FAILED(ImportStatus.FAILED),
    DONE(ImportStatus.COMPLETED),
}

data class ImportListUiState(
    val imports: List<ImportJob> = emptyList(),
    val filter: ImportFilter = ImportFilter.ALL,
    val loading: Boolean = true,
)

sealed interface ImportListEvent {
    data class FilterChanged(val filter: ImportFilter) : ImportListEvent

    data class Retry(val jobId: String) : ImportListEvent
}

sealed interface ImportListEffect {
    data object RetryFailed : ImportListEffect
}

/** The local import jobs. Room updates the list when a job changes, so nothing polls (§7.2). */
@HiltViewModel
class ImportListViewModel @Inject constructor(private val receipts: ReceiptRepository) :
    ViewModel() {
    private val filter = MutableStateFlow(ImportFilter.ALL)

    val state: StateFlow<ImportListUiState> =
        combine(receipts.getImportJobs(), filter) { jobs, current ->
            ImportListUiState(
                imports = current.status?.let { status -> jobs.filter { it.status == status } }
                    ?: jobs,
                filter = current,
                loading = false,
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            ImportListUiState(),
        )

    private val _effects = Channel<ImportListEffect>(Channel.BUFFERED)
    val effects: Flow<ImportListEffect> = _effects.receiveAsFlow()

    fun onEvent(event: ImportListEvent) {
        when (event) {
            is ImportListEvent.FilterChanged -> filter.value = event.filter

            is ImportListEvent.Retry -> viewModelScope.launch {
                if (receipts.retryImport(event.jobId).isFailure) {
                    _effects.send(ImportListEffect.RetryFailed)
                }
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
