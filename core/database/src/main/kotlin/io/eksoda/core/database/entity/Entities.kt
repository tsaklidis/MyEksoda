package io.eksoda.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// The tables of docs/android-standalone.md §6.3. Each one names the Django model it comes from;
// read the field rules there. Rules:
// - Primary keys are random UUID strings; times are UTC epoch milliseconds (§5.3).
// - Decimals are fixed-point Longs with the server column's scale, named by suffix: `_minor`
//   (2 places), `_e2`, `_e4`, `_e6` (§6.2). Mappers in core:data convert them.
// - Enums are stored as their server codes ("sale", "needs_review", …).
// - Room has no partial unique indexes: key columns (`store_key`, `mapping_key`) replace them,
//   and SQLite treats NULLs as distinct in unique indexes, like `WHERE x IS NOT NULL`.

/** `products.ProductCategory`. The 19 system categories are seeded on creation. */
@Entity(tableName = "category", indices = [Index(value = ["slug"], unique = true)])
data class CategoryEntity(
    @PrimaryKey val id: String,
    val slug: String,
    val name: String,
    @ColumnInfo(name = "is_system") val isSystem: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

/** `merchants.Merchant`. */
@Entity(
    tableName = "merchant",
    indices = [
        Index(
            value = ["country_code", "vat_number"],
            unique = true,
        ), Index("default_category_id"),
    ],
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["default_category_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
)
data class MerchantEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "country_code") val countryCode: String,
    @ColumnInfo(name = "vat_number") val vatNumber: String,
    @ColumnInfo(name = "legal_name") val legalName: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    /** `normalizeText(display_name)`, for search (§6.5). */
    @ColumnInfo(name = "normalized_display_name") val normalizedDisplayName: String,
    /** Imports never overwrite a name the user set (§6.3). */
    @ColumnInfo(name = "display_name_set_by_user") val displayNameSetByUser: Boolean = false,
    @ColumnInfo(name = "activity_description") val activityDescription: String = "",
    @ColumnInfo(name = "tax_office") val taxOffice: String = "",
    @ColumnInfo(name = "gemi_number") val gemiNumber: String = "",
    val street: String = "",
    @ColumnInfo(name = "street_number") val streetNumber: String = "",
    val city: String = "",
    @ColumnInfo(name = "postal_code") val postalCode: String = "",
    @ColumnInfo(name = "details_as_of") val detailsAsOf: Long?,
    @ColumnInfo(name = "default_category_id") val defaultCategoryId: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

/** `merchants.Store`. `store_key` is `branch:<number>`, else `addr:<normalized address>`, else null. */
@Entity(
    tableName = "store",
    indices = [Index(value = ["merchant_id", "store_key"], unique = true)],
    foreignKeys = [
        ForeignKey(
            entity = MerchantEntity::class,
            parentColumns = ["id"],
            childColumns = ["merchant_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
)
data class StoreEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "merchant_id") val merchantId: String,
    @ColumnInfo(name = "branch_number") val branchNumber: String?,
    @ColumnInfo(name = "display_name") val displayName: String = "",
    val street: String = "",
    val city: String = "",
    @ColumnInfo(name = "postal_code") val postalCode: String = "",
    @ColumnInfo(name = "country_code") val countryCode: String,
    @ColumnInfo(name = "normalized_address") val normalizedAddress: String = "",
    @ColumnInfo(name = "store_key") val storeKey: String?,
    @ColumnInfo(name = "details_as_of") val detailsAsOf: Long?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

/** `products.Product`. Package quantity and unit are both null or both set. */
@Entity(
    tableName = "product",
    indices = [Index("normalized_name"), Index("category_id")],
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
)
data class ProductEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "normalized_name") val normalizedName: String,
    val brand: String = "",
    @ColumnInfo(name = "category_id") val categoryId: String? = null,
    @ColumnInfo(name = "package_quantity_e4") val packageQuantityE4: Long? = null,
    /** g | kg | ml | cl | l | piece */
    @ColumnInfo(name = "package_unit") val packageUnit: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

/** `products.MerchantProductMapping`. `mapping_key` is `code:<code>` or `desc:<normalized>`. */
@Entity(
    tableName = "merchant_product_mapping",
    indices = [Index(value = ["merchant_id", "mapping_key"], unique = true), Index("product_id")],
    foreignKeys = [
        ForeignKey(
            entity = MerchantEntity::class,
            parentColumns = ["id"],
            childColumns = ["merchant_id"],
        ),
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["product_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class MerchantProductMappingEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "merchant_id") val merchantId: String,
    @ColumnInfo(name = "merchant_product_code") val merchantProductCode: String?,
    @ColumnInfo(name = "normalized_description") val normalizedDescription: String,
    @ColumnInfo(name = "last_seen_description") val lastSeenDescription: String,
    @ColumnInfo(name = "product_id") val productId: String,
    @ColumnInfo(name = "mapping_key") val mappingKey: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

/** `receipts.Receipt`. */
@Entity(
    tableName = "receipt",
    indices = [
        Index(value = ["mark"], unique = true),
        Index(value = ["issuer_country", "issuer_vat", "uid"], unique = true),
        Index(value = ["source_key"], unique = true),
        Index("issued_at"),
        Index(value = ["merchant_id", "issued_at"]),
        Index("store_id"),
    ],
    foreignKeys = [
        ForeignKey(
            entity = MerchantEntity::class,
            parentColumns = ["id"],
            childColumns = ["merchant_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = StoreEntity::class,
            parentColumns = ["id"],
            childColumns = ["store_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
)
data class ReceiptEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "merchant_id") val merchantId: String,
    @ColumnInfo(name = "store_id") val storeId: String?,
    @ColumnInfo(name = "provider_code") val providerCode: String,
    @ColumnInfo(name = "parser_version") val parserVersion: String,
    @ColumnInfo(name = "issuer_country") val issuerCountry: String,
    @ColumnInfo(name = "issuer_vat") val issuerVat: String,
    val uid: String?,
    val mark: String?,
    @ColumnInfo(name = "cancellation_mark") val cancellationMark: String?,
    @ColumnInfo(name = "source_key") val sourceKey: String?,
    /** sale | credit_note | other */
    @ColumnInfo(name = "document_kind") val documentKind: String,
    @ColumnInfo(name = "document_type_code") val documentTypeCode: String?,
    @ColumnInfo(name = "document_number") val documentNumber: String,
    @ColumnInfo(name = "issued_at") val issuedAt: Long,
    /** YYYY-MM-DD in the household timezone (§5.3). */
    @ColumnInfo(name = "issued_local_date") val issuedLocalDate: String,
    val currency: String,
    @ColumnInfo(name = "gross_total_minor") val grossTotalMinor: Long,
    @ColumnInfo(name = "net_total_minor") val netTotalMinor: Long?,
    @ColumnInfo(name = "vat_total_minor") val vatTotalMinor: Long?,
    @ColumnInfo(name = "discount_total_minor") val discountTotalMinor: Long?,
    /** card | cash | bank | mixed | other | unknown */
    @ColumnInfo(name = "payment_method") val paymentMethod: String,
    /** active | cancelled */
    val status: String,
    /** JSON list of validation issues. */
    @ColumnInfo(name = "validation_warnings") val validationWarnings: String,
    @ColumnInfo(name = "excluded_from_analytics") val excludedFromAnalytics: Boolean = false,
    @ColumnInfo(name = "user_note") val userNote: String = "",
    /** The credential: encrypted at rest with the database, never logged or shown (§8.1). */
    @ColumnInfo(name = "source_url") val sourceUrl: String,
    @ColumnInfo(name = "source_url_fingerprint") val sourceUrlFingerprint: String,
    /** Canonical JSON without URL, unknown values or card numbers (`persistence.source_data()`). */
    @ColumnInfo(name = "source_data") val sourceData: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

/**
 * `receipts.ReceiptItem`. `product_id` is set exactly when `match_status = matched`;
 * `match_status = not_a_product` exactly when `line_type != product`.
 */
@Entity(
    tableName = "receipt_item",
    indices = [
        Index(value = ["receipt_id", "line_number"], unique = true),
        Index("match_status"),
        Index(value = ["product_id", "receipt_id"]),
    ],
    foreignKeys = [
        ForeignKey(
            entity = ReceiptEntity::class,
            parentColumns = ["id"],
            childColumns = ["receipt_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["product_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
)
data class ReceiptItemEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "receipt_id") val receiptId: String,
    @ColumnInfo(name = "line_number") val lineNumber: Int,
    /** product | discount | fee | other */
    @ColumnInfo(name = "line_type") val lineType: String,
    val description: String,
    @ColumnInfo(name = "normalized_description") val normalizedDescription: String,
    @ColumnInfo(name = "merchant_product_code") val merchantProductCode: String?,
    @ColumnInfo(name = "quantity_e4") val quantityE4: Long?,
    /** piece | kg | l | other */
    val unit: String?,
    @ColumnInfo(name = "unit_price_e4") val unitPriceE4: Long?,
    @ColumnInfo(name = "gross_amount_minor") val grossAmountMinor: Long,
    @ColumnInfo(name = "net_amount_minor") val netAmountMinor: Long?,
    @ColumnInfo(name = "vat_rate_e2") val vatRateE2: Long?,
    @ColumnInfo(name = "vat_amount_minor") val vatAmountMinor: Long?,
    @ColumnInfo(name = "discount_amount_minor") val discountAmountMinor: Long?,
    @ColumnInfo(name = "product_id") val productId: String?,
    /** matched | needs_review | not_a_product */
    @ColumnInfo(name = "match_status") val matchStatus: String,
    @ColumnInfo(name = "normalized_quantity_e6") val normalizedQuantityE6: Long?,
    @ColumnInfo(name = "normalized_unit") val normalizedUnit: String?,
    @ColumnInfo(name = "normalized_unit_price_e4") val normalizedUnitPriceE4: Long?,
    @ColumnInfo(name = "user_note") val userNote: String = "",
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

/** `receipts.ReceiptImportJob`. */
@Entity(
    tableName = "import_job",
    indices = [
        Index(
            value = ["provider_code", "url_identity"],
            unique = true,
        ), Index("receipt_id"), Index("status"),
    ],
    foreignKeys = [
        ForeignKey(
            entity = ReceiptEntity::class,
            parentColumns = ["id"],
            childColumns = ["receipt_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
)
data class ImportJobEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "provider_code") val providerCode: String,
    @ColumnInfo(name = "url_identity") val urlIdentity: String,
    /** The credential, like `receipt.source_url`. */
    @ColumnInfo(name = "submitted_url") val submittedUrl: String,
    @ColumnInfo(name = "url_fingerprint") val urlFingerprint: String,
    @ColumnInfo(name = "source_host") val sourceHost: String,
    @ColumnInfo(name = "issuer_vat") val issuerVat: String?,
    /** pending | completed | failed */
    val status: String,
    /** created | duplicate | receipt_deleted, only when completed */
    val outcome: String?,
    @ColumnInfo(name = "attempt_count") val attemptCount: Int,
    @ColumnInfo(name = "next_attempt_at") val nextAttemptAt: Long?,
    @ColumnInfo(name = "last_error_code") val lastErrorCode: String?,
    @ColumnInfo(name = "last_error_message") val lastErrorMessage: String?,
    @ColumnInfo(name = "receipt_id") val receiptId: String?,
    @ColumnInfo(name = "finished_at") val finishedAt: Long?,
    /** The versionCode a job failed with; the after-update retry runs once per version (§7.3). */
    @ColumnInfo(name = "app_version_at_failure") val appVersionAtFailure: Long?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

/** Deleted rows, for sync later (§6.1, §13). Nothing reads it in v1. */
@Entity(tableName = "tombstone", primaryKeys = ["entity", "id"])
data class TombstoneEntity(
    val entity: String,
    val id: String,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long,
)
