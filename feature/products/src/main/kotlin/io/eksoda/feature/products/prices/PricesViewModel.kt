package io.eksoda.feature.products.prices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.eksoda.core.data.repository.PriceBoardRepository
import io.eksoda.core.model.PriceBoard
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

sealed interface PricesUiState {
    data object Loading : PricesUiState

    data object Failed : PricesUiState

    data class Loaded(val board: PriceBoard) : PricesUiState
}

/** The Prices tab: where each product costs least, what each shop is cheapest for, trends. */
@HiltViewModel
class PricesViewModel @Inject constructor(board: PriceBoardRepository) : ViewModel() {
    val state: StateFlow<PricesUiState> = board.getBoard()
        .map<PriceBoard, PricesUiState> { PricesUiState.Loaded(it) }
        .catch { emit(PricesUiState.Failed) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            PricesUiState.Loading,
        )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
