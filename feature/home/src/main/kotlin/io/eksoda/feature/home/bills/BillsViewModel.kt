package io.eksoda.feature.home.bills

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.eksoda.core.data.repository.BillRepository
import io.eksoda.core.model.BillKind
import io.eksoda.core.model.BillsMonth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.YearMonth
import javax.inject.Inject

data class BillsUiState(
    val loading: Boolean = true,
    /** The months that can be shown, oldest first; the last is the current one. */
    val months: List<YearMonth> = emptyList(),
    val month: BillsMonth? = null,
)

/** A bill as typed in the form: [amount] is needed for a fixed one. */
data class BillDraft(val name: String, val kind: BillKind, val amount: BigDecimal?)

sealed interface BillsEvent {
    data class MonthPicked(val month: YearMonth) : BillsEvent

    /** Bill [billId]'s amount in the month shown; null takes it back (fixed) or clears it. */
    data class SetAmount(val billId: String, val amount: BigDecimal?) : BillsEvent

    data class Add(val draft: BillDraft) : BillsEvent

    data class Update(val billId: String, val draft: BillDraft) : BillsEvent

    data class Remove(val billId: String) : BillsEvent
}

sealed interface BillsEffect {
    data object SaveFailed : BillsEffect
}

/**
 * The bills, month by month. The current month can be changed; past months are shown as they
 * were (core:engine `MonthAmounts.kt` decides, and the repository refuses the rest).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class BillsViewModel @Inject constructor(private val bills: BillRepository) : ViewModel() {
    /** The month shown; null until the current month is known. */
    private val shown = MutableStateFlow<YearMonth?>(null)

    val state: StateFlow<BillsUiState> = flow { emit(bills.currentMonth()) }
        .flatMapLatest { current ->
            val months = (MONTHS - 1 downTo 0).map { current.minusMonths(it.toLong()) }
            shown.flatMapLatest { picked ->
                bills.getMonth(picked ?: current).map { BillsUiState(false, months, it) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), BillsUiState())

    private val _effects = Channel<BillsEffect>(Channel.BUFFERED)
    val effects: Flow<BillsEffect> = _effects.receiveAsFlow()

    fun onEvent(event: BillsEvent) {
        when (event) {
            is BillsEvent.MonthPicked -> shown.value = event.month

            is BillsEvent.SetAmount -> save {
                val month = state.value.month?.month ?: bills.currentMonth()
                bills.setAmount(event.billId, month, event.amount)
            }

            is BillsEvent.Add -> save {
                bills.add(event.draft.name, event.draft.kind, event.draft.amount).map { }
            }

            is BillsEvent.Update -> save {
                bills.update(event.billId, event.draft.name, event.draft.kind, event.draft.amount)
            }

            is BillsEvent.Remove -> save { bills.remove(event.billId) }
        }
    }

    private fun save(block: suspend () -> Result<Unit>) {
        viewModelScope.launch {
            if (block().isFailure) _effects.send(BillsEffect.SaveFailed)
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L

        /** How many months can be looked back on, the current one included. */
        const val MONTHS = 4
    }
}
