// Price statistics and comparison. The PRICE_HISTORY query and its date, merchant and store
// filters are SQL in core:database; these functions take its rows, newest first.
package io.eksoda.engine.products

import io.eksoda.engine.text.pyDivide
import java.math.BigDecimal
import java.math.RoundingMode

/** One price observation: a receipt line in PRICE_HISTORY with its receipt's merchant. */
public data class PriceObservation(
    val merchantId: String,
    val merchantName: String,
    /** kg | l | piece | other */
    val normalizedUnit: String,
    val currency: String,
    val normalizedUnitPrice: BigDecimal,
    val grossAmount: BigDecimal,
    val normalizedQuantity: BigDecimal,
)

public data class PriceStats(
    val normalizedUnit: String,
    val currency: String,
    val count: Int,
    val latest: BigDecimal,
    val previous: BigDecimal?,
    val minimum: BigDecimal,
    val maximum: BigDecimal,
    /** Quantity-weighted: Σ gross ÷ Σ normalized quantity, 4 places, half up. */
    val average: BigDecimal,
)

public data class MerchantPrices(
    val merchantId: String,
    val merchantName: String,
    val stats: PriceStats,
    /** 1 = cheapest average; only when the comparison is comparable. */
    val rank: Int? = null,
)

public data class AvailableUnit(val normalizedUnit: String, val currency: String, val count: Int)

public data class Comparison(
    val comparable: Boolean,
    /** no_prices | unknown_unit | one_merchant; null when comparable. */
    val reason: String?,
    val normalizedUnit: String?,
    val currency: String?,
    val merchants: List<MerchantPrices> = emptyList(),
    val availableUnits: List<AvailableUnit> = emptyList(),
)

public const val NO_PRICES: String = "no_prices"
public const val UNKNOWN_UNIT: String = "unknown_unit"
public const val ONE_MERCHANT: String = "one_merchant"

private const val PRICE_PLACES = 4

/** `price_stats`: statistics per (normalized unit, currency), for entries newest first. */
public fun priceStats(entries: List<PriceObservation>): List<PriceStats> =
    groups(entries).map { (key, group) -> stats(key.first, key.second, group) }

/**
 * `compare`: per-merchant statistics in one (unit, currency) group: the requested unit, else
 * the group with the most entries (ties: the greater (unit, currency)).
 */
public fun compare(entries: List<PriceObservation>, normalizedUnit: String? = null): Comparison {
    val all = groups(entries)
    val available = all.map { (key, group) -> AvailableUnit(key.first, key.second, group.size) }
    val candidates = if (normalizedUnit != null) {
        all.filterKeys { it.first == normalizedUnit }
    } else {
        all
    }
    val chosen = candidates.entries.maxWithOrNull(
        compareBy<Map.Entry<UnitKey, List<PriceObservation>>> { it.value.size }
            .thenBy { it.key.first }
            .thenBy { it.key.second },
    ) ?: return Comparison(false, NO_PRICES, normalizedUnit, null, availableUnits = available)

    val (unit, currency) = chosen.key
    val merchants = chosen.value.groupBy { it.merchantId }
        .map { (merchantId, items) ->
            MerchantPrices(merchantId, items.first().merchantName, stats(unit, currency, items))
        }
        .sortedWith(compareBy<MerchantPrices> { it.stats.average }.thenBy { it.merchantName })

    val reason = when {
        unit == NormalizedUnit.OTHER.code -> UNKNOWN_UNIT
        merchants.size < 2 -> ONE_MERCHANT
        else -> null
    }
    val ranked = if (reason == null) {
        merchants.mapIndexed { index, row -> row.copy(rank = index + 1) }
    } else {
        merchants
    }
    return Comparison(reason == null, reason, unit, currency, ranked, available)
}

/** Groups in first-seen order. */
private fun groups(entries: List<PriceObservation>): Map<UnitKey, List<PriceObservation>> =
    entries.groupBy { it.normalizedUnit to it.currency }

/** (normalized unit, currency). */
private typealias UnitKey = Pair<String, String>

private fun stats(unit: String, currency: String, entries: List<PriceObservation>): PriceStats {
    val prices = entries.map { it.normalizedUnitPrice } // newest first
    val gross = entries.fold(BigDecimal.ZERO) { sum, entry -> sum + entry.grossAmount }
    val quantity = entries.fold(BigDecimal.ZERO) { sum, entry -> sum + entry.normalizedQuantity }
    return PriceStats(
        normalizedUnit = unit,
        currency = currency,
        count = entries.size,
        latest = prices.first(),
        previous = prices.getOrNull(1),
        // min and max keep the first of equal values (1.20 before 1.2).
        minimum = prices.minWith(Comparator { a, b -> a.compareTo(b) }),
        maximum = prices.maxWith(Comparator { a, b -> a.compareTo(b) }),
        average = gross.pyDivide(quantity).setScale(PRICE_PLACES, RoundingMode.HALF_UP),
    )
}
