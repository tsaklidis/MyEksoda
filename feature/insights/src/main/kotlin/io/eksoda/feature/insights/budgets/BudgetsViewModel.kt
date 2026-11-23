package io.eksoda.feature.insights.budgets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.eksoda.core.data.repository.GoalsRepository
import io.eksoda.core.model.EverydayBudget
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

data class BudgetsUiState(
    val loading: Boolean = true,
    /** Null while loading. */
    val everyday: EverydayBudget? = null,
)

sealed interface BudgetsEvent {
    /** The everyday budget of every month from this one on; null removes it. */
    data class SetDefault(val amount: BigDecimal?) : BudgetsEvent

    /** This month's everyday budget only; null goes back to the default. */
    data class SetThisMonth(val amount: BigDecimal?) : BudgetsEvent
}

sealed interface BudgetsEffect {
    data object SaveFailed : BudgetsEffect
}

/**
 * The everyday budget: one amount for every month, and the current month's own when it differs.
 * Category limits are set in each category. It follows the database, so a saved budget shows by
 * itself.
 */
@HiltViewModel
class BudgetsViewModel @Inject constructor(private val goals: GoalsRepository) : ViewModel() {
    val state: StateFlow<BudgetsUiState> = goals.getEveryday()
        .map { BudgetsUiState(loading = false, everyday = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), BudgetsUiState())

    private val _effects = Channel<BudgetsEffect>(Channel.BUFFERED)
    val effects: Flow<BudgetsEffect> = _effects.receiveAsFlow()

    fun onEvent(event: BudgetsEvent) {
        viewModelScope.launch {
            val result = when (event) {
                is BudgetsEvent.SetDefault -> goals.setEveryday(event.amount)
                is BudgetsEvent.SetThisMonth -> goals.setEverydayThisMonth(event.amount)
            }
            if (result.isFailure) _effects.send(BudgetsEffect.SaveFailed)
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
