package io.eksoda.feature.settings

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
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
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.VolunteerActivism
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.eksoda.core.designsystem.component.SectionCard
import io.eksoda.core.designsystem.component.SwitchRow
import io.eksoda.core.designsystem.theme.EksodaTheme
import io.eksoda.core.designsystem.theme.tabular
import io.eksoda.core.model.Household
import io.eksoda.core.model.Money
import io.eksoda.core.ui.component.IncomeDialog
import io.eksoda.core.ui.text.AppLanguage
import io.eksoda.core.ui.text.appLocale
import io.eksoda.core.ui.text.dayTimeLabel
import io.eksoda.core.ui.text.formatMoney
import java.math.BigDecimal
import io.eksoda.core.ui.R as UiR

/** Settings, reached from the household button on Home. */
@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    onCategoriesClick: () -> Unit,
    onMerchantsClick: () -> Unit,
    onBudgetsClick: () -> Unit,
    onSyncClick: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    val context = LocalContext.current
    val language = remember { AppLanguage.current(context) }
    val permission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> viewModel.onEvent(SettingsEvent.NotifyImportsChanged(granted)) }
    // Storage Access Framework: no storage permission, the user picks the file.
    val createBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BACKUP_MIME_TYPE),
    ) { uri -> viewModel.onEvent(SettingsEvent.ExportFileChosen(uri)) }
    val openBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> viewModel.onEvent(SettingsEvent.ImportFileChosen(uri)) }
    val snackbarHostState = remember { SnackbarHostState() }
    val incomeFailed = stringResource(UiR.string.income_save_failed)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                SettingsEffect.IncomeSaveFailed -> snackbarHostState.showSnackbar(incomeFailed)

                // No app may handle the picker on a stripped-down device: then nothing happens.
                is SettingsEffect.ChooseExportFile ->
                    runCatching { createBackup.launch(effect.fileName) }

                SettingsEffect.ChooseImportFile -> runCatching { openBackup.launch(arrayOf("*/*")) }
            }
        }
    }
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
        onBudgetsClick = onBudgetsClick,
        onSyncClick = onSyncClick,
        onEvent = viewModel::onEvent,
        snackbarHostState = snackbarHostState,
    )
}

/** `.eksoda` files have no registered type; octet-stream keeps the name as given. */
private const val BACKUP_MIME_TYPE = "application/octet-stream"

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
    onBudgetsClick: () -> Unit,
    onSyncClick: () -> Unit,
    onEvent: (SettingsEvent) -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val colors = EksodaTheme.colors
    BackupDialogs(dialog = state.backup, onEvent = onEvent)
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
            // Settings opens from the household button on Home, so the household comes first;
            // then what is used most (organizing), preferences, the data, and the app itself.
            state.household?.let { household ->
                HouseholdCard(
                    household = household,
                    sync = state.sync,
                    onIncomeChange = { onEvent(SettingsEvent.AnnualIncomeChanged(it)) },
                    onSyncClick = onSyncClick,
                )
            }
            SectionCard(title = stringResource(R.string.settings_catalog)) {
                LinkRow(
                    label = stringResource(R.string.settings_categories),
                    icon = Icons.Outlined.Category,
                    onClick = onCategoriesClick,
                )
                HorizontalDivider(color = colors.line)
                LinkRow(
                    label = stringResource(R.string.settings_merchants),
                    icon = Icons.Outlined.Storefront,
                    onClick = onMerchantsClick,
                )
                HorizontalDivider(color = colors.line)
                LinkRow(
                    label = stringResource(R.string.settings_budgets),
                    icon = Icons.Outlined.Savings,
                    onClick = onBudgetsClick,
                )
            }
            PreferencesCard(
                language = language,
                notifyImports = state.notifyImports,
                hideContent = state.hideContent,
                onLanguageSelect = onLanguageSelect,
                onNotifyImportsChange = onNotifyImportsChange,
                onHideContentChange = { onEvent(SettingsEvent.HideContentChanged(it)) },
            )
            SectionCard(title = stringResource(R.string.settings_data)) {
                Hint(stringResource(R.string.settings_data_hint))
                HorizontalDivider(color = colors.line)
                LinkRow(
                    label = stringResource(R.string.settings_backup_export),
                    supporting = stringResource(R.string.settings_backup_export_hint),
                    icon = Icons.Outlined.FileUpload,
                    onClick = { onEvent(SettingsEvent.ExportClicked) },
                )
                HorizontalDivider(color = colors.line)
                LinkRow(
                    label = stringResource(R.string.settings_backup_import),
                    supporting = stringResource(R.string.settings_backup_import_hint),
                    icon = Icons.Outlined.FileDownload,
                    onClick = { onEvent(SettingsEvent.ImportClicked) },
                )
            }
            SectionCard(title = stringResource(R.string.settings_about)) {
                ValueRow(stringResource(R.string.settings_version), version, number = true)
                HorizontalDivider(color = colors.line)
                Credits()
            }
        }
    }
}

