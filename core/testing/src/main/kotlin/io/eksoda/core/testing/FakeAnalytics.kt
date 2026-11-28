package io.eksoda.core.testing

import io.eksoda.core.data.repository.AnalyticsRepository
import io.eksoda.core.data.repository.GoalsRepository
import io.eksoda.core.model.Budget
import io.eksoda.core.model.CategoryDetail
import io.eksoda.core.model.CategorySpending
import io.eksoda.core.model.EPaymentProgress
import io.eksoda.core.model.MerchantSpending
import io.eksoda.core.model.MonthBudgets
import io.eksoda.core.model.MonthPace
import io.eksoda.core.model.MonthlyTrend
import io.eksoda.core.model.Period
import io.eksoda.core.model.SpendingSummary
import io.eksoda.core.model.TopProduct
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onStart
import java.math.BigDecimal
import java.time.LocalDate

/**
 * [AnalyticsRepository] in memory. Each figure fails ([NotFaked]) until the test sets it, so a
 * screen's failure paths are the default; [change] is a change to the data. The periods each
 * figure was asked for are in [periods].
 */
class FakeAnalyticsRepository(var today: LocalDate = LocalDate.parse("2026-11-20")) :
    AnalyticsRepository {
    private val changed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    var summary: Result<SpendingSummary> = notFaked("summary")
    var trend: Result<MonthlyTrend> = notFaked("trend")
    var categories: Result<CategorySpending> = notFaked("categories")
    var categoryDetail: Result<CategoryDetail> = notFaked("category detail")
    var merchants: Result<List<MerchantSpending>> = notFaked("merchants")
    var topProducts: Result<List<TopProduct>> = notFaked("top products")
    var pace: Result<MonthPace> = notFaked("pace")

    /** "<figure> <period>", in the order asked. */
    val periods = mutableListOf<String>()

    /** How many times the figures were loaded (each load asks for the summary or the detail). */
    var loads = 0
        private set

    /** A receipt, line, product or category changed: the screens reload. */
    fun change() {
        changed.tryEmit(Unit)
    }

    override fun changes(): Flow<Unit> = changed.onStart { emit(Unit) }

    override suspend fun today(): LocalDate = today

    override suspend fun getSummary(period: Period): Result<SpendingSummary> {
        loads++
        periods += "summary $period"
        return summary
    }

    override suspend fun getMonthlyTrend(months: Int): Result<MonthlyTrend> = trend

    override suspend fun getSpendingByCategory(period: Period): Result<CategorySpending> {
        periods += "categories $period"
        return categories
    }

    override suspend fun getCategoryDetail(
        categoryId: String?,
        period: Period,
    ): Result<CategoryDetail> {
        loads++
        periods += "detail $categoryId $period"
        return categoryDetail
    }

    override suspend fun getSpendingByMerchant(period: Period): Result<List<MerchantSpending>> =
        merchants

    override suspend fun getTopProducts(
        period: Period,
        byCount: Boolean,
        limit: Int,
    ): Result<List<TopProduct>> = topProducts

    override suspend fun getMonthPace(): Result<MonthPace> = pace
}

/** [GoalsRepository] in memory: budgets as set, the month's progress as the test sets it. */
class FakeGoalsRepository :
    Recorder(),
    GoalsRepository {
    val budgets = MutableStateFlow<List<Budget>>(emptyList())
    var monthBudgets: Result<MonthBudgets> = notFaked("month budgets")
    var ePayments: Result<EPaymentProgress> = notFaked("e-payments")

    /** The category limit by category id; none by default. */
    var limits: Map<String, BigDecimal> = emptyMap()

    /** The spending each [getMonthBudgets] call was given (null: it queries its own). */
    val spendingPassed = mutableListOf<CategorySpending?>()

    override fun getBudgets(): Flow<List<Budget>> = budgets

    override fun categoryBudgetLimit(budgets: List<Budget>, categoryId: String): BigDecimal? =
        limits[categoryId]

    override suspend fun setBudget(categoryId: String?, amount: BigDecimal?): Result<Unit> =
        answer("setBudget($categoryId, ${amount?.toPlainString()})") {}

    override suspend fun getMonthBudgets(spending: CategorySpending?): Result<MonthBudgets> {
        spendingPassed += spending
        return monthBudgets
    }

    override suspend fun getEPayments(): Result<EPaymentProgress> = ePayments
}

private fun <T> notFaked(what: String): Result<T> = Result.failure(NotFaked(what))
