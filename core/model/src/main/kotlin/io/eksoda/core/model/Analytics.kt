package io.eksoda.core.model

import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

// The results of the ported analytics functions (backend/apps/analytics/queries.py, §6.6).
// Amounts are in the household's default currency.

/** `summary()`: spending in a period and the change from the period of equal length before. */
public data class SpendingSummary(
    val currency: String,
    val current: PeriodTotals,
    val previous: PeriodTotals,
    val needsReviewCount: Int,
) {
    val changeAmount: BigDecimal get() = current.spending - previous.spending

    /** Null when the previous period's spending was 0. */
    val changePercent: BigDecimal? get() = percentOf(changeAmount, previous.spending)
}

public data class PeriodTotals(
    val from: LocalDate,
    val to: LocalDate,
    /** Sales minus credit notes. */
    val spending: BigDecimal,
    val receiptCount: Int,
    val salesCount: Int,
    /** The average sale (credit notes left out), or null without sales. */
    val averageSale: BigDecimal?,
    /** The credit notes' total, positive. */
    val creditNotesTotal: BigDecimal,
    /** Receipts in another currency, which the totals leave out. */
    val otherCurrencyReceipts: Int,
)

/** `spending_by_category()`: the buckets add up to [total]. */
public data class CategorySpending(
    val from: LocalDate,
    val to: LocalDate,
    val currency: String,
    val total: BigDecimal,
    val buckets: List<SpendingBucket>,
)

/** Buckets that are not categories (requirements §13). */
public enum class SpendingBucketKind(public val code: String) {
    CATEGORY("category"),
    UNCATEGORIZED("uncategorized"),
    DISCOUNTS("discounts"),
    FEES("fees_deposits"),
    OTHER_ADJUSTMENTS("other_adjustments"),
    UNALLOCATED("unallocated"),
}

public data class SpendingBucket(
    val kind: SpendingBucketKind,
    /** The category, for [SpendingBucketKind.CATEGORY]. */
    val category: Category?,
    val total: BigDecimal,
    /** Percent of spending (0–100, 2 places), or null when there was no spending. */
    val share: BigDecimal?,
)

/** `monthly_trend()`, with empty months filled in. */
public data class MonthlyTrend(val currency: String, val months: List<MonthTotal>)

public data class MonthTotal(val month: YearMonth, val total: BigDecimal, val receipts: Int)

/** `spending_by_merchant()`. */
public data class MerchantSpending(
    val merchantId: String,
    val name: String,
    val total: BigDecimal,
    val receipts: Int,
    val share: BigDecimal?,
)

/** `top_products()`: over the price-history lines of the period. */
public data class TopProduct(
    val productId: String,
    val name: String,
    val receipts: Int,
    val spend: BigDecimal,
)

/**
 * The Home pace chart (docs/design/ui-redesign.md §6): running totals by day of month for this
 * month (up to [today]) and last month (whole), and last month's total by the same day.
 */
public data class MonthPace(
    val currency: String,
    val month: YearMonth,
    val today: LocalDate,
    /** One value per day of [month]: the running total, null after [today]. */
    val current: List<BigDecimal?>,
    /** One value per day of the previous month: its running total. */
    val previous: List<BigDecimal>,
    /** Last month's running total on the same day of the month (capped at its last day). */
    val previousBySameDay: BigDecimal,
) {
    val spentSoFar: BigDecimal get() = current.lastOrNull { it != null } ?: BigDecimal.ZERO
    val previousTotal: BigDecimal get() = previous.lastOrNull() ?: BigDecimal.ZERO

    /** The change from last month by the same day, in percent; null when that was 0. */
    val changePercent: BigDecimal? get() = percentOf(
        spentSoFar - previousBySameDay,
        previousBySameDay,
    )
}

private val HUNDRED = BigDecimal(100)

/** `_percent`: [part] of [whole] in percent, 2 places half up; null when [whole] is 0. */
public fun percentOf(part: BigDecimal, whole: BigDecimal): BigDecimal? = if (whole.signum() == 0) {
    null
} else {
    part.multiply(HUNDRED).divide(whole, 2, java.math.RoundingMode.HALF_UP)
}