/** Language (one row, a picker on tap), import notifications and hiding the app's content. */
@Composable
private fun PreferencesCard(
    language: String,
    notifyImports: Boolean,
    hideContent: Boolean,
    onLanguageSelect: (String) -> Unit,
    onNotifyImportsChange: (Boolean) -> Unit,
    onHideContentChange: (Boolean) -> Unit,
) {
    var picking by rememberSaveable { mutableStateOf(false) }
    val current = LANGUAGES.firstOrNull { it.first == language }?.second
        ?: R.string.settings_language_system
    SectionCard(title = stringResource(R.string.settings_preferences)) {
        LinkRow(
            label = stringResource(R.string.settings_language),
            value = stringResource(current),
            icon = Icons.Outlined.Language,
            onClick = { picking = true },
        )
        HorizontalDivider(color = EksodaTheme.colors.line)
        SwitchRow(
            label = stringResource(R.string.settings_notify_imports),
            supporting = stringResource(R.string.settings_notify_imports_hint),
            icon = Icons.Outlined.Notifications,
            checked = notifyImports,
            onCheckedChange = onNotifyImportsChange,
        )
        HorizontalDivider(color = EksodaTheme.colors.line)
        SwitchRow(
            label = stringResource(R.string.settings_hide_content),
            supporting = stringResource(R.string.settings_hide_content_hint),
            icon = Icons.Outlined.VisibilityOff,
            checked = hideContent,
            onCheckedChange = onHideContentChange,
        )
    }
    if (picking) {
        LanguageDialog(
            selected = language,
            onSelect = { tag ->
                picking = false
                if (tag != language) onLanguageSelect(tag)
            },
            onDismiss = { picking = false },
        )
    }
}

@Composable
private fun LanguageDialog(selected: String, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_language)) },
        text = {
            Column(modifier = Modifier.selectableGroup()) {
                LANGUAGES.forEach { (tag, label) ->
                    LanguageRow(
                        label = stringResource(label),
                        selected = tag == selected,
                        onClick = { onSelect(tag) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) }
        },
    )
}

@Composable
private fun Hint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = EksodaTheme.colors.ink2,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

/** The leading icon of a row: decorative, the row's text says what it is. */
@Composable
private fun RowIcon(icon: ImageVector) {
    Icon(icon, contentDescription = null, tint = EksodaTheme.colors.ink2)
}

/**
 * Who made the app, with links to the author's GitHub profile, email and PayPal (for donations:
 * the app is free), and the license notice the GPL asks an interactive program to show
 * (copyright, no warranty, where the license is).
 * When no app can open a link (no browser or no mail app), the address is copied instead and a
 * short message says so.
 */
@Composable
private fun Credits() {
    val colors = EksodaTheme.colors
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val copied = stringResource(R.string.settings_link_copied)
    val open = { uri: String, shown: String ->
        runCatching { uriHandler.openUri(uri) }.onFailure {
            context.getSystemService(ClipboardManager::class.java)
                ?.setPrimaryClip(ClipData.newPlainText(shown, shown))
            Toast.makeText(context, copied, Toast.LENGTH_SHORT).show()
        }
    }
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            stringResource(R.string.settings_made_by),
            style = MaterialTheme.typography.bodySmall,
            color = colors.ink2,
        )
        Text(stringResource(R.string.settings_author), style = MaterialTheme.typography.bodyLarge)
    }
    LinkRow(
        label = stringResource(R.string.settings_github),
        value = GITHUB_LABEL,
        icon = Icons.Outlined.Code,
        onClick = { open(GITHUB_URL, GITHUB_URL) },
    )
    LinkRow(
        label = stringResource(R.string.settings_email),
        value = EMAIL,
        icon = Icons.Outlined.Email,
        onClick = { open("mailto:$EMAIL", EMAIL) },
    )
    LinkRow(
        label = stringResource(R.string.settings_donate),
        value = DONATE_LABEL,
        icon = Icons.Outlined.VolunteerActivism,
        onClick = { open(DONATE_URL, DONATE_URL) },
    )
    LinkRow(
        label = stringResource(R.string.settings_license),
        value = LICENSE_LABEL,
        icon = Icons.Outlined.Gavel,
        onClick = { open(LICENSE_URL, LICENSE_URL) },
    )
    Hint(stringResource(R.string.settings_license_notice))
}

