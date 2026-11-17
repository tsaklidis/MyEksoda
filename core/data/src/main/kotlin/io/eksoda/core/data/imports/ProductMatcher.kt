package io.eksoda.core.data.imports

import io.eksoda.core.data.util.Fixed
import io.eksoda.core.database.CategorySeed
import io.eksoda.core.database.dao.MerchantDao
import io.eksoda.core.database.dao.ProductDao
import io.eksoda.core.database.entity.MerchantProductMappingEntity
import io.eksoda.core.database.entity.ProductEntity
import io.eksoda.core.database.entity.ReceiptItemEntity
import io.eksoda.engine.classification.LineType
import io.eksoda.engine.merchants.FieldSize
import io.eksoda.engine.mydata.Unit
import io.eksoda.engine.products.PricedLine
import io.eksoda.engine.products.ProductPackage
import io.eksoda.engine.products.isReusedCode
import io.eksoda.engine.products.mappingKey
import io.eksoda.engine.products.newProduct
import io.eksoda.engine.products.normalize
import io.eksoda.engine.products.productCategorySlug
import io.eksoda.engine.text.normalizeText
import java.time.Clock
import java.util.UUID
import javax.inject.Inject

/**
 * `match_item` and `apply_prices` (the server's `products/matching.py` and `prices.py`), shared
 * by imports (Persister) and the review actions (ReviewService). Callers run inside the
 * WriteCoordinator's transaction.
 */
class ProductMatcher @Inject constructor(
    private val products: ProductDao,
    private val merchants: MerchantDao,
    private val clock: Clock,
) {
    /**
     * The mapping's product, a new product and mapping for a new item, or null when the
     * code-reuse guard sends the line to review.
     */
    suspend fun match(
        merchantId: String,
        description: String,
        normalized: String,
        code: String?,
        unit: Unit?,
    ): ProductEntity? {
        val key = mappingKey(code, normalized)
        val mapping = products.findMapping(merchantId, key)
            ?: return createProductAndMapping(merchantId, description, normalized, code, unit)
        if (code != null && isReusedCode(normalized, mapping.normalizedDescription)) return null
        if (mapping.lastSeenDescription != description) {
            // A code mapping follows the item's latest description (the guard compares to it).
            val remembered = if (mapping.merchantProductCode != null) {
                normalized
            } else {
                mapping.normalizedDescription
            }
            products.updateMapping(
                mapping.copy(
                    lastSeenDescription = description,
                    normalizedDescription = remembered,
                    updatedAt = clock.millis(),
                ),
            )
        }
        return products.get(mapping.productId)
    }

    private suspend fun createProductAndMapping(
        merchantId: String,
        description: String,
        normalized: String,
        code: String?,
        unit: Unit?,
    ): ProductEntity {
        val proposal = newProduct(description, code, unit)
        val now = clock.millis()
        val merchantSlug = merchants.get(merchantId)?.defaultCategoryId?.let(CategorySeed::slugOf)
        val product = ProductEntity(
            id = UUID.randomUUID().toString(),
            name = proposal.name,
            normalizedName = normalizeText(proposal.name).take(FieldSize.PRODUCT_NAME),
            packageQuantityE4 = Fixed.toLongOrNull(proposal.packageQuantity, Fixed.E4),
            packageUnit = proposal.packageUnit,
            // Android-only: a system category from the name, or eating out at a café or
            // restaurant (AutoCategory.kt).
            categoryId = productCategorySlug(description, merchantSlug)
                ?.let(CategorySeed::categoryId),
            createdAt = now,
            updatedAt = now,
        )
        products.insert(product)
        products.insertMapping(
            MerchantProductMappingEntity(
                id = UUID.randomUUID().toString(),
                merchantId = merchantId,
                merchantProductCode = code,
                normalizedDescription = normalized,
                lastSeenDescription = description,
                productId = product.id,
                mappingKey = mappingKey(code, normalized),
                createdAt = now,
                updatedAt = now,
            ),
        )
        return product
    }
}

/** The engine's unit for a stored unit code. */
internal fun unitOf(code: String?): Unit? = Unit.entries.firstOrNull { it.code == code }

/** The engine's line type for a stored line type code. */
internal fun lineTypeOf(code: String): LineType = LineType.entries.first { it.code == code }

/**
 * `apply_prices`: the line with its normalized quantity, unit and unit price for [product]
 * (all null for a line that is not a product, or has no product or quantity).
 */
internal fun ReceiptItemEntity.withPrices(product: ProductEntity?): ReceiptItemEntity {
    val price = product?.let {
        normalize(
            PricedLine(
                lineType = lineTypeOf(lineType),
                quantity = Fixed.toDecimalOrNull(quantityE4, Fixed.E4),
                unit = unitOf(unit),
                grossAmount = Fixed.toDecimal(grossAmountMinor, Fixed.MINOR),
            ),
            ProductPackage(Fixed.toDecimalOrNull(it.packageQuantityE4, Fixed.E4), it.packageUnit),
        )
    }
    return copy(
        normalizedQuantityE6 = price?.let { Fixed.toLong(it.quantity, Fixed.E6) },
        normalizedUnit = price?.unit?.code,
        normalizedUnitPriceE4 = price?.unitPrice?.let { Fixed.toLong(it, Fixed.E4) },
    )
}
