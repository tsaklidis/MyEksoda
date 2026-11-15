package io.eksoda.core.database.dao

import androidx.paging.PagingSource
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import io.eksoda.core.database.entity.ReceiptEntity
import io.eksoda.core.database.entity.ReceiptItemEntity
import kotlinx.coroutines.flow.Flow

/** A receipt with the names the lists show. */
data class ReceiptRow(
    @Embedded val receipt: ReceiptEntity,
    @ColumnInfo(name = "merchant_name") val merchantName: String,
    @ColumnInfo(name = "store_name") val storeName: String?,
    @ColumnInfo(name = "store_street") val storeStreet: String?,
    @ColumnInfo(name = "store_city") val storeCity: String?,
)

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
    suspend fun findExisting(
        mark: String?,
        country: String,
        vat: String,
        uid: String?,
        sourceKey: String?,
    ): ReceiptEntity?

    @Query(
        """SELECT receipt.*, merchant.display_name AS merchant_name, store.display_name AS store_name,
           store.street AS store_street, store.city AS store_city
           FROM receipt JOIN merchant ON merchant.id = receipt.merchant_id
           LEFT JOIN store ON store.id = receipt.store_id
           ORDER BY receipt.issued_at DESC, receipt.id""",
    )
    fun pagingRows(): PagingSource<Int, ReceiptRow>

    @Query(
        """SELECT receipt.*, merchant.display_name AS merchant_name, store.display_name AS store_name,
           store.street AS store_street, store.city AS store_city
           FROM receipt JOIN merchant ON merchant.id = receipt.merchant_id
           LEFT JOIN store ON store.id = receipt.store_id WHERE receipt.id = :id""",
    )
    fun observeRow(id: String): Flow<ReceiptRow?>

    @Query("SELECT * FROM receipt_item WHERE receipt_id = :receiptId ORDER BY line_number")
    fun observeItems(receiptId: String): Flow<List<ReceiptItemEntity>>

    @Query("DELETE FROM receipt WHERE id = :id")
    suspend fun delete(id: String): Int
}
