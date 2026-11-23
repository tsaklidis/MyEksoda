package io.eksoda.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.eksoda.core.data.repository.AnalyticsRepository
import io.eksoda.core.data.repository.BillRepository
import io.eksoda.core.data.repository.GoalsRepository
import io.eksoda.core.data.repository.ReceiptRepository
import io.eksoda.core.model.BillsMonth
import io.eksoda.core.model.BudgetLevel
import io.eksoda.core.model.CategorySpending
import io.eksoda.core.model.MonthBudgets
import io.eksoda.core.model.Period
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import javax.inject.Inject

/**
 * The Overview (docs/design/ui-redesign.md §5): one month's everyday spending against its budget,
 * the month's bills, and its categories with their limits. The last [MONTHS] months can be
 * picked; a month that went over its budget is marked. Amounts are in the household's currency.
 */
data class HomeUiState(
    val loading: Boolean = true,
    /** Some figures could not be computed; the rest are shown. */
    val failed: Boolean = false,
    val greeting: Greeting = Greeting.DAY,
    val today: LocalDate? = null,
    /** The months that can be picked, oldest first; the last is the current one. */
    val months: List<YearMonth> = emptyList(),
    val selected: YearMonth? = null,
    /** The months whose spending passed their everyday budget. */
    val overMonths: Set<YearMonth> = emptySet(),
    /** The selected month's spending (so far, for the current month). */
    val spending: CategorySpending? = null,
    val budgets: MonthBudgets? = null,
    val bills: BillsMonth? = null,
    val importing: Int = 0,
    val needsReview: Int = 0,
) {
    val isCurrent: Boolean get() = selected != null && selected == months.lastOrNull()
}

enum class Greeting { MORNING, DAY, EVENING }

sealed interface HomeEvent {
    data class MonthPicked(val month: YearMonth) : HomeEvent

    data object Retry : HomeEvent
}

/** The analytics part of the state, reloaded whenever the data or the month changes. */
private data class Figures(
    val today: LocalDate,
    val month: YearMonth,
    val spending: CategorySpending?,
    val budgets: MonthBudgets?,
    val overMonths: Set<YearMonth>,
    val needsReview: Int,
    val failed: Boolean,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val analytics: AnalyticsRepository,
    private val goals: GoalsRepository,
    private val billRepository: BillRepository,
    receipts: ReceiptRepository,
) : ViewModel() {
    private val retries = MutableStateFlow(0)

    /** The month picked; null follows the current month. */
    private val picked = MutableStateFlow<YearMonth?>(null)

    // Spending changes with the receipts, budgets and limits with the budget rows (getEveryday
    // follows them all).
    private val figures = combine(
        retries.flatMapLatest { analytics.changes() },
        goals.getEveryday(),
        picked,
    ) { _, _, month -> month }.mapLatest { load(it) }

    private val monthBills = figures.flatMapLatest { loaded -> billsOf(loaded.month) }

    val uiState: StateFlow<HomeUiState> = combine(
        figures.map<Figures, Figures?> { it }.onStart { emit(null) },
        monthBills.onStart { emit(null) },
        receipts.pendingImportCount,
    ) { figures, bills, importing ->
        if (figures == null) {
            HomeUiState(greeting = greetingAt(LocalTime.now()), importing = importing)
        } else {
            val current = YearMonth.from(figures.today)
            HomeUiState(
                loading = false,
                failed = figures.failed,
                greeting = greetingAt(LocalTime.now()),
                today = figures.today,
                months = (MONTHS - 1 downTo 0).map { current.minusMonths(it.toLong()) },
                selected = figures.month,
                overMonths = figures.overMonths,
                spending = figures.spending,
                budgets = figures.budgets,
                bills = bills?.takeIf { it.month == figures.month },
                importing = importing,
                needsReview = figures.needsReview,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), HomeUiState())

    /** [month]'s bills, null until they are read. */
    private fun billsOf(month: YearMonth): Flow<BillsMonth?> =
        billRepository.getMonth(month).map<BillsMonth, BillsMonth?> { it }.onStart { emit(null) }

    fun onEvent(event: HomeEvent) {
        when (event) {
            is HomeEvent.MonthPicked -> picked.value = event.month
            HomeEvent.Retry -> retries.value++
        }
    }

    private suspend fun load(asked: YearMonth?): Figures = coroutineScope {
        val today = analytics.today()
        val current = YearMonth.from(today)
        val month = asked?.takeIf { it <= current } ?: current
        val end = if (month == current) today else month.atEndOfMonth()
        val spending = async { analytics.getSpendingByCategory(Period.Range(month.atDay(1), end)) }
        val summary = async { analytics.getSummary(Period.Range(current.atDay(1), today)) }
        // The month's budgets reuse its category spending rather than querying it again.
        val budgets = async { goals.getMonthBudgets(month, spending.await().getOrNull()) }
        // A mark on the months that went over is extra: when one fails, it just has none.
        val over = (0 until MONTHS).map { back ->
            async {
                val other = current.minusMonths(back.toLong())
                val result = if (other == month) budgets.await() else goals.getMonthBudgets(other)
                other.takeIf { result.getOrNull()?.total?.level == BudgetLevel.OVER }
            }
        }
        Figures(
            today = today,
            month = month,
            spending = spending.await().getOrNull(),
            budgets = budgets.await().getOrNull(),
            overMonths = over.awaitAll().filterNotNull().toSet(),
            needsReview = summary.await().getOrNull()?.needsReviewCount ?: 0,
            failed = spending.await().isFailure || budgets.await().isFailure,
        )
    }

    private companion object {
        const val STOP_TIMEOUT = 5_000L

        /** How many months the Overview lets you pick, the current one included. */
        const val MONTHS = 6
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

/**
 * What can still be spent each day to stay within the budget: [left] over the days from [today]
 * to the end of its month, today included, rounded down to whole euros. Null when nothing is
 * left.
 */
internal fun perDay(left: BigDecimal, today: LocalDate): BigDecimal? {
    if (left.signum() <= 0) return null
    val days = today.lengthOfMonth() - today.dayOfMonth + 1
    return left.divide(BigDecimal(days), 0, RoundingMode.DOWN)
}
