package io.eksoda

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dagger.hilt.android.AndroidEntryPoint
import io.eksoda.core.data.imports.ImportNotifier
import io.eksoda.core.designsystem.theme.EksodaTheme
import io.eksoda.core.ui.text.AppLanguage
import io.eksoda.ui.AppLaunch
import io.eksoda.ui.AppState
import io.eksoda.ui.AppViewModel
import io.eksoda.ui.EksodaApp

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val appViewModel: AppViewModel by viewModels()

    /** The chosen app language before Android 13 (AppLanguage); 13+ applies it itself. */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        // The app is dark in both system modes (docs/design/ui-redesign.md §1): light bar icons.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        // The splash stays until the encrypted database has opened, instead of an empty screen.
        splash.setKeepOnScreenCondition { appViewModel.state.value == AppState.LOADING }
        // After a configuration change the launch was already handled (it lives in the ViewModel).
        if (savedInstanceState == null) handle(intent)
        setContent {
            EksodaTheme {
                EksodaApp(viewModel = appViewModel)
            }
        }
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
