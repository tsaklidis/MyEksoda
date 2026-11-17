package io.eksoda.core.data.imports

import android.content.Context
import androidx.core.content.pm.PackageInfoCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import io.eksoda.core.data.db.WriteCoordinator
import io.eksoda.core.database.dao.ImportJobDao
import io.eksoda.engine.ErrorCode
import kotlinx.coroutines.flow.first
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/** The app's versionCode, for the after-update retry. */
@Singleton
class AppVersion @Inject constructor(@ApplicationContext context: Context) {
    val code: Long = PackageInfoCompat.getLongVersionCode(
        context.packageManager.getPackageInfo(context.packageName, 0),
    )
}

/** Runs once per app start (from the Application). */
class ImportStartup @Inject constructor(
    private val jobs: ImportJobDao,
    private val coordinator: WriteCoordinator,
    private val scheduler: ImportScheduler,
    private val preferences: DataStore<Preferences>,
    private val appVersion: AppVersion,
    private val clock: Clock,
) {
    suspend fun run() {
        retryAfterUpdate()
        // WorkManager keeps queued work across restarts; this also covers work that was lost
        // (e.g. a restored database). Queued runs are kept, so nothing runs twice.
        jobs.pending().forEach { scheduler.enqueue(it.id) }
    }

    /**
     * On the first start of a new versionCode, jobs that failed with a format change, an
     * inconsistent page or an internal error go back to pending once: the new version may have
     * the parser fix. `app_version_at_failure` prevents loops.
     */
    private suspend fun retryAfterUpdate() {
        val lastVersion = preferences.data.first()[LAST_VERSION_KEY]
        if (lastVersion != null && lastVersion >= appVersion.code) return
        val candidates = jobs.failedBeforeVersion(AFTER_UPDATE_CODES, appVersion.code)
        val requeued = coordinator.write {
            candidates.filter {
                jobs.requeueFailed(it.id, clock.millis()) ==
                    1
            }
        }
        requeued.forEach { scheduler.enqueue(it.id) }
        preferences.edit { it[LAST_VERSION_KEY] = appVersion.code }
    }

    private companion object {
        val LAST_VERSION_KEY = longPreferencesKey("last_started_version_code")
        val AFTER_UPDATE_CODES = listOf(
            ErrorCode.PROVIDER_FORMAT_CHANGED.code,
            ErrorCode.INCONSISTENT_SOURCE.code,
            ErrorCode.INTERNAL_ERROR.code,
        )
    }
}
