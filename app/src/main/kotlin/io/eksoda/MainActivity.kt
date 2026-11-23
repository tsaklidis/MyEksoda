package io.eksoda

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.view.Window
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import io.eksoda.core.data.imports.ImportNotifier
import io.eksoda.core.designsystem.theme.EksodaTheme
import io.eksoda.core.ui.text.AppLanguage
import io.eksoda.core.ui.text.AppTheme
import io.eksoda.ui.AppLaunch
import io.eksoda.ui.AppState
import io.eksoda.ui.AppViewModel
import io.eksoda.ui.EksodaApp
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val appViewModel: AppViewModel by viewModels()

    /** The chosen app language before Android 13 (AppLanguage); 13+ applies it itself. */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        // Bar icons follow the theme; setContent sets them again whenever it changes.
        val dark = AppTheme.isDark(AppTheme.current(this), systemDark = isSystemDark())
        applySystemBars(dark)
        super.onCreate(savedInstanceState)
        // The splash stays until the encrypted database has opened, instead of an empty screen,
        // and until "Hide app content" is known, so no content is drawn before the window is
        // secure.
        splash.setKeepOnScreenCondition {
            appViewModel.state.value == AppState.LOADING || appViewModel.hideContent.value == null
        }
        // Settings → "Hide app content": no screenshots, and a blank card in recent apps. A
        // window flag, not UI state, so it is set here rather than from the composition. The
        // main dispatcher is immediate: when the value is already known (after a configuration
        // change) the flag is set before setContent, ahead of the first frame.
        lifecycleScope.launch {
            appViewModel.hideContent.filterNotNull().collect { window.setSecure(it) }
        }
        // After a configuration change the launch was already handled (it lives in the ViewModel).
        if (savedInstanceState == null) handle(intent)
        setContent {
            // Settings → "Theme": the phone's setting, or light or dark (ui-redesign.md §1).
            val themeMode by AppTheme.mode(this).collectAsState()
            val darkTheme = AppTheme.isDark(themeMode, systemDark = isSystemInDarkTheme())
            DisposableEffect(darkTheme) {
                applySystemBars(darkTheme)
                onDispose {}
            }
            EksodaTheme(darkTheme = darkTheme) {
                EksodaApp(viewModel = appViewModel)
            }
        }
    }

    private fun isSystemDark(): Boolean =
        resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES

    /** Transparent bars with icons that read on the theme's page: light icons on dark. */
    private fun applySystemBars(dark: Boolean) {
        val style = if (dark) {
            SystemBarStyle.dark(Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        }
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }

    private fun Window.setSecure(secure: Boolean) {
        val flag = WindowManager.LayoutParams.FLAG_SECURE
        if (secure) addFlags(flag) else clearFlags(flag)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    /**
     * A shared link or a tapped import notification. The shared text is taken out of the intent
     * once read, so the link does not stay in the activity's intent.
     */
    private fun handle(intent: Intent) {
        val launch = when {
            intent.action == Intent.ACTION_SEND && intent.type == "text/plain" ->
                intent.getStringExtra(Intent.EXTRA_TEXT)?.let { AppLaunch.Share(it) }

            else -> intent.getStringExtra(ImportNotifier.EXTRA_RECEIPT_ID)
                ?.let { AppLaunch.OpenReceipt(it) }
        }
        intent.removeExtra(Intent.EXTRA_TEXT)
        intent.removeExtra(ImportNotifier.EXTRA_RECEIPT_ID)
        launch?.let(appViewModel::onLaunch)
    }
}
