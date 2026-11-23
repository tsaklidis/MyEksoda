package io.eksoda.feature.settings

import android.content.Context
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import io.eksoda.core.designsystem.theme.EksodaTheme
import io.eksoda.core.ui.text.AppLanguage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The preference switches as drawn: never flipped from a placeholder. */
@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val events = mutableListOf<SettingsEvent>()

    private fun show(switches: PreferenceSwitches?) {
        compose.setContent {
            EksodaTheme {
                SettingsScreen(
                    state = SettingsUiState(),
                    switches = switches,
                    language = AppLanguage.GREEK,
                    version = "1.0",
                    onBack = {},
                    onLanguageSelect = {},
                    onNotifyImportsChange = { events += SettingsEvent.NotifyImportsChanged(it) },
                    onCategoriesClick = {},
                    onMerchantsClick = {},
                    onBudgetsClick = {},
                    onSyncClick = {},
                    onEvent = { events += it },
                )
            }
        }
    }

    /** A switch row: the whole row is the switch for touch and screen readers. */
    private fun row(label: Int): SemanticsNodeInteraction =
        compose.onNodeWithText(context.getString(label)).performScrollTo()

    @Test
    fun `while the stored settings are read, the switches show off and take no taps`() {
        show(switches = null)
        row(R.string.settings_hide_content).assertIsNotEnabled().assertIsOff().performClick()
        row(R.string.settings_notify_imports).assertIsNotEnabled().assertIsOff().performClick()
        assertThat(events).isEmpty()
    }

    @Test
    fun `once read, each switch shows its stored value, and a tap asks for the other`() {
        show(PreferenceSwitches(notifyImports = false, hideContent = true))
        row(R.string.settings_hide_content).assertIsEnabled().assertIsOn().performClick()
        row(R.string.settings_notify_imports).assertIsEnabled().assertIsOff().performClick()
        assertThat(events).containsExactly(
            SettingsEvent.HideContentChanged(false),
            SettingsEvent.NotifyImportsChanged(true),
        )
    }
}
