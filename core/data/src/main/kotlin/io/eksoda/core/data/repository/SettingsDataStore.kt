package io.eksoda.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

/**
 * The app's preferences file (preferences only, never data or secrets), shared by every
 * repository that keeps a setting. A corrupt file is replaced with the defaults, once and for
 * every reader and writer: the launch screen reads "Hide app content" from it, so a bad file
 * must not crash the app before any screen shows, or every time a setting is changed.
 */
internal fun settingsDataStore(
    scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
    produceFile: () -> File,
): DataStore<Preferences> = PreferenceDataStoreFactory.create(
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
    scope = scope,
    produceFile = produceFile,
)
