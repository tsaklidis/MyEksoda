package io.eksoda.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import io.eksoda.core.data.imports.ImportNotifier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(private val preferences: DataStore<Preferences>) :
    SettingsRepository {
    // A file that cannot be read gives safePreferences (settingsDataStore), never an error.
    override val notifyImports: Flow<Boolean> =
        preferences.data.map { it[ImportNotifier.NOTIFY_IMPORTS_KEY] == true }

    override suspend fun setNotifyImports(enabled: Boolean) {
        preferences.edit { it[ImportNotifier.NOTIFY_IMPORTS_KEY] = enabled }
    }

    override val hideContent: Flow<Boolean> = preferences.data.map { it[HIDE_CONTENT_KEY] == true }

    override suspend fun setHideContent(enabled: Boolean) {
        preferences.edit { it[HIDE_CONTENT_KEY] = enabled }
    }

    override val foldedInsightsCards: Flow<Set<String>> =
        preferences.data.map { it[FOLDED_INSIGHTS_CARDS_KEY].orEmpty() }

    override suspend fun toggleInsightsCardFolded(key: String) {
        // Read and written in one edit, so quick taps never undo each other.
        preferences.edit {
            val current = it[FOLDED_INSIGHTS_CARDS_KEY].orEmpty()
            it[FOLDED_INSIGHTS_CARDS_KEY] = if (key in current) current - key else current + key
        }
    }

    // Internal: an unreadable settings file hides the content (safePreferences).
    internal companion object {
        val HIDE_CONTENT_KEY = booleanPreferencesKey("hide_content")
        private val FOLDED_INSIGHTS_CARDS_KEY = stringSetPreferencesKey("folded_insights_cards")
    }
}
