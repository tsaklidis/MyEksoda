package io.eksoda.core.ui.text

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The app's light or dark look (docs/design/ui-redesign.md §1): the phone's setting, or one
 * fixed in Settings. About this phone only: kept in a small preferences file, like
 * [AppLanguage], so the first frame already has it (a DataStore read would flash the other
 * theme). Never synced or backed up.
 */
object AppTheme {
    /** Follow the phone's dark theme setting. */
    const val SYSTEM = ""
    const val LIGHT = "light"
    const val DARK = "dark"

    private const val FILE = "app_theme"
    private const val KEY_MODE = "mode"

    @Volatile
    private var state: MutableStateFlow<String>? = null

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** The choice, read once from the file and then kept in memory. */
    private fun state(context: Context): MutableStateFlow<String> = state ?: synchronized(this) {
        state ?: MutableStateFlow(known(prefs(context).getString(KEY_MODE, SYSTEM)))
            .also { state = it }
    }

    /** [SYSTEM], [LIGHT] or [DARK], as it changes. */
    fun mode(context: Context): StateFlow<String> = state(context).asStateFlow()

    /** [SYSTEM], [LIGHT] or [DARK]. */
    fun current(context: Context): String = state(context).value

    /** Saves [newMode]; screens following [mode] switch at once, without recreating anything. */
    fun set(context: Context, newMode: String) {
        val checked = known(newMode)
        prefs(context).edit { putString(KEY_MODE, checked) }
        state(context).value = checked
    }

    /** Whether to draw dark, given the phone's own dark theme setting. */
    fun isDark(mode: String, systemDark: Boolean): Boolean = when (mode) {
        LIGHT -> false
        DARK -> true
        else -> systemDark
    }

    private fun known(value: String?): String =
        if (value == LIGHT || value == DARK) value else SYSTEM
}
