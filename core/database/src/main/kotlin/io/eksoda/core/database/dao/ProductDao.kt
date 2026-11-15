package io.eksoda.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import io.eksoda.core.database.entity.CategoryEntity
import io.eksoda.core.database.entity.MerchantProductMappingEntity
import io.eksoda.core.database.entity.ProductEntity

@Dao
interface ProductDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCategories(categories: List<CategoryEntity>)

    @Insert
    suspend fun insert(product: ProductEntity)

    @Query("SELECT * FROM product WHERE id = :id")
    suspend fun get(id: String): ProductEntity?

    @Query(
        "SELECT * FROM merchant_product_mapping WHERE merchant_id = :merchantId AND mapping_key = :mappingKey",
    )
    suspend fun findMapping(merchantId: String, mappingKey: String): MerchantProductMappingEntity?

    @Insert
    suspend fun insertMapping(mapping: MerchantProductMappingEntity)

    @Update
    suspend fun updateMapping(mapping: MerchantProductMappingEntity)
}
