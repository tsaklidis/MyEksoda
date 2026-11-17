package io.eksoda

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.Lazy
import dagger.hilt.android.HiltAndroidApp
import io.eksoda.core.data.di.ApplicationScope
import io.eksoda.core.data.imports.ImportStartup
import io.eksoda.core.data.products.AutoCategoryBackfill
import io.eksoda.core.ui.text.AppLanguage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class EksodaApplication :
    Application(),
    Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory

    /** Lazy: opening the encrypted database must not happen on the main thread at start. */
    @Inject lateinit var importStartup: Lazy<ImportStartup>

    @Inject lateinit var autoCategoryBackfill: Lazy<AutoCategoryBackfill>

    @Inject @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        AppLanguage.initDefault(this)
        // The after-update retry and requeueing pending imports.
        // A database that cannot be opened (its key does not match) must not crash the app at
        // start: the UI shows "data unavailable" instead (AppViewModel).
        applicationScope.launch {
            runCatching { importStartup.get().run() }
            // Products imported before automatic categories get one (AutoCategory.kt).
            runCatching { autoCategoryBackfill.get().run() }
        }
    }
}
