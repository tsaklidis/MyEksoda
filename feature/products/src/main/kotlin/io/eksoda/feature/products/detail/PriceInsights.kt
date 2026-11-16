package io.eksoda.feature.products.detail

import io.eksoda.core.model.MerchantPrices
import io.eksoda.core.model.PriceEntry
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.YearMonth

// What Product detail shows beyond the engine's statistics (docs/design/ui-redesign.md §5, §6):
// the lowest and highest purchase with their month and merchant, the average per month for the
// line chart, and how much cheaper the cheapest merchant is. Pure functions of the observed rows.

/** The purchases in one normalized unit and currency that have a unit price, newest first. */
internal fun pricedEntries(
    entries: List<PriceEntry>,
    unit: String,
    currency: String,
): List<PriceEntry> = entries.filter {
    it.normalizedUnit == unit && it.grossAmount.currency == currency &&
        it.normalizedUnitPrice != null
}

/** The cheapest and the dearest purchase; on a tie, the most recent one. */
internal data class PriceExtremes(val lowest: PriceEntry, val highest: PriceEntry)

/** [priced] newest first, as [pricedEntries] returns it. Null when there is nothing. */
internal fun priceExtremes(priced: List<PriceEntry>): PriceExtremes? {
    val lowest = priced.minByOrNull { checkNotNull(it.normalizedUnitPrice) } ?: return null
    val highest = priced.maxByOrNull { checkNotNull(it.normalizedUnitPrice) } ?: return null
    return PriceExtremes(lowest, highest)
}

/** The [count] months up to [end], oldest first, each with its average unit price or null. */
internal data class MonthlyPrices(val months: List<YearMonth>, val averages: List<BigDecimal?>) {
    /** Months with a price. */
    val pricedMonths: Int get() = averages.count { it != null }
}

/**
 * The average unit price per month, weighted by the normalized quantity when every purchase of
 * the month has one (as the engine's statistics are), else the plain mean.
 */
internal fun monthlyAverages(
    priced: List<PriceEntry>,
    end: YearMonth,
    count: Int = MONTHS,
): MonthlyPrices {
    val months = (count - 1 downTo 0).map { end.minusMonths(it.toLong()) }
    val byMonth = priced.groupBy { YearMonth.from(it.issuedAt) }
    return MonthlyPrices(months, months.map { month -> byMonth[month]?.let(::averagePrice) })
}

private fun averagePrice(entries: List<PriceEntry>): BigDecimal {
    val weighted = entries.all { (it.normalizedQuantity?.signum() ?: 0) > 0 }
    var total = BigDecimal.ZERO
    var weight = BigDecimal.ZERO
    entries.forEach { entry ->
        val price = checkNotNull(entry.normalizedUnitPrice)
        val quantity = if (weighted) checkNotNull(entry.normalizedQuantity) else BigDecimal.ONE
        total += price.multiply(quantity)
        weight += quantity
    }
    return total.divide(weight, SCALE, RoundingMode.HALF_UP)
}

/** "Lidl is 0,13 €/l cheaper than AB Vasilopoulos": the first two ranked merchants. */
internal data class CheapestGap(
    val cheapest: MerchantPrices,
    val next: MerchantPrices,
    val difference: BigDecimal,
)

internal fun cheapestGap(merchants: List<MerchantPrices>): CheapestGap? {
    val ranked = merchants.filter { it.rank != null }.sortedBy { it.rank }
    if (ranked.size < 2) return null
    val (cheapest, next) = ranked
    val difference = next.stats.average - cheapest.stats.average
    return CheapestGap(cheapest, next, difference.setScale(2, RoundingMode.HALF_UP))
}

/** The line chart's span: a year. */
internal const val MONTHS = 12
private const val SCALE = 4
