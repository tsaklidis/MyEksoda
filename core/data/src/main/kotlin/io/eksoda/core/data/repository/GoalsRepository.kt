package io.eksoda.core.data.repository

import io.eksoda.core.model.CategorySpending
import io.eksoda.core.model.EPaymentProgress
import io.eksoda.core.model.EverydayBudget
import io.eksoda.core.model.MonthBudgets
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.time.YearMonth

/**
 * The household's goals: the everyday budget, a monthly limit per category, and the yearly
 * e-payment target. A budget has a default for every month and may differ in the current month
 * only; past months keep what they had (core:engine `analytics/MonthAmounts.kt`). The rules are
 * in core:engine `analytics/Budgets.kt` and `EPayments.kt`. The annual income the target comes
 * from is set through [HouseholdRepository.setAnnualIncome].
 */
interface GoalsRepository {
    /** The everyday budget in the current month. */
    fun getEveryday(): Flow<EverydayBudget>

    /**
     * Sets the everyday budget of every month from this one on to [amount] (above 0), or removes
     * it when null. Past months keep theirs; a change for this month only stays.
     */
    suspend fun setEveryday(amount: BigDecimal?): Result<Unit>

    /** The everyday budget of the current month only (above 0); null goes back to the default. */
    suspend fun setEverydayThisMonth(amount: BigDecimal?): Result<Unit>

    /** [categoryId]'s limit in the current month; null without one. */
    fun getCategoryLimit(categoryId: String): Flow<BigDecimal?>

    /**
     * Sets [categoryId]'s monthly limit from this month on to [amount] (above 0), or removes it
     * when null. Past months keep theirs.
     */
    suspend fun setCategoryLimit(categoryId: String, amount: BigDecimal?): Result<Unit>

    /**
     * [month]'s budgets (the current month when null) against its spending, so far for the
     * current month. A caller that already has that spending (the month's 1st to today, or to its
     * last day for a past month) passes it to save the query; any other range is ignored.
     */
    suspend fun getMonthBudgets(
        month: YearMonth? = null,
        spending: CategorySpending? = null,
    ): Result<MonthBudgets>

    /** This year's card and bank payments against the target. */
    suspend fun getEPayments(): Result<EPaymentProgress>
}
