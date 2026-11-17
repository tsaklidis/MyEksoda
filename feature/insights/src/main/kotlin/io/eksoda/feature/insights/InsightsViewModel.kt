package io.eksoda.feature.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.eksoda.core.data.repository.AnalyticsRepository
import io.eksoda.core.model.CategorySpending
import io.eksoda.core.model.MerchantSpending
import io.eksoda.core.model.MonthPace
import io.eksoda.core.model.MonthlyTrend
import io.eksoda.core.model.Period
import io.eksoda.core.model.PeriodPreset
import io.eksoda.core.model.SpendingSummary
import io.eksoda.core.model.TopProduct
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
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
    val categories: CardState<CategorySpending> = CardState.Loading,
    val merchants: CardState<List<MerchantSpending>> = CardState.Loading,
    val trend: CardState<MonthlyTrend> = CardState.Loading,
    val topBySpend: CardState<List<TopProduct>> = CardState.Loading,
    val topByCount: CardState<List<TopProduct>> = CardState.Loading,
)

sealed interface InsightsEvent {
    data class PeriodSelected(val period: InsightsPeriod) : InsightsEvent

    data object Retry : InsightsEvent
}

private data class Request(val period: InsightsPeriod, val attempt: Int)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class InsightsViewModel @Inject constructor(private val analytics: AnalyticsRepository) :
    ViewModel() {
    private val request = MutableStateFlow(Request(InsightsPeriod.DEFAULT, 0))

    val uiState: StateFlow<InsightsUiState> = request
        .flatMapLatest { current -> analytics.changes().map { current } }
        .mapLatest { load(it.period) }
        .let { loaded ->
            combine(loaded, request) { state, current ->
                // While another period loads, the chips already show the new choice.
                if (state.period == current.period) {
                    state
                } else {
                    InsightsUiState(period = current.period)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), InsightsUiState())

    fun onEvent(event: InsightsEvent) {
        request.value = when (event) {
            is InsightsEvent.PeriodSelected -> Request(event.period, request.value.attempt)
            InsightsEvent.Retry -> request.value.copy(attempt = request.value.attempt + 1)
        }
    }

    private suspend fun load(period: InsightsPeriod): InsightsUiState = coroutineScope {
        val today = analytics.today()
        val range = rangeOf(period, today)
        val summary = async { analytics.getSummary(range).card() }
        val pace = if (period == InsightsPeriod.DEFAULT) {
            async { analytics.getMonthPace().card() }
        } else {
            null
        }
        val categories = async { analytics.getSpendingByCategory(range).card() }
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
