package io.eksoda.core.model

import java.math.BigDecimal
import java.time.OffsetDateTime

// Price history and comparison (backend/apps/products/prices.py and history.py).

/** One receipt line of a product, as shown in its price history. */
public data class PriceEntry(
    val itemId: String,
    val receiptId: String,
    val issuedAt: OffsetDateTime,
    val merchant: Merchant,
    val store: Store?,
    val description: String,
    val quantity: BigDecimal?,
    val unit: String?,
    val grossAmount: Money,
    val normalizedQuantity: BigDecimal?,
    val normalizedUnit: String?,
    val normalizedUnitPrice: BigDecimal?,
)

/** Statistics over a product's prices in one normalized unit and currency. */
public data class PriceStats(
    val normalizedUnit: String,
    val currency: String,
    val count: Int,
    val latest: BigDecimal,
    val previous: BigDecimal?,
    val minimum: BigDecimal,
    val maximum: BigDecimal,
    val average: BigDecimal,
)

public data class PriceComparison(
    val comparable: Boolean,
    /** Why the prices cannot be compared, when [comparable] is false. */
    val reason: String?,
    val normalizedUnit: String?,
    val currency: String?,
    val merchants: List<MerchantPrices>,
    val availableUnits: List<AvailableUnit>,
)

public data class MerchantPrices(
    val merchant: Merchant,
    /** 1 = lowest average price; null when not ranked. */
    val rank: Int?,
    val stats: PriceStats,
)

public data class AvailableUnit(
    val normalizedUnit: String,
    val currency: String,
    val count: Int,
)
