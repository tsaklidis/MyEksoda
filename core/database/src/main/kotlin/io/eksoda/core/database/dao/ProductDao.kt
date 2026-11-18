package io.eksoda.core.database.dao

import androidx.paging.PagingSource
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import io.eksoda.core.database.entity.CategoryEntity
import io.eksoda.core.database.entity.MerchantProductMappingEntity
import io.eksoda.core.database.entity.ProductEntity
import io.eksoda.core.database.entity.ReceiptItemEntity
import kotlinx.coroutines.flow.Flow

private const val PRODUCT_ROW = """SELECT product.*, category.name AS category_name,
        category.slug AS category_slug, category.is_system AS category_is_system,
        (SELECT COUNT(*) FROM receipt_item i WHERE i.product_id = product.id) AS line_count
    FROM product LEFT JOIN category ON category.id = product.category_id"""

/** The product list's filters: name substring, category, bought at a merchant. */
private const val PRODUCT_LIST_WHERE = """WHERE (:key = '' OR instr(product.normalized_name, :key) > 0)
    AND (:categoryId IS NULL OR product.category_id = :categoryId)
    AND (:merchantId IS NULL OR EXISTS (SELECT 1 FROM merchant_product_mapping m
        WHERE m.product_id = product.id AND m.merchant_id = :merchantId))"""

/** A product with its category and the number of receipt lines linked to it. */
data class ProductRow(
    @Embedded val product: ProductEntity,
    @ColumnInfo(name = "category_name") val categoryName: String?,
    @ColumnInfo(name = "category_slug") val categorySlug: String?,
    @ColumnInfo(name = "category_is_system") val categoryIsSystem: Boolean?,
    @ColumnInfo(name = "line_count") val lineCount: Int,
)

/** One `PRICE_HISTORY` line, with its receipt. */
data class PriceRow(
    val id: String,
    @ColumnInfo(name = "receipt_id") val receiptId: String,
    @ColumnInfo(name = "issued_at") val issuedAt: Long,
    @ColumnInfo(name = "merchant_id") val merchantId: String,
    @ColumnInfo(name = "merchant_name") val merchantName: String,
    @ColumnInfo(name = "store_id") val storeId: String?,
    @ColumnInfo(name = "store_name") val storeName: String?,
    @ColumnInfo(name = "store_street") val storeStreet: String?,
    @ColumnInfo(name = "store_city") val storeCity: String?,
    val description: String,
    @ColumnInfo(name = "quantity_e4") val quantityE4: Long?,
    val unit: String?,
    @ColumnInfo(name = "gross_amount_minor") val grossAmountMinor: Long,
    val currency: String,
    @ColumnInfo(name = "normalized_quantity_e6") val normalizedQuantityE6: Long?,
    @ColumnInfo(name = "normalized_unit") val normalizedUnit: String?,
    @ColumnInfo(name = "normalized_unit_price_e4") val normalizedUnitPriceE4: Long?,
)

/** A product without a category, with the default category of the merchant it came from. */
data class UncategorizedRow(
    val id: String,
    val name: String,
    @ColumnInfo(name = "merchant_category_id") val merchantCategoryId: String?,
)

/** A merchant's id and default category: what matching a line needs from it. */
data class MerchantRef(
    val id: String,
    @ColumnInfo(name = "default_category_id") val defaultCategoryId: String?,
)

/** A line waiting for review, with its receipt. */
data class ReviewRow(
    @Embedded val item: ReceiptItemEntity,
    @ColumnInfo(name = "issued_at") val issuedAt: Long,
    val currency: String,
    @ColumnInfo(name = "merchant_id") val merchantId: String,
    @ColumnInfo(name = "merchant_name") val merchantName: String,
)

