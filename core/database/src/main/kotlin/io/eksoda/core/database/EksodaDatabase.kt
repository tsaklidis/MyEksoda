package io.eksoda.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import io.eksoda.core.database.dao.HouseholdDao
import io.eksoda.core.database.dao.ImportJobDao
import io.eksoda.core.database.dao.MerchantDao
import io.eksoda.core.database.dao.ProductDao
import io.eksoda.core.database.dao.ReceiptDao
import io.eksoda.core.database.dao.TombstoneDao
import io.eksoda.core.database.entity.CategoryEntity
import io.eksoda.core.database.entity.HouseholdEntity
import io.eksoda.core.database.entity.ImportJobEntity
import io.eksoda.core.database.entity.MerchantEntity
import io.eksoda.core.database.entity.MerchantProductMappingEntity
import io.eksoda.core.database.entity.ProductEntity
import io.eksoda.core.database.entity.ReceiptEntity
import io.eksoda.core.database.entity.ReceiptItemEntity
import io.eksoda.core.database.entity.StoreEntity
import io.eksoda.core.database.entity.TombstoneEntity

/**
 * The encrypted on-device database: one file per household (docs/android-standalone.md §6). It
 * is opened through SQLCipher with the key from KeyManager (core:data); tests use plain Room in
 * memory (§8.2). Until the first release the schema changes freely; each change updates the
 * exported schema in core/database/schemas (§6.4).
 */
@Database(
    entities = [
        HouseholdEntity::class,
        CategoryEntity::class,
        MerchantEntity::class,
        StoreEntity::class,
        ProductEntity::class,
        MerchantProductMappingEntity::class,
        ReceiptEntity::class,
        ReceiptItemEntity::class,
        ImportJobEntity::class,
        TombstoneEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class EksodaDatabase : RoomDatabase() {
    abstract fun householdDao(): HouseholdDao

    abstract fun importJobDao(): ImportJobDao

    abstract fun receiptDao(): ReceiptDao

    abstract fun merchantDao(): MerchantDao

    abstract fun productDao(): ProductDao

    abstract fun tombstoneDao(): TombstoneDao
}
