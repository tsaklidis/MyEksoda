package io.eksoda.core.model

import java.math.BigDecimal
import java.time.OffsetDateTime

/** A category with the number of products in it. */
public data class CategorySummary(val category: Category, val productCount: Int)

/** A merchant as shown in the merchant list and detail. */
public data class MerchantSummary(
    val id: String,
    val name: String,
    val legalName: String,
    val vatNumber: String,
    /** True when the household renamed the merchant; imports keep the name then. */
    val nameSetByUser: Boolean,
    val address: String?,
    val defaultCategory: Category?,
    val receiptCount: Int,
    val lastReceiptAt: OffsetDateTime?,
)

/** Values of a product to create or edit. The package size has both parts or neither. */
public data class ProductDraft(
    val name: String,
    val brand: String = "",
    val categoryId: String? = null,
    val packageQuantity: BigDecimal? = null,
    val packageUnit: String? = null,
)

public enum class ProductOrdering { NAME, MOST_BOUGHT }

public data class ProductFilter(
    val query: String = "",
    val category: Category? = null,
    /** Products bought at this merchant. */
    val merchant: Merchant? = null,
    val ordering: ProductOrdering = ProductOrdering.NAME,
)

/** Why a product, category or line change was refused. */
public enum class CatalogError {
    BLANK_NAME,
    NAME_TAKEN,
    SYSTEM_CATEGORY,
    CATEGORY_IN_USE,
    NOT_A_PRODUCT_LINE,
    PACKAGE_INCOMPLETE,
    TOO_MANY_PRODUCTS,
    NOT_FOUND,
}

public class CatalogException(public val error: CatalogError) : Exception(error.name)
