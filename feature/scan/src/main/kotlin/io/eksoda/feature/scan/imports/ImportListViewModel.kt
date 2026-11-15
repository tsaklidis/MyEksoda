package io.eksoda.feature.scan.imports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.eksoda.core.data.repository.ReceiptRepository
import io.eksoda.core.model.ImportJob
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The local import jobs. Room updates the list when a job changes, so nothing polls (§7.2). */
@HiltViewModel
class ImportListViewModel @Inject constructor(
    private val receiptRepository: ReceiptRepository,
) : ViewModel() {

    val uiState: StateFlow<ImportListUiState> = receiptRepository.getImportJobs()
        .map { ImportListUiState(imports = it, isLoading = false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ImportListUiState())

    fun retryImport(id: String) {
        viewModelScope.launch { receiptRepository.retryImport(id) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

data class ImportListUiState(
    val imports: List<ImportJob> = emptyList(),
    val isLoading: Boolean = true,
)
