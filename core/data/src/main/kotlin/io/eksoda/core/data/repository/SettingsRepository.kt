package io.eksoda.core.data.repository

import kotlinx.coroutines.flow.Flow

/** App preferences shown in Settings (preferences only, never data or secrets). */
interface SettingsRepository {
    /** A notification when a receipt finishes importing while the app is closed. Off by default. */
    val notifyImports: Flow<Boolean>

    suspend fun setNotifyImports(enabled: Boolean)

    /**
     * Hide the app's content from screenshots, screen recording and the recent apps screen
     * (the window is secure). About this phone only: never synced or backed up. Off by default.
     */
    val hideContent: Flow<Boolean>

    suspend fun setHideContent(enabled: Boolean)

    /** The Insights cards the user folded, by key; all are open by default. */
    val foldedInsightsCards: Flow<Set<String>>

    /** Folds the card [key] if it is open, or opens it if it is folded. */
    suspend fun toggleInsightsCardFolded(key: String)
}
