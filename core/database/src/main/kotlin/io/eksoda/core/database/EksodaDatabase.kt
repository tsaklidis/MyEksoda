package io.eksoda.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import io.eksoda.core.database.dao.HouseholdDao
import io.eksoda.core.database.entity.HouseholdEntity

/**
 * The encrypted on-device database: one file per household (docs/android-standalone.md §6). The
 * remaining tables, the category seed and the SQLCipher open helper with its KeyManager come in
 * L3. Schemas are exported to core/database/schemas (§6.4).
 */
@Database(
    entities = [HouseholdEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class EksodaDatabase : RoomDatabase() {
    abstract fun householdDao(): HouseholdDao
}
