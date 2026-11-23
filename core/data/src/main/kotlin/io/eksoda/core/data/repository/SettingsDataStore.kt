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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeoutOrNull
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
 *   updates once the file reads again ([FailSafePreferences]).
 *
 * Writes still fail with an IOException; every caller handles it (a message, or nothing lost).
 * A key whose default would do harm when the file is lost belongs in [safePreferences].
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

/**
 * Answers a failed read with [safePreferences] and reads again after a pause: 1 s, 2 s, 4 s … up
 * to a minute while the reads keep failing, from 1 s again after one that works. A write that
 * goes through ends the pause at once (it read the file), so a switch the user turns shows
 * without waiting.
 */
internal class FailSafePreferences(private val delegate: DataStore<Preferences>) :
    DataStore<Preferences> {
    /** How many writes went through. */
    private val writes = MutableStateFlow(0L)

    override val data: Flow<Preferences> = flow {
        // Each reader keeps its own count of failures in a row.
        var failures = 0
        var writesSeen = writes.value
        val reads = delegate.data
            .onEach {
                failures = 0
                writesSeen = writes.value
            }
            .retryWhen { cause, _ ->
                if (cause !is IOException) return@retryWhen false
                emit(safePreferences())
                withTimeoutOrNull(retryDelay(failures++)) { writes.first { it != writesSeen } }
                writesSeen = writes.value
                true
            }
        emitAll(reads)
    }.distinctUntilChanged()

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences) =
        delegate.updateData(transform).also { writes.update { count -> count + 1 } }

    private companion object {
        val FIRST_RETRY = 1.seconds
        val MAX_RETRY = 1.minutes
        const val MAX_DOUBLINGS = 6

        /** 1 s, 2 s, 4 s … up to a minute (2⁶ s is past it, so the shift never overflows). */
        fun retryDelay(failures: Int): Duration =
            (FIRST_RETRY * (1 shl failures.coerceAtMost(MAX_DOUBLINGS))).coerceAtMost(MAX_RETRY)
    }
}
