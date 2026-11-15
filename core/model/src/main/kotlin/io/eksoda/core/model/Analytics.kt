package io.eksoda.core.model

import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

// The results of the ported analytics functions (backend/apps/analytics/queries.py, §6.6).
// Amounts are in the household's default currency.

/** `summary()`: spending in a period and the change from the period before. */
public data class SpendingSummary(
    val currency: String,
    val current: PeriodTotals,
    val previousSpending: BigDecimal,
    val changeAmount: BigDecimal,
    /** Null when the previous period's spending was 0. */
    val changePercent: BigDecimal?,
    val needsReviewCount: Int,
)

public data class PeriodTotals(
    val from: LocalDate,
    val to: LocalDate,
    val spending: BigDecimal,
    val receiptCount: Int,
    val salesCount: Int,
    val averageSale: BigDecimal?,
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

public data class SpendingBucket(
    /** The bucket key: a category slug, or a remainder bucket. */
    val bucket: String,
    val categoryId: String?,
    val name: String,
    val total: BigDecimal,
    /** Percent of spending (0–100), or null when there was no spending. */
    val share: BigDecimal?,
)

/** `monthly_trend()`, with empty months filled in. */
public data class MonthlyTrend(
    val currency: String,
    val months: List<MonthTotal>,
)

public data class MonthTotal(
    val month: YearMonth,
    val total: BigDecimal,
    val receipts: Int,
)
