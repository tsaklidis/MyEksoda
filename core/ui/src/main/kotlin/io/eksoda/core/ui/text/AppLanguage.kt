package io.eksoda.core.ui.text

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.core.content.edit
import java.util.Locale

/**
 * The app's own language (per-app language, `locales_config.xml`): Greek, English, or the
 * phone's. The app starts in Greek; Settings changes it.
 *
 * Android 13+ keeps the choice itself (LocaleManager, also shown in the system's app settings).
 * Before 13 it is kept in a small preferences file and applied to each activity through
 * [wrap] in `attachBaseContext`.
 */
object AppLanguage {
    /** Follow the phone's language. */
    const val SYSTEM = ""
    const val GREEK = "el"
    const val ENGLISH = "en"

    private const val FILE = "app_language"
    private const val KEY_TAG = "tag"
    private const val KEY_INITIALIZED = "initialized"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** On the very first start only, the app is set to Greek. A later choice is kept. */
    fun initDefault(context: Context) {
        val prefs = prefs(context)
        if (prefs.getBoolean(KEY_INITIALIZED, false)) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val manager = context.getSystemService(LocaleManager::class.java)
            // An app restored or migrated with a language already set keeps it.
            if (manager != null && manager.applicationLocales.isEmpty) {
                manager.applicationLocales = LocaleList.forLanguageTags(GREEK)
            }
        }
        prefs.edit {
            putBoolean(KEY_INITIALIZED, true)
            if (!prefs.contains(KEY_TAG)) putString(KEY_TAG, GREEK)
        }
    }

    /** [SYSTEM], [GREEK] or [ENGLISH]. */
    fun current(context: Context): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val locales = context.getSystemService(LocaleManager::class.java)?.applicationLocales
            if (locales != null) {
                return if (locales.isEmpty) SYSTEM else known(locales[0].language)
            }
        }
        return known(prefs(context).getString(KEY_TAG, GREEK).orEmpty())
    }

    private fun known(language: String): String =
        if (language == GREEK || language == ENGLISH) language else SYSTEM

    /** Switches the app's language; the activity is recreated to show it. */
    fun set(activity: Activity, tag: String) {
        prefs(activity).edit { putString(KEY_TAG, tag) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // The system recreates the activity with the new configuration.
            activity.getSystemService(LocaleManager::class.java)?.applicationLocales =
                if (tag ==
                    SYSTEM
                ) {
                    LocaleList.getEmptyLocaleList()
                } else {
                    LocaleList.forLanguageTags(tag)
                }
        } else {
            activity.recreate()
        }
    }

    /**
     * Before Android 13: [base] with the chosen language, for `attachBaseContext`. Android 13+
     * applies the per-app language itself, so [base] is returned unchanged.
     */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val tag = prefs(base).getString(KEY_TAG, GREEK).orEmpty()
        if (tag == SYSTEM) return base
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val configuration = Configuration(base.resources.configuration)
        configuration.setLocales(LocaleList(locale))
        return base.createConfigurationContext(configuration)
    }
}
