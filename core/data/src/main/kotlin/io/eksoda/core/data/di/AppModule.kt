package io.eksoda.core.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.work.WorkManager
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.eksoda.core.data.repository.AnalyticsRepository
import io.eksoda.core.data.repository.AnalyticsRepositoryImpl
import io.eksoda.core.data.repository.BackupRepository
import io.eksoda.core.data.repository.BackupRepositoryImpl
import io.eksoda.core.data.repository.CatalogRepository
import io.eksoda.core.data.repository.CatalogRepositoryImpl
import io.eksoda.core.data.repository.HouseholdRepository
import io.eksoda.core.data.repository.HouseholdRepositoryImpl
import io.eksoda.core.data.repository.ProductRepository
import io.eksoda.core.data.repository.ProductRepositoryImpl
import io.eksoda.core.data.repository.ReceiptFilterStore
import io.eksoda.core.data.repository.ReceiptFilterStoreImpl
import io.eksoda.core.data.repository.ReceiptOptionsRepository
import io.eksoda.core.data.repository.ReceiptOptionsRepositoryImpl
import io.eksoda.core.data.repository.ReceiptRepository
import io.eksoda.core.data.repository.ReceiptRepositoryImpl
import io.eksoda.core.data.repository.ReviewRepository
import io.eksoda.core.data.repository.ReviewRepositoryImpl
import io.eksoda.core.data.repository.SettingsRepository
import io.eksoda.core.data.repository.SettingsRepositoryImpl
import io.eksoda.engine.Engine
import io.eksoda.engine.fetch.SafeFetcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

/** A scope that lives as long as the process, for app-start work (never GlobalScope). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideEngine(): Engine = Engine(SafeFetcher())

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager =
        WorkManager.getInstance(context)

    /** Preferences only: theme, language, flags. Never data or secrets. */
    @Provides
    @Singleton
    fun providePreferences(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("settings") }

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(
        @DefaultDispatcher dispatcher: CoroutineDispatcher,
    ): CoroutineScope = CoroutineScope(SupervisorJob() + dispatcher)
}

@Module
@InstallIn(SingletonComponent::class)
interface RepositoryModule {
    @Binds
    fun bindReceiptRepository(implementation: ReceiptRepositoryImpl): ReceiptRepository

    @Binds
    fun bindHouseholdRepository(implementation: HouseholdRepositoryImpl): HouseholdRepository

    @Binds
    fun bindReceiptOptionsRepository(
        implementation: ReceiptOptionsRepositoryImpl,
    ): ReceiptOptionsRepository

    @Binds
    fun bindReceiptFilterStore(implementation: ReceiptFilterStoreImpl): ReceiptFilterStore

    @Binds
    fun bindProductRepository(implementation: ProductRepositoryImpl): ProductRepository

    @Binds
    fun bindReviewRepository(implementation: ReviewRepositoryImpl): ReviewRepository

    @Binds
    fun bindCatalogRepository(implementation: CatalogRepositoryImpl): CatalogRepository

    @Binds
    fun bindAnalyticsRepository(implementation: AnalyticsRepositoryImpl): AnalyticsRepository

    @Binds
    fun bindSettingsRepository(implementation: SettingsRepositoryImpl): SettingsRepository

    @Binds
    fun bindBackupRepository(implementation: BackupRepositoryImpl): BackupRepository
}
