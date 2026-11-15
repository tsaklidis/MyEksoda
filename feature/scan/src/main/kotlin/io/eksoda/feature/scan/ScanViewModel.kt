package io.eksoda.feature.scan

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.eksoda.core.data.repository.ReceiptRepository
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val receiptRepository: ReceiptRepository,
) : ViewModel() {

    var uiState by mutableStateOf(ScanUiState())
        private set

    fun onQrScanned(url: String) {
        // The camera reports the same code many times a second: submit it once. A failed submit
        // forgets the link, so pointing the camera at it again retries.
        if (uiState.isSubmitting || url == uiState.lastSubmittedUrl) return
        uiState = uiState.copy(isSubmitting = true, lastSubmittedUrl = url, error = null)
        viewModelScope.launch {
            val result = receiptRepository.importReceipt(url)
            uiState = if (result.isSuccess) {
                uiState.copy(isSubmitting = false, submitted = true)
            } else {
                uiState.copy(isSubmitting = false, lastSubmittedUrl = null, error = ScanError.SubmitFailed)
            }
        }
    }

    fun clearMessages() {
        uiState = uiState.copy(error = null, submitted = false)
    }
}

data class ScanUiState(
    val isSubmitting: Boolean = false,
    val lastSubmittedUrl: String? = null,
    val submitted: Boolean = false,
    val error: ScanError? = null,
)

enum class ScanError { SubmitFailed }
