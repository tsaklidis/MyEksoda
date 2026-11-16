package io.eksoda.feature.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.eksoda.core.designsystem.component.SectionCard
import io.eksoda.core.designsystem.theme.EksodaTheme
import io.eksoda.core.designsystem.theme.tabular
import io.eksoda.core.model.Household
import io.eksoda.core.ui.text.AppLanguage

/** Settings, reached from the household button on Home. */
@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    onCategoriesClick: () -> Unit,
    onMerchantsClick: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    val context = LocalContext.current
    val language = remember { AppLanguage.current(context) }
    val permission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> viewModel.onEvent(SettingsEvent.NotifyImportsChanged(granted)) }
    SettingsScreen(
        state = state,
        language = language,
        version = remember { appVersion(context) },
        onBack = onBack,
        onLanguageSelect = { tag -> activity?.let { AppLanguage.set(it, tag) } },
        onNotifyImportsChange = { enabled ->
            if (enabled && needsNotificationPermission(context)) {
                permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                viewModel.onEvent(SettingsEvent.NotifyImportsChanged(enabled))
            }
        },
        onCategoriesClick = onCategoriesClick,
        onMerchantsClick = onMerchantsClick,
    )
}

private fun needsNotificationPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
        PackageManager.PERMISSION_GRANTED

private fun appVersion(context: Context): String = runCatching {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName
}.getOrNull().orEmpty()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    language: String,
    version: String,
    onBack: () -> Unit,
    onLanguageSelect: (String) -> Unit,
    onNotifyImportsChange: (Boolean) -> Unit,
    onCategoriesClick: () -> Unit,
    onMerchantsClick: () -> Unit,
) {
    val colors = EksodaTheme.colors
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SectionCard(title = stringResource(R.string.settings_language)) {
                Column(modifier = Modifier.selectableGroup()) {
                    LANGUAGES.forEachIndexed { index, (tag, label) ->
                        if (index > 0) HorizontalDivider(color = colors.line)
                        LanguageRow(
                            label = stringResource(label),
                            selected = tag == language,
                            onClick = { if (tag != language) onLanguageSelect(tag) },
                        )
                    }
                }
            }
            SectionCard(title = stringResource(R.string.settings_notifications)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.settings_notify_imports),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            stringResource(R.string.settings_notify_imports_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.ink2,
                        )
                    }
                    Switch(checked = state.notifyImports, onCheckedChange = onNotifyImportsChange)
                }
            }
            SectionCard(title = stringResource(R.string.settings_catalog)) {
                LinkRow(stringResource(R.string.settings_categories), onCategoriesClick)
                HorizontalDivider(color = colors.line)
                LinkRow(stringResource(R.string.settings_merchants), onMerchantsClick)
            }
            state.household?.let { household -> HouseholdCard(household) }
            SectionCard(title = stringResource(R.string.settings_about)) {
                ValueRow(stringResource(R.string.settings_version), version, number = true)
                Text(
                    text = stringResource(R.string.settings_privacy),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.ink2,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                )
            }
        }
    }
}

/** The languages offered, as (tag, label); `locales_config.xml` lists the same. */
private val LANGUAGES = listOf(
    AppLanguage.GREEK to R.string.settings_language_greek,
    AppLanguage.ENGLISH to R.string.settings_language_english,
    AppLanguage.SYSTEM to R.string.settings_language_system,
)

@Composable
private fun LanguageRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        RadioButton(selected = selected, onClick = null)
    }
}

@Composable
private fun LinkRow(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = EksodaTheme.colors.ink2,
        )
    }
}

@Composable
private fun ValueRow(label: String, value: String, number: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium.let { if (number) it.tabular() else it },
            color = EksodaTheme.colors.ink2,
        )
    }
}

@Composable
private fun HouseholdCard(household: Household) {
    val colors = EksodaTheme.colors
    SectionCard(title = stringResource(R.string.settings_household)) {
        ValueRow(stringResource(R.string.settings_household_name), household.name)
        HorizontalDivider(color = colors.line)
        ValueRow(stringResource(R.string.settings_currency), household.defaultCurrency)
        HorizontalDivider(color = colors.line)
        ValueRow(stringResource(R.string.settings_timezone), household.timezone)
    }
}

@Preview
@Composable
private fun SettingsScreenPreview() {
    EksodaTheme {
        SettingsScreen(
            state = SettingsUiState(
                household = Household("1", "Σπίτι", "EUR", "Europe/Athens"),
                notifyImports = true,
            ),
            language = AppLanguage.GREEK,
            version = "0.1.0",
            onBack = {},
            onLanguageSelect = {},
            onNotifyImportsChange = {},
            onCategoriesClick = {},
            onMerchantsClick = {},
        )
    }
}
