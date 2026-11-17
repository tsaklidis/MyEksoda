package io.eksoda.feature.receipts.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.eksoda.core.data.repository.ReceiptOptionsRepository
import io.eksoda.core.data.repository.ReceiptRepository
import io.eksoda.core.model.Option
import io.eksoda.core.model.ReceiptDetail
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ReceiptDetailUiState {
    data object Loading : ReceiptDetailUiState

    /** Deleted, or never existed. */
    data object Missing : ReceiptDetailUiState

    data class Loaded(val detail: ReceiptDetail, val stores: List<Option>) : ReceiptDetailUiState
}

sealed interface ReceiptDetailEvent {
    data class NoteSaved(val note: String) : ReceiptDetailEvent

    /** "Include in stats" (the inverse of `excluded_from_analytics`). */
    data class IncludedChanged(val included: Boolean) : ReceiptDetailEvent

    data class StoreChanged(val storeId: String?) : ReceiptDetailEvent

    data object DeleteConfirmed : ReceiptDetailEvent

    /** "Open original", after the user confirmed. */
    data object OpenOriginalConfirmed : ReceiptDetailEvent
}

sealed interface ReceiptDetailEffect {
    /** Open the receipt link in a Custom Tab. The link is a credential: never log or share it. */
    class OpenLink(val link: String) : ReceiptDetailEffect {
        override fun toString(): String = "OpenLink(<link>)"
    }

    data object Deleted : ReceiptDetailEffect

    data object SaveFailed : ReceiptDetailEffect
}

/**
 * Receipt detail. Edits go to the database, and the screen follows the
 * database Flow, so a saved edit shows by itself; a failed one leaves the old value and says so.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ReceiptDetailViewModel @Inject constructor(
    private val receipts: ReceiptRepository,
    options: ReceiptOptionsRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val receiptId: String = checkNotNull(savedStateHandle[RECEIPT_ID])

    private val detail = receipts.getReceipt(receiptId)

    private val stores = detail
        .map { it?.receipt?.merchant?.id }
        .distinctUntilChanged()
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else options.storeOptions(id) }

    val state: StateFlow<ReceiptDetailUiState> = combine(detail, stores) { current, storeList ->
        if (current == null) {
            ReceiptDetailUiState.Missing
        } else {
            ReceiptDetailUiState.Loaded(current, storeList)
        }
    }
        .catch { emit(ReceiptDetailUiState.Missing) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            ReceiptDetailUiState.Loading,
        )

    private val _effects = Channel<ReceiptDetailEffect>(Channel.BUFFERED)
    val effects: Flow<ReceiptDetailEffect> = _effects.receiveAsFlow()

    fun onEvent(event: ReceiptDetailEvent) {
        viewModelScope.launch {
            when (event) {
                is ReceiptDetailEvent.NoteSaved -> save { receipts.setNote(receiptId, event.note) }

                is ReceiptDetailEvent.IncludedChanged ->
                    save { receipts.setExcludedFromAnalytics(receiptId, !event.included) }

                is ReceiptDetailEvent.StoreChanged ->
                    save { receipts.setStore(receiptId, event.storeId) }

                ReceiptDetailEvent.DeleteConfirmed -> delete()

                ReceiptDetailEvent.OpenOriginalConfirmed ->
                    receipts.originalLink(receiptId)?.let {
                        _effects.send(ReceiptDetailEffect.OpenLink(it))
                    }
            }
        }
    }

    private suspend fun save(write: suspend () -> Result<Unit>) {
        if (write().isFailure) _effects.send(ReceiptDetailEffect.SaveFailed)
    }

    private suspend fun delete() {
        val result = receipts.deleteReceipt(receiptId)
        _effects.send(
            if (result.isSuccess) ReceiptDetailEffect.Deleted else ReceiptDetailEffect.SaveFailed,
        )
    }

    private companion object {
        /** The navigation argument (the property name of the app's ReceiptDetailDestination). */
        const val RECEIPT_ID = "receiptId"
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