private const val LICENSE_URL = "https://github.com/tsaklidis/MyEksoda/blob/master/LICENSE"
private const val LICENSE_LABEL = "GPL-3.0"
private const val GITHUB_URL = "https://github.com/tsaklidis"
private const val GITHUB_LABEL = "github.com/tsaklidis"
private const val EMAIL = "stefanos@tsaklidis.gr"
private const val DONATE_URL = "https://paypal.me/stsaklidis"
private const val DONATE_LABEL = "paypal.me/stsaklidis"

/** The languages offered, as (tag, label); `locales_config.xml` lists the same. */
private val LANGUAGES = listOf(
    AppLanguage.GREEK to R.string.settings_language_greek,
    AppLanguage.ENGLISH to R.string.settings_language_english,
    AppLanguage.SYSTEM to R.string.settings_language_system,
)

/** A choice in the language dialog, which pads its content itself. */
@Composable
private fun LanguageRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        RadioButton(selected = selected, onClick = null)
    }
}

/** A row that opens something: an icon, the label (with a line under it), a value, a chevron. */
@Composable
private fun LinkRow(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    value: String? = null,
    supporting: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        RowIcon(icon)
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (supporting != null) {
                Text(
                    supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = EksodaTheme.colors.ink2,
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (value != null) {
                Text(
                    value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = EksodaTheme.colors.compare,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = EksodaTheme.colors.ink2,
            )
        }
    }
}

/** A label and its value; with [onClick], the row opens an editor and ends with a chevron. */
@Composable
private fun ValueRow(
    label: String,
    value: String,
    number: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val colors = EksodaTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium.let { if (number) it.tabular() else it },
            color = if (onClick != null) colors.compare else colors.ink2,
        )
        if (onClick != null) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = colors.ink2,
            )
        }
    }
}

/**
 * The household's details, its annual income (for the e-payment target in Insights), and the
 * row that opens household sync.
 */
@Composable
private fun HouseholdCard(
    household: Household,
    sync: SyncSummary,
    onIncomeChange: (BigDecimal?) -> Unit,
    onSyncClick: () -> Unit,
) {
    val colors = EksodaTheme.colors
    val locale = appLocale()
    var editingIncome by rememberSaveable { mutableStateOf(false) }
    val income = household.annualIncome
    SectionCard(title = stringResource(R.string.settings_household)) {
        ValueRow(stringResource(R.string.settings_household_name), household.name)
        HorizontalDivider(color = colors.line)
        ValueRow(stringResource(R.string.settings_currency), household.defaultCurrency)
        // The time zone is not shown: every household is Europe/Athens for now
        // (OnboardingViewModel.DEFAULT_TIMEZONE, HouseholdZone.DEFAULT).
        HorizontalDivider(color = colors.line)
        ValueRow(
            label = stringResource(R.string.settings_income),
            value = income?.let { formatMoney(Money(it, household.defaultCurrency), locale) }
                ?: stringResource(R.string.settings_income_none),
            number = income != null,
            onClick = { editingIncome = true },
        )
        HorizontalDivider(color = colors.line)
        val lastSuccess = sync.lastSuccess
        LinkRow(
            label = stringResource(R.string.settings_sync),
            supporting = when {
                !sync.enabled -> stringResource(R.string.settings_sync_off)
                lastSuccess == null -> stringResource(R.string.settings_sync_on)
                else -> stringResource(R.string.settings_sync_on_last, dayTimeLabel(lastSuccess))
            },
            icon = Icons.Outlined.Sync,
            onClick = onSyncClick,
        )
    }
    if (editingIncome) {
        IncomeDialog(
            income = income,
            currency = household.defaultCurrency,
            onChange = {
                editingIncome = false
                onIncomeChange(it)
            },
            onDismiss = { editingIncome = false },
        )
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
            onBudgetsClick = {},
            onSyncClick = {},
            onEvent = {},
        )
    }
}
