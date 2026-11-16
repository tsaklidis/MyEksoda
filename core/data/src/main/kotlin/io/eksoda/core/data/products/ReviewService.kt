package io.eksoda.core.data.products

import io.eksoda.core.data.imports.ProductMatcher
import io.eksoda.core.data.imports.unitOf
import io.eksoda.core.data.imports.withPrices
import io.eksoda.core.data.util.Fixed
import io.eksoda.core.database.dao.ProductDao
import io.eksoda.core.database.dao.TombstoneDao
import io.eksoda.core.database.entity.MerchantProductMappingEntity
import io.eksoda.core.database.entity.ProductEntity
import io.eksoda.core.database.entity.ReceiptItemEntity
import io.eksoda.core.database.entity.TombstoneEntity
import io.eksoda.core.model.CatalogError
import io.eksoda.core.model.CatalogException
import io.eksoda.core.model.ProductDraft
import io.eksoda.engine.merchants.FieldSize
import io.eksoda.engine.products.mappingKey
import io.eksoda.engine.text.normalizeText
import java.time.Clock
import java.util.UUID
import javax.inject.Inject

/**
 * Corrections people make to matching: the server's `products/review.py`. Each function runs
 * inside the caller's WriteCoordinator transaction and recomputes the affected lines' prices.
 * Corrections never change source amounts or original descriptions.
 */
