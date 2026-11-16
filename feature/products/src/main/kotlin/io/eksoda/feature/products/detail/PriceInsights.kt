package io.eksoda.feature.products.detail

import io.eksoda.core.model.MerchantPrices
import io.eksoda.core.model.PriceEntry
import java.math.BigDecimal
import java.math.RoundingMode

// What Product detail shows beyond the engine's statistics (docs/design/ui-redesign.md §5, §6):
// the lowest and highest purchase with their month and merchant, the price changes for the line
// chart, and how much cheaper the cheapest merchant is. Pure functions of the observed rows.

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

/** One point of the price chart: a purchase's unit price and when it was bought. */
internal data class PricePoint(val entry: PriceEntry, val price: BigDecimal)

/**
 * The purchases to draw, oldest first: every change of price, so most distinct prices show. A run
 * of purchases at the same price is one point (its first purchase). When more than [max] points
 * remain, prices that differ from the last kept point by no more than a growing share of the
 * whole range are dropped, until [max] remain; the first, last, lowest and highest always stay.
 */
internal fun pricePoints(priced: List<PriceEntry>, max: Int = MAX_POINTS): List<PricePoint> {
    val oldestFirst = priced.reversed().map { PricePoint(it, checkNotNull(it.normalizedUnitPrice)) }
    val changes = oldestFirst.filterIndexed { index, point ->
        index == 0 || point.price.compareTo(oldestFirst[index - 1].price) != 0
    }
    if (changes.size <= max) return changes
    // The first purchase at the lowest and at the highest price always stays.
    val lowest = changes.minBy { it.price }
    val highest = changes.maxBy { it.price }
    val range = highest.price - lowest.price
    var share = BigDecimal("0.01")
    var points = changes
    // Past a share of 1 only the first, last, lowest and highest are left, so this ends.
    while (points.size > max && share <= BigDecimal(2)) {
        val tolerance = range.multiply(share)
        val kept = mutableListOf(changes.first())
        changes.drop(1).forEachIndexed { index, point ->
            val last = index == changes.size - 2
            val extreme = point === lowest || point === highest
            if (last || extreme || (point.price - kept.last().price).abs() > tolerance) {
                kept += point
            }
        }
        points = kept
        share = share.multiply(BigDecimal(2))
    }
    return points
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

/** The most points the price chart draws before close prices are merged. */
internal const val MAX_POINTS = 24
