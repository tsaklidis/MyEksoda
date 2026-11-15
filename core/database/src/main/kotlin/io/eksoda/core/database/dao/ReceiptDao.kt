package io.eksoda.core.database.dao

import androidx.paging.PagingSource
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.RawQuery
import androidx.sqlite.db.SimpleSQLiteQuery
import io.eksoda.core.database.entity.MerchantEntity
import io.eksoda.core.database.entity.ProductEntity
import io.eksoda.core.database.entity.ReceiptEntity
import io.eksoda.core.database.entity.ReceiptItemEntity
import io.eksoda.core.database.entity.StoreEntity
import kotlinx.coroutines.flow.Flow

/** A receipt with what the lists show next to it. */
data class ReceiptRow(
    @Embedded val receipt: ReceiptEntity,
    @ColumnInfo(name = "merchant_name") val merchantName: String,
    @ColumnInfo(name = "store_name") val storeName: String?,
    @ColumnInfo(name = "store_street") val storeStreet: String?,
    @ColumnInfo(name = "store_city") val storeCity: String?,
    @ColumnInfo(name = "item_count") val itemCount: Int,
    @ColumnInfo(name = "needs_review") val needsReview: Boolean,
    @ColumnInfo(name = "has_warnings") val hasWarnings: Boolean,
)

/** A line with its product's name and category slug. */
data class ReceiptItemRow(
    @Embedded val item: ReceiptItemEntity,
    @ColumnInfo(name = "product_name") val productName: String?,
    @ColumnInfo(name = "category_slug") val categorySlug: String?,
)

/** A row for a picker: id and name. */
data class OptionRow(val id: String, val name: String)

@Dao
interface ReceiptDao {
    @Insert
    suspend fun insert(receipt: ReceiptEntity)

    @Insert
    suspend fun insertItems(items: List<ReceiptItemEntity>)

    /** `find_existing`: the receipt with the same MARK, (country, VAT, UID) or source key. */
    @Query(
        """SELECT * FROM receipt WHERE (:mark IS NOT NULL AND mark = :mark)
           OR (:uid IS NOT NULL AND issuer_country = :country AND issuer_vat = :vat AND uid = :uid)
           OR (:sourceKey IS NOT NULL AND source_key = :sourceKey) LIMIT 1""",
    )
    suspend fun findExisting(mark: String?, country: String, vat: String, uid: String?, sourceKey: String?): ReceiptEntity?

    /** The filtered list; core:data builds the query from [ROW_SELECT] (ReceiptQuery). */
    @RawQuery(
        observedEntities = [ReceiptEntity::class, ReceiptItemEntity::class, MerchantEntity::class, StoreEntity::class, ProductEntity::class],
    )
    fun pagingRows(query: SimpleSQLiteQuery): PagingSource<Int, ReceiptRow>

    @RawQuery(observedEntities = [ReceiptEntity::class, ReceiptItemEntity::class, MerchantEntity::class, StoreEntity::class])
    fun observeRow(query: SimpleSQLiteQuery): Flow<ReceiptRow?>

    @Query(
        """SELECT receipt_item.*, product.name AS product_name, category.slug AS category_slug
           FROM receipt_item LEFT JOIN product ON product.id = receipt_item.product_id
           LEFT JOIN category ON category.id = product.category_id
           WHERE receipt_item.receipt_id = :receiptId ORDER BY receipt_item.line_number""",
    )
    fun observeItems(receiptId: String): Flow<List<ReceiptItemRow>>

    @Query("SELECT source_url FROM receipt WHERE id = :id")
    suspend fun sourceUrl(id: String): String?

    @Query("SELECT merchant_id FROM receipt WHERE id = :id")
    suspend fun merchantId(id: String): String?

    @Query("UPDATE receipt SET user_note = :note, updated_at = :now WHERE id = :id")
    suspend fun setNote(id: String, note: String, now: Long): Int

    @Query("UPDATE receipt SET excluded_from_analytics = :excluded, updated_at = :now WHERE id = :id")
    suspend fun setExcluded(id: String, excluded: Boolean, now: Long): Int

    /** A store of the receipt's own merchant only (or none). */
    @Query(
        """UPDATE receipt SET store_id = :storeId, updated_at = :now WHERE id = :id
           AND (:storeId IS NULL OR EXISTS (SELECT 1 FROM store WHERE store.id = :storeId AND store.merchant_id = receipt.merchant_id))""",
    )
    suspend fun setStore(id: String, storeId: String?, now: Long): Int

    @Query("DELETE FROM receipt WHERE id = :id")
    suspend fun delete(id: String): Int

    // --- filter options -------------------------------------------------------------------------

    @Query(
        """SELECT merchant.id AS id, merchant.display_name AS name FROM merchant
           LEFT JOIN receipt ON receipt.merchant_id = merchant.id
           GROUP BY merchant.id ORDER BY COUNT(receipt.id) DESC, merchant.display_name""",
    )
    fun merchantOptions(): Flow<List<OptionRow>>

    @Query(
        """SELECT id, CASE WHEN display_name != '' THEN display_name WHEN street != '' THEN street ELSE city END AS name
           FROM store WHERE merchant_id = :merchantId ORDER BY name""",
    )
    fun storeOptions(merchantId: String): Flow<List<OptionRow>>

    /** System categories are returned by slug; the UI shows their translated names. */
    @Query("SELECT id, slug AS name FROM category ORDER BY slug")
    fun categoryOptions(): Flow<List<OptionRow>>

    @Query("SELECT id, name FROM product WHERE instr(normalized_name, :key) > 0 ORDER BY name LIMIT 30")
    suspend fun searchProducts(key: String): List<OptionRow>

    companion object {
        /** The columns of [ReceiptRow]; filters and ordering are appended. */
        const val ROW_SELECT: String = """SELECT receipt.*, merchant.display_name AS merchant_name,
            store.display_name AS store_name, store.street AS store_street, store.city AS store_city,
            (SELECT COUNT(*) FROM receipt_item i WHERE i.receipt_id = receipt.id) AS item_count,
            EXISTS (SELECT 1 FROM receipt_item i WHERE i.receipt_id = receipt.id AND i.match_status = 'needs_review') AS needs_review,
            (receipt.validation_warnings != '[]') AS has_warnings
            FROM receipt JOIN merchant ON merchant.id = receipt.merchant_id
            LEFT JOIN store ON store.id = receipt.store_id"""
    }
}
