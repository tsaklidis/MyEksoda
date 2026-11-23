package io.eksoda.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.preferencesOf
import io.eksoda.core.data.products.AutoCategoryBackfill
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.retryWhen
import java.io.File
import java.io.IOException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * The app's preferences file (preferences only, never data or secrets), shared by every
 * repository and startup task that keeps a value in it. What cannot be read never crashes the
 * app and never weakens privacy; it falls back to [safePreferences]:
 * - a corrupt file is replaced with them (what it held is lost either way);
 * - a read that fails is answered with them and retried, so every reader keeps receiving
 *   updates once the file reads again. Writes still fail; their callers handle that.
 */
internal fun settingsDataStore(
    scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
    produceFile: () -> File,
): DataStore<Preferences> = FailSafePreferences(
    PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler { safePreferences() },
        scope = scope,
        produceFile = produceFile,
    ),
)

/**
 * The values used when the stored ones cannot be read. Different from a new install where a
 * default would do harm:
 * - "Hide app content" is on (fail closed), so content the user asked to hide is never shown
 *   because the file broke; they can turn it off in Settings.
 * - The automatic category backfill counts as done, so categories the user removed are not put
 *   back.
 *
 * Everything else takes its default: household sync is off (no network until the user turns it
 * back on), import notifications are off, and the after-update import retry runs again, which
 * is harmless (`app_version_at_failure` keeps it to once per job and version).
 */
internal fun safePreferences(): Preferences = preferencesOf(
    SettingsRepositoryImpl.HIDE_CONTENT_KEY to true,
    AutoCategoryBackfill.DONE_KEY to AutoCategoryBackfill.VERSION,
)

/** Answers a failed read with [safePreferences] and retries it, with a growing pause. */
internal class FailSafePreferences(private val delegate: DataStore<Preferences>) :
    DataStore<Preferences> {
    override val data: Flow<Preferences> = delegate.data
        .retryWhen { cause, attempt ->
            if (cause !is IOException) return@retryWhen false
            emit(safePreferences())
            delay(retryDelay(attempt))
            true
        }
        .distinctUntilChanged()

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences) =
        delegate.updateData(transform)

    private companion object {
        val FIRST_RETRY = 1.seconds
        val MAX_RETRY = 1.minutes
        const val MAX_DOUBLINGS = 6L

        /** 1 s, 2 s, 4 s … up to a minute. */
        fun retryDelay(attempt: Long): Duration =
            (FIRST_RETRY * (1 shl attempt.coerceAtMost(MAX_DOUBLINGS).toInt()))
                .coerceAtMost(MAX_RETRY)
    }
}