@Dao
interface ProductDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCategories(categories: List<CategoryEntity>)

    @Insert
    suspend fun insert(product: ProductEntity)

    @Update
    suspend fun update(product: ProductEntity)

    @Query("SELECT * FROM product WHERE id = :id")
    suspend fun get(id: String): ProductEntity?

    @Query("SELECT * FROM product WHERE id IN (:ids)")
    suspend fun getAll(ids: List<String>): List<ProductEntity>

    /** Products without a category, with their first merchant's default category. */
    @Query(
        """SELECT product.id AS id, product.name AS name,
            (SELECT merchant.default_category_id FROM merchant_product_mapping m
             JOIN merchant ON merchant.id = m.merchant_id
             WHERE m.product_id = product.id ORDER BY m.created_at LIMIT 1) AS merchant_category_id
           FROM product WHERE product.category_id IS NULL""",
    )
    suspend fun uncategorized(): List<UncategorizedRow>

    /** Sets the category only if the product still has none. */
    @Query(
        """UPDATE product SET category_id = :categoryId, updated_at = :now
           WHERE id = :id AND category_id IS NULL""",
    )
    suspend fun setCategoryIfNone(id: String, categoryId: String, now: Long): Int

    @Query("DELETE FROM product WHERE id IN (:ids)")
    suspend fun delete(ids: List<String>): Int

    /** No lines and no mappings left, e.g. after a wrong automatic match was corrected. */
    @Query(
        """SELECT NOT EXISTS (SELECT 1 FROM receipt_item WHERE product_id = :id)
           AND NOT EXISTS (SELECT 1 FROM merchant_product_mapping WHERE product_id = :id)""",
    )
    suspend fun isOrphan(id: String): Boolean

    // --- list and detail --------------------------------------------------------------------

    /**
     * The product list sorted by name: [key] is a normalized substring of the name (empty for
     * all), [merchantId] keeps products bought at that merchant (through its mappings). A plain
     * ORDER BY on `normalized_name` walks its index, so a page reads only the rows it shows
     * (a `CASE` in the ORDER BY would sort the whole table for every page).
     */
    @Query(
        """$PRODUCT_ROW $PRODUCT_LIST_WHERE
           ORDER BY product.normalized_name, product.name, product.id""",
    )
    fun pagingByName(
        key: String,
        categoryId: String?,
        merchantId: String?,
    ): PagingSource<Int, ProductRow>

    /** As [pagingByName], the most bought first. */
    @Query(
        """$PRODUCT_ROW $PRODUCT_LIST_WHERE
           ORDER BY line_count DESC, product.normalized_name, product.name, product.id""",
    )
    fun pagingByLineCount(
        key: String,
        categoryId: String?,
        merchantId: String?,
    ): PagingSource<Int, ProductRow>

    @Query("$PRODUCT_ROW WHERE product.id = :id")
    fun observeRow(id: String): Flow<ProductRow?>

    /** `price_entries`: the product's price observations, newest first. */
    @Query(
        """SELECT i.id, i.receipt_id, r.issued_at, r.merchant_id, m.display_name AS merchant_name,
                r.store_id, s.display_name AS store_name, s.street AS store_street,
                s.city AS store_city, i.description, i.quantity_e4, i.unit,
                i.gross_amount_minor, r.currency, i.normalized_quantity_e6, i.normalized_unit,
                i.normalized_unit_price_e4
           FROM receipt_item i JOIN receipt r ON r.id = i.receipt_id
           JOIN merchant m ON m.id = r.merchant_id LEFT JOIN store s ON s.id = r.store_id
           WHERE i.product_id = :productId AND i.line_type = 'product'
           AND i.normalized_unit_price_e4 IS NOT NULL AND i.quantity_e4 > 0
           AND i.gross_amount_minor > 0 AND r.status = 'active' AND r.document_kind = 'sale'
           ORDER BY r.issued_at DESC, i.line_number DESC""",
    )
    fun observePrices(productId: String): Flow<List<PriceRow>>

    // --- mappings ---------------------------------------------------------------------------

    @Query(
        "SELECT * FROM merchant_product_mapping WHERE merchant_id = :merchantId AND mapping_key = :mappingKey",
    )
    suspend fun findMapping(merchantId: String, mappingKey: String): MerchantProductMappingEntity?

    @Insert
    suspend fun insertMapping(mapping: MerchantProductMappingEntity)

    @Update
    suspend fun updateMapping(mapping: MerchantProductMappingEntity)

    /** Mapping keys are unique per merchant, so moving them never collides. */
    @Query(
        """UPDATE merchant_product_mapping SET product_id = :targetId, updated_at = :now
           WHERE product_id IN (:sourceIds)""",
    )
    suspend fun moveMappings(sourceIds: List<String>, targetId: String, now: Long)

    // --- receipt lines ----------------------------------------------------------------------

    @Query("SELECT * FROM receipt_item WHERE id = :id")
    suspend fun getItem(id: String): ReceiptItemEntity?

    @Query("SELECT * FROM receipt_item WHERE product_id IN (:productIds)")
    suspend fun itemsOf(productIds: List<String>): List<ReceiptItemEntity>

    @Update
    suspend fun updateItems(items: List<ReceiptItemEntity>)

    @Query("SELECT merchant_id FROM receipt WHERE id = :receiptId")
    suspend fun merchantOf(receiptId: String): String?

    /** The receipt's merchant with its default category, for matching a line. */
    @Query(
        """SELECT merchant.id, merchant.default_category_id FROM receipt
           JOIN merchant ON merchant.id = receipt.merchant_id WHERE receipt.id = :receiptId""",
    )
    suspend fun merchantRefOf(receiptId: String): MerchantRef?

    // --- review -----------------------------------------------------------------------------

    /** Lines waiting for a person, newest receipt first. */
    @Query(
        """SELECT i.*, r.issued_at, r.currency, r.merchant_id, m.display_name AS merchant_name
           FROM receipt_item i JOIN receipt r ON r.id = i.receipt_id
           JOIN merchant m ON m.id = r.merchant_id
           WHERE i.match_status = 'needs_review'
           ORDER BY r.issued_at DESC, i.receipt_id, i.line_number""",
    )
    fun pagingReview(): PagingSource<Int, ReviewRow>

    @Query("SELECT COUNT(*) FROM receipt_item WHERE match_status = 'needs_review'")
    fun observeReviewCount(): Flow<Int>
}
