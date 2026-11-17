package io.eksoda.core.database.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import io.eksoda.core.database.entity.MerchantEntity
import io.eksoda.core.database.entity.StoreEntity
import kotlinx.coroutines.flow.Flow

private const val MERCHANT_ROW = """SELECT merchant.*, category.name AS category_name,
        category.slug AS category_slug, category.is_system AS category_is_system,
        (SELECT COUNT(*) FROM receipt r WHERE r.merchant_id = merchant.id) AS receipt_count,
        (SELECT MAX(r.issued_at) FROM receipt r WHERE r.merchant_id = merchant.id)
        AS last_receipt_at
    FROM merchant LEFT JOIN category ON category.id = merchant.default_category_id"""

/** A merchant with its default category and receipts. */
data class MerchantRow(
    @Embedded val merchant: MerchantEntity,
    @ColumnInfo(name = "category_name") val categoryName: String?,
    @ColumnInfo(name = "category_slug") val categorySlug: String?,
    @ColumnInfo(name = "category_is_system") val categoryIsSystem: Boolean?,
    @ColumnInfo(name = "receipt_count") val receiptCount: Int,
    @ColumnInfo(name = "last_receipt_at") val lastReceiptAt: Long?,
)

@Dao
interface MerchantDao {
    @Query("SELECT * FROM merchant WHERE country_code = :countryCode AND vat_number = :vatNumber")
    suspend fun find(countryCode: String, vatNumber: String): MerchantEntity?

    @Insert
    suspend fun insert(merchant: MerchantEntity)

    @Update
    suspend fun update(merchant: MerchantEntity)

    @Query("SELECT * FROM store WHERE merchant_id = :merchantId AND store_key = :storeKey")
    suspend fun findStore(merchantId: String, storeKey: String): StoreEntity?

    @Insert
    suspend fun insertStore(store: StoreEntity)

    @Update
    suspend fun updateStore(store: StoreEntity)

    @Query("SELECT * FROM merchant WHERE id = :id")
    suspend fun get(id: String): MerchantEntity?

    /** Every merchant, the most frequent first. */
    @Query("$MERCHANT_ROW ORDER BY receipt_count DESC, merchant.normalized_display_name")
    fun observeRows(): Flow<List<MerchantRow>>

    @Query("$MERCHANT_ROW WHERE merchant.id = :id")
    fun observeRow(id: String): Flow<MerchantRow?>

    @Query("SELECT * FROM store WHERE merchant_id = :merchantId ORDER BY display_name, street")
    fun observeStores(merchantId: String): Flow<List<StoreEntity>>

    @Query(
        """UPDATE merchant SET default_category_id = :categoryId, updated_at = :now
           WHERE id = :id""",
    )
    suspend fun setDefaultCategory(id: String, categoryId: String?, now: Long): Int

    @Query("SELECT * FROM merchant WHERE default_category_id IS NULL")
    suspend fun withoutDefaultCategory(): List<MerchantEntity>

    /** Sets the default category only if the merchant still has none. */
    @Query(
        """UPDATE merchant SET default_category_id = :categoryId, updated_at = :now
           WHERE id = :id AND default_category_id IS NULL""",
    )
    suspend fun setDefaultCategoryIfNone(id: String, categoryId: String, now: Long): Int
}
