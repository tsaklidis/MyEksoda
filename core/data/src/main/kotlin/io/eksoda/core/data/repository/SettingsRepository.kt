package io.eksoda.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import io.eksoda.core.data.imports.ImportNotifier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** App preferences shown in Settings (§3: preferences only, never data or secrets). */
interface SettingsRepository {
    /** A notification when a receipt finishes importing while the app is closed. Off by default. */
    val notifyImports: Flow<Boolean>

    suspend fun setNotifyImports(enabled: Boolean)
}

@Singleton
class SettingsRepositoryImpl @Inject constructor(private val preferences: DataStore<Preferences>) :
    SettingsRepository {
    override val notifyImports: Flow<Boolean> =
        preferences.data.map { it[ImportNotifier.NOTIFY_IMPORTS_KEY] == true }

    override suspend fun setNotifyImports(enabled: Boolean) {
        preferences.edit { it[ImportNotifier.NOTIFY_IMPORTS_KEY] = enabled }
    }
}
