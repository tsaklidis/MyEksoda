// The bucket arithmetic of spending by category, and the running totals of the Home pace chart
// (docs/design/ui-redesign.md §6).
package io.eksoda.engine.analytics

import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

/** Category buckets that are not categories. */
public const val UNCATEGORIZED: String = "uncategorized"
public const val DISCOUNTS: String = "discounts"
public const val FEES: String = "fees_deposits"
public const val OTHER_ADJUSTMENTS: String = "other_adjustments"
public const val UNALLOCATED: String = "unallocated"
public const val CATEGORY: String = "category"

private val LINE_BUCKETS = mapOf(
    "discount" to DISCOUNTS,
    "fee" to FEES,
    "other" to OTHER_ADJUSTMENTS,
)

/** Signed line totals by line type; [categoryId] is the product's, else the merchant default. */
public data class LineTotal(val lineType: String, val categoryId: String?, val total: BigDecimal)

/** A signed total for one merchant default category (null: none). */
public data class DefaultCategoryTotal(val defaultCategoryId: String?, val total: BigDecimal)

/** One bucket: [kind] is [CATEGORY] with a [categoryId], or one of the remainder buckets. */
public data class BucketTotal(val kind: String, val categoryId: String?, val total: BigDecimal)

/**
 * Every line in exactly one bucket, so Σ buckets = spending: product lines by category, else
 * [UNCATEGORIZED]; discount, fee and other lines in their own buckets; what the lines do not
 * cover (receipt totals minus their lines) in the merchant's default category, else
 * [UNALLOCATED]. Zero buckets are dropped. Returns the buckets (unsorted: the caller sorts by
 * total, then name) and the spending.
 */
public fun combineCategoryBuckets(
    lines: List<LineTotal>,
    receiptsByDefault: List<DefaultCategoryTotal>,
    linesByDefault: List<DefaultCategoryTotal>,
): Pair<List<BucketTotal>, BigDecimal> {
    val totals = LinkedHashMap<Pair<String, String?>, BigDecimal>()
    fun add(key: Pair<String, String?>, amount: BigDecimal) {
        totals[key] = (totals[key] ?: BigDecimal.ZERO) + amount
    }
    lines.forEach { row ->
        val key = if (row.lineType == "product") {
            if (row.categoryId != null) CATEGORY to row.categoryId else UNCATEGORIZED to null
        } else {
            (LINE_BUCKETS[row.lineType] ?: OTHER_ADJUSTMENTS) to null
        }
        add(key, row.total)
    }
    val linesPerDefault = linesByDefault.associate { it.defaultCategoryId to it.total }
    var spending = BigDecimal.ZERO
    receiptsByDefault.forEach { row ->
        spending += row.total
        val remainder = row.total - (linesPerDefault[row.defaultCategoryId] ?: BigDecimal.ZERO)
        if (remainder.signum() != 0) {
            val key = row.defaultCategoryId?.let { CATEGORY to it } ?: (UNALLOCATED to null)
            add(key, remainder)
        }
    }
    val buckets = totals.filterValues { it.signum() != 0 }
        .map { (key, total) -> BucketTotal(key.first, key.second, total) }
    return buckets to spending
}

/**
 * Running totals by day of [month] from [daily] totals: one value per day, null after
 * [through] (the days still to come). With [through] null, every day of the month is filled.
 */
public fun runningTotals(
    daily: Map<LocalDate, BigDecimal>,
    month: YearMonth,
    through: LocalDate? = null,
): List<BigDecimal?> {
    var sum = BigDecimal.ZERO
    return (1..month.lengthOfMonth()).map { day ->
        val date = month.atDay(day)
        if (through != null && date.isAfter(through)) {
            null
        } else {
            sum += daily[date] ?: BigDecimal.ZERO
            sum
        }
    }
}

/** The running total on [dayOfMonth], capped at the month's last day (31 Oct for 31 Nov). */
public fun totalBySameDay(running: List<BigDecimal?>, dayOfMonth: Int): BigDecimal {
    if (running.isEmpty()) return BigDecimal.ZERO
    val index = (dayOfMonth.coerceAtMost(running.size) - 1).coerceAtLeast(0)
    return running[index] ?: BigDecimal.ZERO
}
