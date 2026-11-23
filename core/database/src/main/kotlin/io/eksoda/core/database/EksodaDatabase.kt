package io.eksoda.core.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import io.eksoda.core.database.dao.AnalyticsDao
import io.eksoda.core.database.dao.BillDao
import io.eksoda.core.database.dao.BudgetDao
import io.eksoda.core.database.dao.CategoryDao
import io.eksoda.core.database.dao.HouseholdDao
import io.eksoda.core.database.dao.ImportJobDao
import io.eksoda.core.database.dao.MerchantDao
import io.eksoda.core.database.dao.ProductDao
import io.eksoda.core.database.dao.ReceiptDao
import io.eksoda.core.database.dao.SyncDao
import io.eksoda.core.database.dao.TombstoneDao
import io.eksoda.core.database.entity.BillEntity
import io.eksoda.core.database.entity.BillMonthEntity
import io.eksoda.core.database.entity.BudgetEntity
import io.eksoda.core.database.entity.CategoryEntity
import io.eksoda.core.database.entity.HouseholdEntity
import io.eksoda.core.database.entity.ImportJobEntity
import io.eksoda.core.database.entity.MerchantEntity
import io.eksoda.core.database.entity.MerchantProductMappingEntity
import io.eksoda.core.database.entity.ProductEntity
import io.eksoda.core.database.entity.ReceiptEntity
import io.eksoda.core.database.entity.ReceiptItemEntity
import io.eksoda.core.database.entity.StoreEntity
import io.eksoda.core.database.entity.SyncEventEntity
import io.eksoda.core.database.entity.SyncPeerEntity
import io.eksoda.core.database.entity.TombstoneEntity

/**
 * The encrypted on-device database: one file per household. It is opened through SQLCipher with
 * the key from KeyManager (core:data); tests use plain Room in memory.
 *
 * Version 4 shipped in the first release (0.1.1), so every schema change from then on needs a
 * migration that keeps the user's data: an [AutoMigration] where Room can derive it from the
 * exported schemas in core/database/schemas, a manual one otherwise, and a test in
 * `MigrationTest`. There is no destructive fallback: a missing migration fails to open.
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
        BudgetEntity::class,
        BillEntity::class,
        BillMonthEntity::class,
        SyncPeerEntity::class,
        SyncEventEntity::class,
    ],
    version = 8,
    exportSchema = true,
    autoMigrations = [
        // 5: the budget table and household.annual_income_minor.
        AutoMigration(from = 4, to = 5),
        // 6: the sync_peer and sync_event tables (household sync).
        AutoMigration(from = 5, to = 6),
        // 7: receipt.source_url nullable (synced receipts have no link), tombstone.natural_key
        // and tombstone.replaced_by.
        AutoMigration(from = 6, to = 7),
        // 8: receipt.origin ('import' for every existing receipt), receipt.category_id and
        // receipt.manual_kind (manual expenses); category.custom_name; budget.month with a
        // nullable amount (budgets per month); the bill and bill_month tables.
        AutoMigration(from = 7, to = 8),
    ],
)
abstract class EksodaDatabase : RoomDatabase() {
    abstract fun householdDao(): HouseholdDao

    abstract fun importJobDao(): ImportJobDao

    abstract fun receiptDao(): ReceiptDao

    abstract fun merchantDao(): MerchantDao

    abstract fun productDao(): ProductDao

    abstract fun tombstoneDao(): TombstoneDao

    abstract fun categoryDao(): CategoryDao

    abstract fun analyticsDao(): AnalyticsDao

    abstract fun budgetDao(): BudgetDao

    abstract fun billDao(): BillDao

    abstract fun syncDao(): SyncDao
}
