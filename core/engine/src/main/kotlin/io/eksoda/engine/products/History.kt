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
    /** Quantity-weighted: Σ gross ÷ Σ normalized quantity, in cents, half up. */
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

/** Unit prices are compared and shown in cents: household goods, not stocks. */
private const val PRICE_PLACES = 2

/**
 * A unit price as statistics, charts and comparisons see it: rounded to cents, half up. Unit
 * prices are stored with 4 places (a weighed line's price per kg), so two purchases that both
 * read 9,95 €/kg can differ in the third place; rounded they are the same price.
 */
public fun priceInCents(price: BigDecimal): BigDecimal =
    price.setScale(PRICE_PLACES, RoundingMode.HALF_UP)

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
    // Ranked on the unrounded average (the one PriceStats.average is rounded from): two shops
    // that both show 1,23 still have a cheaper one.
    val merchants = chosen.value.groupBy { it.merchantId }
        .map { (merchantId, items) ->
            val prices = MerchantPrices(
                merchantId,
                items.first().merchantName,
                stats(unit, currency, items),
            )
            prices to weightedAverage(items)
        }
        .sortedWith(
            compareBy<Pair<MerchantPrices, BigDecimal>> { it.second }
                .thenBy { it.first.merchantName },
        )
        .map { it.first }

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

/**
 * Σ gross ÷ Σ normalized quantity, unrounded. Each line's unit price is its gross ÷ its
 * normalized quantity ([normalize]), so this is the quantity-weighted average of the unit
 * prices, without their 4-place rounding: what was paid per kg, l or piece over all purchases.
 */
private fun weightedAverage(entries: List<PriceObservation>): BigDecimal {
    val gross = entries.fold(BigDecimal.ZERO) { sum, entry -> sum + entry.grossAmount }
    val quantity = entries.fold(BigDecimal.ZERO) { sum, entry -> sum + entry.normalizedQuantity }
    return gross.pyDivide(quantity)
}

private fun stats(unit: String, currency: String, entries: List<PriceObservation>): PriceStats {
    val prices = entries.map { priceInCents(it.normalizedUnitPrice) } // newest first
    return PriceStats(
        normalizedUnit = unit,
        currency = currency,
        count = entries.size,
        latest = prices.first(),
        previous = prices.getOrNull(1),
        // min and max keep the first of equal values (1.20 before 1.2).
        minimum = prices.minWith(Comparator { a, b -> a.compareTo(b) }),
        maximum = prices.maxWith(Comparator { a, b -> a.compareTo(b) }),
        average = priceInCents(weightedAverage(entries)),
    )
}
