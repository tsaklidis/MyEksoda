package io.eksoda.engine.products

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

// The Prices tab (docs/design/ui-redesign.md §5), from the household's own receipts only: where
// each product it buys costs least, what each shop is cheapest for, and how prices move. A
// product is compared in one unit and currency (its normalized unit price: per kg, l or piece),
// and only when it was bought at two shops or more.

/** How far back the comparison looks, in days (today included). */
public const val COMPARE_DAYS: Long = 90

/** The length of each of the two windows a trend compares, in days. */
public const val TREND_DAYS: Long = 30

private const val PRICE_SCALE = 4
private const val MONEY_SCALE = 2
private const val PERCENT_SCALE = 1
private val HUNDRED = BigDecimal("100")

/** One product line: what was paid per unit for how much, where and when. */
public data class PricedPurchase(
    val productId: String,
    val productName: String,
    val merchantId: String,
    val merchantName: String,
    val date: LocalDate,
    /** kg | l | piece | other */
    val unit: String,
    val currency: String,
    val unitPrice: BigDecimal,
    /** In [unit]s; above 0. */
    val quantity: BigDecimal,
)

/** A shop's average unit price for one product (weighted by quantity). */
public data class MerchantPrice(
    val merchantId: String,
    val merchantName: String,
    val average: BigDecimal,
)

/** Where one product costs least, against the other shops it was bought at. */
public data class ProductDeal(
    val productId: String,
    val productName: String,
    val unit: String,
    val cheapest: MerchantPrice,
    /** The other shops, cheapest first. */
    val others: List<MerchantPrice>,
    /** What buying it at [cheapest] would have saved on what was bought elsewhere. */
    val saving: BigDecimal,
)

/** What one shop is cheapest for. */
public data class MarketDeals(
    val merchantId: String,
    val merchantName: String,
    /** Its products, the biggest saving first. */
    val products: List<ProductDeal>,
    val saving: BigDecimal,
)

/** A price then and now: a product's ([unit] set) or a shop's typical change. */
public data class PriceChange(
    val id: String,
    val name: String,
    val unit: String?,
    /** The earlier window's average unit price; null for a shop. */
    val previous: BigDecimal?,
    /** The later window's; null for a shop. */
    val latest: BigDecimal?,
    /** Up is positive, in percent (1 place). */
    val changePercent: BigDecimal,
    /** How many products it covers (1 for a product). */
    val products: Int,
)

public data class PriceBoard(
    /** The currency compared (the one most bought in); null without purchases. */
    val currency: String?,
    /** The products bought at two shops or more, the biggest saving first. */
    val byProduct: List<ProductDeal>,
    /** The shops that are cheapest for something, the most products first. */
    val byMarket: List<MarketDeals>,
    /** Products bought in both trend windows, the biggest change first. */
    val productTrends: List<PriceChange>,
    /** Each shop's median change over its products, the biggest first. */
    val marketTrends: List<PriceChange>,
    /** All of [byProduct]'s savings together. */
    val saving: BigDecimal,
)

/** The Prices tab from [purchases] (any period; only the last 90 days are compared). */
public fun priceBoard(purchases: List<PricedPurchase>, today: LocalDate): PriceBoard {
    val currency = purchases.groupingBy { it.currency }.eachCount().maxByOrNull { it.value }?.key
    val usable = purchases.filter {
        it.currency == currency && it.quantity.signum() > 0 && it.unitPrice.signum() > 0
    }
    val since = today.minusDays(COMPARE_DAYS - 1)
    val deals = usable.filter { it.date in since..today }
        .groupBy { it.productId to it.unit }
        .mapNotNull { (_, lines) -> deal(lines) }
        .sortedWith(compareByDescending<ProductDeal> { it.saving }.thenBy { it.productName })
    val markets = deals.groupBy { it.cheapest.merchantId }
        .map { (merchantId, products) ->
            MarketDeals(
                merchantId = merchantId,
                merchantName = products.first().cheapest.merchantName,
                products = products,
                saving = products.sumOf { it.saving },
            )
        }
        .sortedWith(
            compareByDescending<MarketDeals> { it.products.size }.thenByDescending { it.saving },
        )
    return PriceBoard(
        currency = currency,
        byProduct = deals,
        byMarket = markets,
        productTrends = productTrends(usable, today),
        marketTrends = marketTrends(usable, today),
        saving = deals.sumOf { it.saving }.setScale(MONEY_SCALE, RoundingMode.HALF_UP),
    )
}

