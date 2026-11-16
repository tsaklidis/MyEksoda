package io.eksoda.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.eksoda.core.data.repository.AnalyticsRepository
import io.eksoda.core.data.repository.ReceiptRepository
import io.eksoda.core.model.CategorySpending
import io.eksoda.core.model.ImportStatus
import io.eksoda.core.model.MonthPace
import io.eksoda.core.model.MonthlyTrend
import io.eksoda.core.model.Period
import io.eksoda.core.model.Receipt
import io.eksoda.core.model.SpendingSummary
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
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

/** What Home shows (docs/design/ui-redesign.md §5). Amounts are in the household's currency. */
data class HomeUiState(
    val loading: Boolean = true,
    /** Some figures could not be computed; the rest are shown. */
    val failed: Boolean = false,
    val today: LocalDate? = null,
    val greeting: Greeting = Greeting.DAY,
    val pace: MonthPace? = null,
    /** This month up to today. */
    val summary: SpendingSummary? = null,
    val trend: MonthlyTrend? = null,
    val categories: CategorySpending? = null,
    val importing: Int = 0,
    val recent: List<Receipt> = emptyList(),
) {
    val needsReview: Int get() = summary?.needsReviewCount ?: 0
}

enum class Greeting { MORNING, DAY, EVENING }

/** The analytics part of the state, reloaded whenever the data changes. */
private data class Figures(
    val today: LocalDate,
    val pace: MonthPace?,
    val summary: SpendingSummary?,
    val trend: MonthlyTrend?,
    val categories: CategorySpending?,
    val failed: Boolean,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val analytics: AnalyticsRepository,
    receipts: ReceiptRepository,
) : ViewModel() {
    private val retries = MutableStateFlow(0)

    private val figures = retries.flatMapLatest { analytics.changes() }.mapLatest { load() }

    val uiState: StateFlow<HomeUiState> = combine(
        figures.map<Figures, Figures?> { it }.onStart { emit(null) },
        receipts.getImportJobs().map { jobs -> jobs.count { it.status == ImportStatus.PENDING } },
        receipts.getRecentReceipts(RECENT_COUNT),
    ) { figures, importing, recent ->
        HomeUiState(
            loading = figures == null,
            failed = figures?.failed == true,
            today = figures?.today,
            greeting = greetingAt(LocalTime.now()),
            pace = figures?.pace,
            summary = figures?.summary,
            trend = figures?.trend,
            categories = figures?.categories,
            importing = importing,
            recent = recent,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), HomeUiState())

    fun retry() {
        retries.value++
    }

    private suspend fun load(): Figures = coroutineScope {
        val today = analytics.today()
        val soFar = Period.Range(today.withDayOfMonth(1), today)
        val pace = async { analytics.getMonthPace() }
        val summary = async { analytics.getSummary(soFar) }
        val trend = async { analytics.getMonthlyTrend(TREND_MONTHS) }
        val categories = async { analytics.getSpendingByCategory(soFar) }
        val results = listOf(pace.await(), summary.await(), trend.await(), categories.await())
        Figures(
            today = today,
            pace = pace.await().getOrNull(),
            summary = summary.await().getOrNull(),
            trend = trend.await().getOrNull(),
            categories = categories.await().getOrNull(),
            failed = results.any { it.isFailure },
        )
    }

    private companion object {
        const val STOP_TIMEOUT = 5_000L
        const val RECENT_COUNT = 3
        const val TREND_MONTHS = 6
    }
}

private const val MORNING_FROM = 5
private const val DAY_FROM = 12
private const val EVENING_FROM = 18

internal fun greetingAt(time: LocalTime): Greeting = when (time.hour) {
    in MORNING_FROM until DAY_FROM -> Greeting.MORNING
    in DAY_FROM until EVENING_FROM -> Greeting.DAY
    else -> Greeting.EVENING
}
