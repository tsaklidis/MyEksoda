package io.eksoda.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.eksoda.core.data.repository.BillRepository
import io.eksoda.core.data.repository.GoalsRepository
import io.eksoda.core.data.repository.HouseholdRepository
import io.eksoda.core.model.BillKind
import io.eksoda.core.ui.text.parseMoneyInput
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

/** The bills offered on the "Your month" page: each becomes a bill when an amount is typed. */
enum class StarterBill(val kind: BillKind) {
    RENT(BillKind.FIXED),
    ELECTRICITY(BillKind.VARIABLE),
    WATER(BillKind.FIXED),
    INTERNET(BillKind.FIXED),
    MOBILE(BillKind.FIXED),
    TV(BillKind.FIXED),
}

data class OnboardingUiState(
    val name: String = "",
    /** The everyday budget as typed; empty means none. */
    val budget: String = "",
    /** The bills' amounts as typed; a missing or empty one means none. */
    val bills: Map<StarterBill, String> = emptyMap(),
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

    /** The bills typed as amounts above 0. */
    val billAmounts: Map<StarterBill, BigDecimal>
        get() = bills.mapNotNull { (bill, text) ->
            parseMoneyInput(text)?.takeIf { it.signum() > 0 }?.let { bill to it }
        }.toMap()

    /** The bills whose field holds something that is not an amount above 0. */
    val invalidBills: Set<StarterBill>
        get() = bills.filter { (bill, text) -> text.isNotBlank() && bill !in billAmounts }.keys

    /** What the month comes to: the bills and the everyday budget. */
    val monthTotal: BigDecimal
        get() = billAmounts.values.fold(budgetAmount ?: BigDecimal.ZERO, BigDecimal::add)

    val canCreate: Boolean
        get() = name.isNotBlank() && !budgetInvalid && invalidBills.isEmpty() && !creating

    companion object {
        const val DEFAULT_CURRENCY = "EUR"
        const val DEFAULT_TIMEZONE = "Europe/Athens"
    }
}

sealed interface OnboardingEvent {
    data class NameChanged(val name: String) : OnboardingEvent

    data class BudgetChanged(val budget: String) : OnboardingEvent

    data class BillChanged(val bill: StarterBill, val amount: String) : OnboardingEvent

    /** [billNames]: each starter bill's name in the app's language, as it will be saved. */
    data class Create(val billNames: Map<StarterBill, String>) : OnboardingEvent
}

/**
 * Creating the household on the last page of the onboarding, with the bills and the everyday
 * budget typed on "Your month". The app leaves onboarding by itself once the household exists,
 * so nothing is written before the last page. Restoring a backup and the app lock come with L7.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val households: HouseholdRepository,
    private val goals: GoalsRepository,
    private val bills: BillRepository,
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

            is OnboardingEvent.BillChanged -> _state.update {
                it.copy(bills = it.bills + (event.bill to event.amount), failed = false)
            }

            is OnboardingEvent.Create -> create(event.billNames)
        }
    }

    private fun create(billNames: Map<StarterBill, String>) {
        val current = _state.value
        if (!current.canCreate) return
        _state.update { it.copy(creating = true, failed = false) }
        viewModelScope.launch {
            val result =
                households.createHousehold(current.name, current.currency, current.timezone)
            // A budget or a bill that cannot be saved is not worth holding the household back
            // for: they can be set again from More.
            if (result.isSuccess) {
                current.budgetAmount?.let { goals.setEveryday(it) }
                current.billAmounts.forEach { (bill, amount) ->
                    bills.add(billNames[bill] ?: bill.name, bill.kind, amount)
                }
            }
            _state.update { it.copy(creating = false, failed = result.isFailure) }
        }
    }
}
