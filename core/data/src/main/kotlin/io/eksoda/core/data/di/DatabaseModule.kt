package io.eksoda.core.data.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.eksoda.core.data.db.KeyManager
import io.eksoda.core.data.db.sqlCipherRawKey
import io.eksoda.core.database.CategorySeed
import io.eksoda.core.database.EksodaDatabase
import io.eksoda.core.database.dao.CategoryDao
import io.eksoda.core.database.dao.HouseholdDao
import io.eksoda.core.database.dao.ImportJobDao
import io.eksoda.core.database.dao.MerchantDao
import io.eksoda.core.database.dao.ProductDao
import io.eksoda.core.database.dao.ReceiptDao
import io.eksoda.core.database.dao.TombstoneDao
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    private const val DATABASE_NAME = "eksoda.db"

    /** The SQLCipher-encrypted database (§8.2). Opening throws if the key cannot be unwrapped. */
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        keyManager: KeyManager,
        clock: Clock,
    ): EksodaDatabase {
        System.loadLibrary("sqlcipher")
        val exists = context.getDatabasePath(DATABASE_NAME).exists()
        // The raw-key form skips SQLCipher's per-connection key stretching (KeyManager.kt).
        val key = sqlCipherRawKey(keyManager.passphrase(databaseExists = exists))
        val factory = SupportOpenHelperFactory(key)
        return Room.databaseBuilder(context, EksodaDatabase::class.java, DATABASE_NAME)
            .openHelperFactory(factory)
            .addCallback(
                object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        CategorySeed.insertStatements(clock.millis()).forEach(db::execSQL)
                    }
                },
            )
            .build()
    }

    @Provides fun householdDao(database: EksodaDatabase): HouseholdDao = database.householdDao()

    @Provides fun importJobDao(database: EksodaDatabase): ImportJobDao = database.importJobDao()

    @Provides fun receiptDao(database: EksodaDatabase): ReceiptDao = database.receiptDao()

    @Provides fun merchantDao(database: EksodaDatabase): MerchantDao = database.merchantDao()

    @Provides fun productDao(database: EksodaDatabase): ProductDao = database.productDao()

    @Provides fun categoryDao(database: EksodaDatabase): CategoryDao = database.categoryDao()

    @Provides fun tombstoneDao(database: EksodaDatabase): TombstoneDao = database.tombstoneDao()
}
