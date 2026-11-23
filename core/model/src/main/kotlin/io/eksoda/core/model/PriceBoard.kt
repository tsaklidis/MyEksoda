package io.eksoda.core.model

import java.math.BigDecimal

// The Prices tab, from the household's own receipts (core:engine `products/PriceBoard.kt`).

/** A shop's average unit price for one product. */
public data class ShopPrice(val merchant: Merchant, val average: BigDecimal)

/** Where one product costs least, against the other shops it was bought at. */
public data class ProductDeal(
    val productId: String,
    val productName: String,
    /** kg | l | piece | other */
    val unit: String,
    val cheapest: ShopPrice,
    /** The other shops, cheapest first. */
    val others: List<ShopPrice>,
    /** What buying it at [cheapest] would have saved on what was bought elsewhere. */
    val saving: BigDecimal,
)

/** What one shop is cheapest for. */
public data class ShopDeals(
    val merchant: Merchant,
    val products: List<ProductDeal>,
    val saving: BigDecimal,
)

/** A product's price then and now. */
public data class ProductTrend(
    val productId: String,
    val productName: String,
    val unit: String,
    val previous: BigDecimal,
    val latest: BigDecimal,
    val changePercent: BigDecimal,
)

/** A shop's typical (median) change over [products] products. */
public data class ShopTrend(
    val merchant: Merchant,
    val changePercent: BigDecimal,
    val products: Int,
)

/** The Prices tab. Amounts are in [currency]; null without any priced purchase. */
public data class PriceBoard(
    val currency: String?,
    val byProduct: List<ProductDeal>,
    val byShop: List<ShopDeals>,
    val productTrends: List<ProductTrend>,
    val shopTrends: List<ShopTrend>,
    val saving: BigDecimal,
    /** How many shops the comparison covers. */
    val shops: Int,
)
