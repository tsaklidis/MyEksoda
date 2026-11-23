package io.eksoda.feature.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.eksoda.core.data.repository.AnalyticsRepository
import io.eksoda.core.data.repository.GoalsRepository
import io.eksoda.core.data.repository.HouseholdRepository
import io.eksoda.core.data.repository.SettingsRepository
import io.eksoda.core.model.CategorySpending
import io.eksoda.core.model.EPaymentProgress
import io.eksoda.core.model.MerchantSpending
import io.eksoda.core.model.MonthBudgets
import io.eksoda.core.model.MonthPace
import io.eksoda.core.model.MonthlyTrend
import io.eksoda.core.model.Period
import io.eksoda.core.model.PeriodPreset
import io.eksoda.core.model.SpendingSummary
import io.eksoda.core.model.TopProduct
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException
import java.math.BigDecimal
import java.time.LocalDate
import javax.inject.Inject

/** The period chips (docs/design/ui-redesign.md §5): a preset, or days the user picked. */
sealed interface InsightsPeriod {
    data class Preset(val preset: PeriodPreset) : InsightsPeriod

    /** Both ends inclusive. */
    data class Custom(val from: LocalDate, val to: LocalDate) : InsightsPeriod

    companion object {
        val DEFAULT: InsightsPeriod = Preset(PeriodPreset.THIS_MONTH)
    }
}

/** A card's data: each card loads and fails on its own. */
sealed interface CardState<out T> {
    data object Loading : CardState<Nothing>

    data object Failed : CardState<Nothing>

    data class Ready<T>(val value: T) : CardState<T>
}

data class InsightsUiState(
    val period: InsightsPeriod = InsightsPeriod.DEFAULT,
    val range: Period? = null,
    val today: LocalDate? = null,
    val summary: CardState<SpendingSummary> = CardState.Loading,
    /** This month day by day against last month; only for "This month", else null. */
    val pace: CardState<MonthPace>? = null,
    /** This month's budgets; only for "This month", like [pace]. */
    val budgets: CardState<MonthBudgets>? = null,
    /** This year's e-payments, whatever the period (the card names its year). */
    val ePayments: CardState<EPaymentProgress> = CardState.Loading,
    val categories: CardState<CategorySpending> = CardState.Loading,
    val merchants: CardState<List<MerchantSpending>> = CardState.Loading,
    val trend: CardState<MonthlyTrend> = CardState.Loading,
    val topBySpend: CardState<List<TopProduct>> = CardState.Loading,
    val topByCount: CardState<List<TopProduct>> = CardState.Loading,
    /** The cards the user folded to their title; remembered across launches. */
    val folded: Set<InsightsCard> = emptySet(),
)

sealed interface InsightsEvent {
    data class PeriodSelected(val period: InsightsPeriod) : InsightsEvent

    data object Retry : InsightsEvent

    /** The annual income for the e-payment target; null clears it. */
    data class IncomeSet(val income: BigDecimal?) : InsightsEvent

    /** Folds an open card to its title, or opens a folded one. */
    data class CardToggled(val card: InsightsCard) : InsightsEvent
}

sealed interface InsightsEffect {
    /** The annual income could not be saved. */
    data object IncomeSaveFailed : InsightsEffect
}

private data class Request(val period: InsightsPeriod, val attempt: Int)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class InsightsViewModel @Inject constructor(
    private val analytics: AnalyticsRepository,
    private val goals: GoalsRepository,
    private val households: HouseholdRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    private val folded = settings.foldedInsightsCards.map { keys ->
        keys.mapNotNull { key -> InsightsCard.entries.firstOrNull { it.name == key } }.toSet()
    }

    private val request = MutableStateFlow(Request(InsightsPeriod.DEFAULT, 0))

    private val _effects = Channel<InsightsEffect>(Channel.BUFFERED)
    val effects: Flow<InsightsEffect> = _effects.receiveAsFlow()

    val uiState: StateFlow<InsightsUiState> = request
        .flatMapLatest { current -> analytics.changes().map { current } }
        .mapLatest { load(it.period) }
        .let { loaded ->
            combine(loaded, request, folded) { state, current, foldedCards ->
                // While another period loads, the chips already show the new choice.
                if (state.period == current.period) {
                    state.copy(folded = foldedCards)
                } else {
                    InsightsUiState(period = current.period, folded = foldedCards)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), InsightsUiState())

    fun onEvent(event: InsightsEvent) {
        when (event) {
            is InsightsEvent.PeriodSelected ->
                request.value = Request(event.period, request.value.attempt)

            InsightsEvent.Retry ->
                request.value = request.value.copy(attempt = request.value.attempt + 1)

            // The cards reload by themselves: the household table changed.
            is InsightsEvent.IncomeSet -> viewModelScope.launch {
                if (households.setAnnualIncome(event.income).isFailure) {
                    _effects.send(InsightsEffect.IncomeSaveFailed)
                }
            }

            is InsightsEvent.CardToggled -> viewModelScope.launch {
                try {
                    settings.toggleInsightsCardFolded(event.card.name)
                } catch (ignored: IOException) {
                    // The settings file cannot be written: the card stays as it was, which the
                    // user sees, so there is nothing else to say.
                }
            }
        }
    }

    private suspend fun load(period: InsightsPeriod): InsightsUiState = coroutineScope {
        val today = analytics.today()
        val range = rangeOf(period, today)
        val summary = async { analytics.getSummary(range).card() }
        val thisMonth = period == InsightsPeriod.DEFAULT
        val pace = if (thisMonth) async { analytics.getMonthPace().card() } else null
        val spending = async { analytics.getSpendingByCategory(range) }
        // This month's budgets reuse the period's category spending (it is the same month).
        val budgets = if (thisMonth) {
            async { goals.getMonthBudgets(spending.await().getOrNull()).card() }
        } else {
            null
        }
        val ePayments = async { goals.getEPayments().card() }
        val categories = async { spending.await().card() }
        val merchants = async { analytics.getSpendingByMerchant(range).card() }
        val trend = async { analytics.getMonthlyTrend(TREND_MONTHS).card() }
        val bySpend =
            async { analytics.getTopProducts(range, byCount = false, TOP_PRODUCTS).card() }
        val byCount = async { analytics.getTopProducts(range, byCount = true, TOP_PRODUCTS).card() }
        InsightsUiState(
            period = period,
            range = range,
            today = today,
            summary = summary.await(),
            pace = pace?.await(),
            budgets = budgets?.await(),
            ePayments = ePayments.await(),
            categories = categories.await(),
            merchants = merchants.await(),
            trend = trend.await(),
            topBySpend = bySpend.await(),
            topByCount = byCount.await(),
        )
    }

    private fun <T> Result<T>.card(): CardState<T> =
        fold(onSuccess = { CardState.Ready(it) }, onFailure = { CardState.Failed })

    private companion object {
        const val STOP_TIMEOUT = 5_000L
        const val TREND_MONTHS = 12
        const val TOP_PRODUCTS = 5
    }
}

/**
 * The days a chip covers: a preset up to [today], a custom range as picked. The comparison is
 * the period of equal length before.
 */
internal fun rangeOf(period: InsightsPeriod, today: LocalDate): Period = when (period) {
    is InsightsPeriod.Preset -> period.preset.soFar(today)
    is InsightsPeriod.Custom -> Period.Range(period.from, period.to)
}