/** One product in one unit: a deal when it was bought at two shops or more. */
private fun deal(lines: List<PricedPurchase>): ProductDeal? {
    val prices = lines.groupBy { it.merchantId }
        .map { (merchantId, bought) ->
            MerchantPrice(merchantId, bought.first().merchantName, weightedAverage(bought))
        }
        .sortedWith(compareBy<MerchantPrice> { it.average }.thenBy { it.merchantName })
    if (prices.size < 2) return null
    val cheapest = prices.first()
    val saving = lines.filter { it.merchantId != cheapest.merchantId }
        .sumOf { ((it.unitPrice - cheapest.average) * it.quantity).max(BigDecimal.ZERO) }
        .setScale(MONEY_SCALE, RoundingMode.HALF_UP)
    val first = lines.first()
    return ProductDeal(
        first.productId,
        first.productName,
        first.unit,
        cheapest,
        prices.drop(1),
        saving,
    )
}

/** The two windows of a trend: the last [TREND_DAYS] days, and the [TREND_DAYS] before them. */
private fun windows(today: LocalDate): Pair<ClosedRange<LocalDate>, ClosedRange<LocalDate>> {
    val latestFrom = today.minusDays(TREND_DAYS - 1)
    val previousTo = latestFrom.minusDays(1)
    return previousTo.minusDays(TREND_DAYS - 1)..previousTo to latestFrom..today
}

/** [lines]' average unit price in each window, when both have one. */
private fun change(lines: List<PricedPurchase>, today: LocalDate): Pair<BigDecimal, BigDecimal>? {
    val (previous, latest) = windows(today)
    val before = lines.filter { it.date in previous }
    val after = lines.filter { it.date in latest }
    if (before.isEmpty() || after.isEmpty()) return null
    return weightedAverage(before) to weightedAverage(after)
}

private fun productTrends(purchases: List<PricedPurchase>, today: LocalDate): List<PriceChange> =
    purchases.groupBy { it.productId to it.unit }
        .mapNotNull { (key, lines) ->
            change(lines, today)?.let { (previous, latest) ->
                PriceChange(
                    id = key.first,
                    name = lines.first().productName,
                    unit = key.second,
                    previous = previous,
                    latest = latest,
                    changePercent = percent(previous, latest),
                    products = 1,
                )
            }
        }
        .sortedWith(compareByDescending<PriceChange> { it.changePercent.abs() }.thenBy { it.name })

private fun marketTrends(purchases: List<PricedPurchase>, today: LocalDate): List<PriceChange> =
    purchases.groupBy { it.merchantId }
        .mapNotNull { (merchantId, lines) ->
            val changes = lines.groupBy { it.productId to it.unit }.values
                .mapNotNull { product -> change(product, today) }
                .map { (previous, latest) -> percent(previous, latest) }
            if (changes.isEmpty()) return@mapNotNull null
            PriceChange(
                id = merchantId,
                name = lines.first().merchantName,
                unit = null,
                previous = null,
                latest = null,
                changePercent = median(changes),
                products = changes.size,
            )
        }
        .sortedWith(compareByDescending<PriceChange> { it.changePercent.abs() }.thenBy { it.name })

private fun weightedAverage(lines: List<PricedPurchase>): BigDecimal {
    val quantity = lines.sumOf { it.quantity }
    return lines.sumOf { it.unitPrice * it.quantity }
        .divide(quantity, PRICE_SCALE, RoundingMode.HALF_UP)
}

private fun percent(previous: BigDecimal, latest: BigDecimal): BigDecimal =
    ((latest - previous) * HUNDRED).divide(previous, PERCENT_SCALE, RoundingMode.HALF_UP)

private fun median(values: List<BigDecimal>): BigDecimal {
    val sorted = values.sorted()
    val middle = sorted.size / 2
    return if (sorted.size % 2 == 1) {
        sorted[middle]
    } else {
        (sorted[middle - 1] + sorted[middle]).divide(
            BigDecimal(2),
            PERCENT_SCALE,
            RoundingMode.HALF_UP,
        )
    }
}
