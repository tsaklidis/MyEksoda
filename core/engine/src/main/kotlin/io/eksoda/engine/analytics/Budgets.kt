package io.eksoda.engine.analytics

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

// Monthly budgets: the household may set one for everyday spending and a limit per category. A
// budget is "near" from [BUDGET_NEAR_PERCENT] of it and "over" once spending passes it. Limits are
// set in each category on their own: they need not add up to the everyday budget. Which amount
// holds in which month is in MonthAmounts.kt.

/** The share of a budget, in percent, from which it counts as nearly used up. */
public val BUDGET_NEAR_PERCENT: BigDecimal = BigDecimal("80")

private const val PERCENT_SCALE = 2
private val HUNDRED = BigDecimal("100")

public enum class BudgetLevel { UNDER, NEAR, OVER }

/** How much of a budget this month's spending uses. */
public data class BudgetUse(
    /** The budget minus the spending: negative when over. */
    val left: BigDecimal,
    /** The spending in percent of the budget (2 places); null for a budget of 0. */
    val usedPercent: BigDecimal?,
    val level: BudgetLevel,
)

public fun budgetUse(budget: BigDecimal, spent: BigDecimal): BudgetUse {
    val used = if (budget.signum() == 0) {
        null
    } else {
        (spent * HUNDRED).divide(budget, PERCENT_SCALE, RoundingMode.HALF_UP)
    }
    val level = when {
        spent > budget -> BudgetLevel.OVER
        used != null && used >= BUDGET_NEAR_PERCENT -> BudgetLevel.NEAR
        else -> BudgetLevel.UNDER
    }
    return BudgetUse(left = budget - spent, usedPercent = used, level = level)
}

/** How far into its month [today] is, in percent (2 places): the day's end counts. */
public fun monthElapsedPercent(today: LocalDate): BigDecimal =
    (BigDecimal(today.dayOfMonth) * HUNDRED)
        .divide(BigDecimal(today.lengthOfMonth()), PERCENT_SCALE, RoundingMode.HALF_UP)
