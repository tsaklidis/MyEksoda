package io.eksoda.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import io.eksoda.core.database.entity.MerchantEntity
import io.eksoda.core.database.entity.StoreEntity

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
}
