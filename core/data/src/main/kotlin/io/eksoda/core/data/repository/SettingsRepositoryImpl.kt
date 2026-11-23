package io.eksoda.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import io.eksoda.core.data.imports.ImportNotifier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(private val preferences: DataStore<Preferences>) :
    SettingsRepository {
    /**
     * The stored preferences, or the defaults when the file cannot be read (an I/O error, or a
     * corrupt file: CorruptionException is an IOException). The launch screen reads
     * [hideContent], so a bad file must not crash the app before any screen shows.
     */
    private val data: Flow<Preferences> = preferences.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }

    override val notifyImports: Flow<Boolean> =
        data.map { it[ImportNotifier.NOTIFY_IMPORTS_KEY] == true }

    override suspend fun setNotifyImports(enabled: Boolean) {
        preferences.edit { it[ImportNotifier.NOTIFY_IMPORTS_KEY] = enabled }
    }

    override val hideContent: Flow<Boolean> = data.map { it[HIDE_CONTENT_KEY] == true }

    override suspend fun setHideContent(enabled: Boolean) {
        preferences.edit { it[HIDE_CONTENT_KEY] = enabled }
    }

    override val foldedInsightsCards: Flow<Set<String>> =
        data.map { it[FOLDED_INSIGHTS_CARDS_KEY].orEmpty() }

    override suspend fun toggleInsightsCardFolded(key: String) {
        // Read and written in one edit, so quick taps never undo each other.
        preferences.edit {
            val current = it[FOLDED_INSIGHTS_CARDS_KEY].orEmpty()
            it[FOLDED_INSIGHTS_CARDS_KEY] = if (key in current) current - key else current + key
        }
    }

    private companion object {
        val HIDE_CONTENT_KEY = booleanPreferencesKey("hide_content")
        val FOLDED_INSIGHTS_CARDS_KEY = stringSetPreferencesKey("folded_insights_cards")
    }
}