class ReviewService @Inject constructor(
    private val products: ProductDao,
    private val tombstones: TombstoneDao,
    private val matcher: ProductMatcher,
    private val clock: Clock,
) {
    /**
     * `link_item`: link a product line to [product] and remember it for the merchant's item, so
     * the next import of that item matches automatically.
     */
    suspend fun linkItem(item: ReceiptItemEntity, product: ProductEntity) {
        requireProductLine(item)
        products.updateItems(
            listOf(
                item.copy(productId = product.id, matchStatus = MATCHED, updatedAt = clock.millis())
                    .withPrices(product),
            ),
        )
        remember(item, product)
        deleteIfOrphan(item.productId, keep = product.id)
    }

    /** `create_product_for_item`: a new product from [draft], then [linkItem]. */
    suspend fun createProductForItem(item: ReceiptItemEntity, draft: ProductDraft) {
        requireProductLine(item)
        linkItem(item, createProduct(draft))
    }

    /**
     * `update_item`: reclassify a line and/or change its note.
     * Back to a product: matched again.
     */
    suspend fun updateItem(item: ReceiptItemEntity, lineType: String?, note: String?) {
        var updated = item.copy(userNote = note ?: item.userNote, updatedAt = clock.millis())
        if (lineType != null && lineType != item.lineType) {
            val product = if (lineType == PRODUCT) match(item) else null
            updated = updated.copy(
                lineType = lineType,
                productId = product?.id,
                matchStatus = when {
                    lineType != PRODUCT -> NOT_A_PRODUCT
                    product != null -> MATCHED
                    else -> NEEDS_REVIEW
                },
            ).withPrices(product)
        }
        products.updateItems(listOf(updated))
        deleteIfOrphan(item.productId, keep = updated.productId)
    }

    suspend fun createProduct(draft: ProductDraft): ProductEntity {
        val now = clock.millis()
        val product = ProductEntity(
            id = UUID.randomUUID().toString(),
            name = "",
            normalizedName = "",
            createdAt = now,
            updatedAt = now,
        ).with(draft)
        products.insert(product)
        return product
    }

    /** `update_product`: a package size change recomputes the product's line prices. */
    suspend fun updateProduct(product: ProductEntity, draft: ProductDraft) {
        val updated = product.with(draft).copy(updatedAt = clock.millis())
        products.update(updated)
        val packageChanged = updated.packageQuantityE4 != product.packageQuantityE4 ||
            updated.packageUnit != product.packageUnit
        if (packageChanged) refreshPrices(products.itemsOf(listOf(product.id)), updated)
    }

    /**
     * `merge_products`: the sources' mappings and lines move to [target], the moved lines get new
     * prices, and the sources are deleted.
     */
    suspend fun mergeProducts(target: ProductEntity, sourceIds: Set<String>) {
        if (sourceIds.size > MAX_MERGE_SOURCES) fail(CatalogError.TOO_MANY_PRODUCTS)
        val ids = sourceIds.toList()
        if (ids.isEmpty() || target.id in sourceIds || products.getAll(ids).size != ids.size) {
            fail(CatalogError.NOT_FOUND)
        }
        val now = clock.millis()
        products.moveMappings(ids, target.id, now)
        val moved = products.itemsOf(ids).map { it.copy(productId = target.id, updatedAt = now) }
        refreshPrices(moved, target)
        products.delete(ids)
        ids.forEach { tombstones.insert(TombstoneEntity(PRODUCT, it, now)) }
    }

    private suspend fun match(item: ReceiptItemEntity): ProductEntity? {
        val merchantId = checkNotNull(products.merchantOf(item.receiptId))
        return matcher.match(
            merchantId,
            item.description,
            item.normalizedDescription,
            item.merchantProductCode,
            unitOf(item.unit),
        )
    }

    private suspend fun refreshPrices(items: List<ReceiptItemEntity>, product: ProductEntity) {
        if (items.isNotEmpty()) products.updateItems(items.map { it.withPrices(product) })
    }

    /** `_remember`: point the mapping of the line's key (code, else description) at [product]. */
    private suspend fun remember(item: ReceiptItemEntity, product: ProductEntity) {
        val merchantId = checkNotNull(products.merchantOf(item.receiptId))
        val key = mappingKey(item.merchantProductCode, item.normalizedDescription)
        val now = clock.millis()
        val mapping = products.findMapping(merchantId, key)
        if (mapping != null) {
            products.updateMapping(
                mapping.copy(
                    productId = product.id,
                    lastSeenDescription = item.description,
                    normalizedDescription = item.normalizedDescription,
                    updatedAt = now,
                ),
            )
        } else {
            products.insertMapping(
                MerchantProductMappingEntity(
                    id = UUID.randomUUID().toString(),
                    merchantId = merchantId,
                    merchantProductCode = item.merchantProductCode,
                    normalizedDescription = item.normalizedDescription,
                    lastSeenDescription = item.description,
                    productId = product.id,
                    mappingKey = key,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }
    }

    /** `_delete_if_orphan`: a product left with no lines and no mappings is deleted. */
    private suspend fun deleteIfOrphan(id: String?, keep: String?) {
        if (id == null || id == keep || !products.isOrphan(id)) return
        products.delete(listOf(id))
        tombstones.insert(TombstoneEntity(PRODUCT, id, clock.millis()))
    }

    private fun requireProductLine(item: ReceiptItemEntity) {
        if (item.lineType != PRODUCT) fail(CatalogError.NOT_A_PRODUCT_LINE)
    }

    companion object {
        const val MAX_MERGE_SOURCES = 50
        private const val PRODUCT = "product"
        private const val MATCHED = "matched"
        private const val NEEDS_REVIEW = "needs_review"
        private const val NOT_A_PRODUCT = "not_a_product"
    }
}

/** The product with [draft]'s values, checked like the server's `ProductSerializer`. */
internal fun ProductEntity.with(draft: ProductDraft): ProductEntity {
    val name = draft.name.trim()
    if (name.isEmpty()) fail(CatalogError.BLANK_NAME)
    val quantity = draft.packageQuantity
    val positive = quantity == null || quantity.signum() > 0
    if ((quantity == null) != (draft.packageUnit == null) || !positive) {
        fail(CatalogError.PACKAGE_INCOMPLETE)
    }
    return copy(
        name = name.take(FieldSize.PRODUCT_NAME),
        normalizedName = normalizeText(name).take(FieldSize.PRODUCT_NAME),
        brand = draft.brand.trim().take(BRAND_LENGTH),
        categoryId = draft.categoryId,
        packageQuantityE4 = Fixed.toLongOrNull(quantity, Fixed.E4),
        packageUnit = draft.packageUnit,
    )
}

private const val BRAND_LENGTH = 120

internal fun fail(error: CatalogError): Nothing = throw CatalogException(error)
