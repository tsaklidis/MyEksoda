package io.eksoda.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.eksoda.core.data.repository.GoalsRepository
import io.eksoda.core.data.repository.HouseholdRepository
import io.eksoda.core.ui.text.parseMoneyInput
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

data class OnboardingUiState(
    val name: String = "",
    /** The monthly budget as typed; empty means none. */
    val budget: String = "",
    val currency: String = DEFAULT_CURRENCY,
    val timezone: String = DEFAULT_TIMEZONE,
    val creating: Boolean = false,
    val failed: Boolean = false,
) {
    /** The budget typed, or null when the field is empty or not an amount above 0. */
    val budgetAmount: BigDecimal?
        get() = parseMoneyInput(budget)?.takeIf { it.signum() > 0 }

    /** Something is typed in the budget field that is not an amount above 0. */
    val budgetInvalid: Boolean get() = budget.isNotBlank() && budgetAmount == null

    val canCreate: Boolean get() = name.isNotBlank() && !budgetInvalid && !creating

    companion object {
        const val DEFAULT_CURRENCY = "EUR"
        const val DEFAULT_TIMEZONE = "Europe/Athens"
    }
}

sealed interface OnboardingEvent {
    data class NameChanged(val name: String) : OnboardingEvent

    data class BudgetChanged(val budget: String) : OnboardingEvent

    data object Create : OnboardingEvent
}

/**
 * Creating the household on the last page of the onboarding, with the monthly budget if one was
 * typed. The app leaves onboarding by itself once the household exists, so nothing is written
 * before the last page. Restoring a backup and the app lock come with L7.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val households: HouseholdRepository,
    private val goals: GoalsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    fun onEvent(event: OnboardingEvent) {
        when (event) {
            is OnboardingEvent.NameChanged -> _state.update {
                it.copy(name = event.name, failed = false)
            }

            is OnboardingEvent.BudgetChanged -> _state.update {
                it.copy(budget = event.budget, failed = false)
            }

            OnboardingEvent.Create -> create()
        }
    }

    private fun create() {
        val current = _state.value
        if (!current.canCreate) return
        _state.update { it.copy(creating = true, failed = false) }
        viewModelScope.launch {
            val result =
                households.createHousehold(current.name, current.currency, current.timezone)
            // A budget that cannot be saved is not worth holding the household back for: it can
            // be set again in Settings → Budgets.
            if (result.isSuccess) current.budgetAmount?.let { goals.setBudget(null, it) }
            _state.update { it.copy(creating = false, failed = result.isFailure) }
        }
    }
}
