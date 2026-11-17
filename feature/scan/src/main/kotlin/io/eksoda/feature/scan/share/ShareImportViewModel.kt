package io.eksoda.feature.scan.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.eksoda.core.data.repository.ReceiptRepository
import io.eksoda.feature.scan.ImportFeedback
import io.eksoda.feature.scan.toFeedback
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ShareImportUiState {
    data object Confirm : ShareImportUiState

    data object Saving : ShareImportUiState

    data class Done(val feedback: ImportFeedback) : ShareImportUiState
}

/** Submits a link shared to Eksoda after the user confirms. */
@HiltViewModel
class ShareImportViewModel @Inject constructor(private val receipts: ReceiptRepository) :
    ViewModel() {
    private val _state = MutableStateFlow<ShareImportUiState>(ShareImportUiState.Confirm)
    val state: StateFlow<ShareImportUiState> = _state.asStateFlow()

    fun import(link: String) {
        if (_state.value != ShareImportUiState.Confirm) return
        _state.value = ShareImportUiState.Saving
        viewModelScope.launch {
            val feedback = receipts.importReceipt(link).getOrNull()?.toFeedback(number = 1)
                ?: ImportFeedback.Failed
            _state.value = ShareImportUiState.Done(feedback)
        }
    }

    /** Ready for the next shared link. */
    fun reset() {
        _state.value = ShareImportUiState.Confirm
    }
}
