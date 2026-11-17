package io.eksoda.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.eksoda.core.data.repository.BackupError
import io.eksoda.core.data.repository.BackupRepository
import io.eksoda.core.designsystem.theme.EksodaTheme
import io.eksoda.core.model.BackupSummary
import io.eksoda.core.ui.text.appLocale
import io.eksoda.core.ui.text.dateFormatter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** The dialog for the current export or import step, or nothing. */
@Composable
internal fun BackupDialogs(dialog: BackupDialog, onEvent: (SettingsEvent) -> Unit) {
    val dismiss = { onEvent(SettingsEvent.BackupDialogDismissed) }
    when (dialog) {
        BackupDialog.None -> Unit

        BackupDialog.ExportPassphrase -> ExportPassphraseDialog(
            onConfirm = { onEvent(SettingsEvent.ExportPassphraseSet(it)) },
            onDismiss = dismiss,
        )

        is BackupDialog.ImportPassphrase -> ImportPassphraseDialog(
            wrongPassphrase = dialog.wrongPassphrase,
            onConfirm = { onEvent(SettingsEvent.ImportPassphraseEntered(it)) },
            onDismiss = dismiss,
        )

        is BackupDialog.Working -> WorkingDialog(dialog.step)

        is BackupDialog.ConfirmRestore -> ConfirmRestoreDialog(
            summary = dialog.summary,
            onConfirm = { onEvent(SettingsEvent.RestoreConfirmed) },
            onDismiss = dismiss,
        )

        BackupDialog.Exported -> MessageDialog(
            title = stringResource(R.string.backup_exported_title),
            text = stringResource(R.string.backup_exported_text),
            onDismiss = dismiss,
        )

        BackupDialog.Restored -> MessageDialog(
            title = stringResource(R.string.backup_restored_title),
            text = stringResource(R.string.backup_restored_text),
            onDismiss = dismiss,
        )

        is BackupDialog.Failed -> MessageDialog(
            title = stringResource(
                if (dialog.step == BackupStep.EXPORT) {
                    R.string.backup_failed_export
                } else {
                    R.string.backup_failed_import
                },
            ),
            text = stringResource(errorRes(dialog.error, dialog.step)),
            onDismiss = dismiss,
        )
    }
}

private fun errorRes(error: BackupError, step: BackupStep): Int = when (error) {
    BackupError.NOT_A_BACKUP -> R.string.backup_error_not_a_backup

    BackupError.WRONG_PASSPHRASE -> R.string.backup_error_passphrase

    BackupError.NEWER_VERSION -> R.string.backup_error_newer

    BackupError.DAMAGED ->
        if (step ==
            BackupStep.EXPORT
        ) {
            R.string.backup_error_export
        } else {
            R.string.backup_error_damaged
        }

    BackupError.FILE_ERROR ->
        if (step == BackupStep.EXPORT) R.string.backup_error_export else R.string.backup_error_file
}

/**
 * Passphrase fields keep their text in plain `remember`, not `rememberSaveable`: a passphrase
 * never goes into saved instance state.
 */
@Composable
private fun ExportPassphraseDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var passphrase by remember { mutableStateOf("") }
    var repeated by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    val accepted = passphraseAccepted(passphrase, repeated)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_export_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    stringResource(R.string.backup_export_text),
                    style = MaterialTheme.typography.bodyMedium,
                )
                PassphraseField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    label = stringResource(R.string.backup_passphrase),
                    visible = visible,
                    imeAction = ImeAction.Next,
                    supporting = when {
                        passphrase.isEmpty() -> null

                        passphrase.length < BackupRepository.MIN_PASSPHRASE_LENGTH ->
                            pluralStringResource(
                                R.plurals.backup_passphrase_min,
                                BackupRepository.MIN_PASSPHRASE_LENGTH,
                                BackupRepository.MIN_PASSPHRASE_LENGTH,
                            )

                        else -> strengthText(passphraseStrength(passphrase))
                    },
                )
                PassphraseField(
                    value = repeated,
                    onValueChange = { repeated = it },
                    label = stringResource(R.string.backup_passphrase_repeat),
                    visible = visible,
                    imeAction = ImeAction.Done,
                    supporting = if (repeated.isNotEmpty() && repeated != passphrase) {
                        stringResource(R.string.backup_passphrase_mismatch)
                    } else {
                        null
                    },
                    isError = repeated.isNotEmpty() && repeated != passphrase,
                )
                ShowPassphraseRow(visible = visible, onChange = { visible = it })
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(passphrase) }, enabled = accepted) {
                Text(stringResource(R.string.backup_choose_file))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.backup_cancel)) }
        },
    )
}

@Composable
private fun ImportPassphraseDialog(
    wrongPassphrase: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var passphrase by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_import_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(R.string.backup_import_text),
                    style = MaterialTheme.typography.bodyMedium,
                )
                PassphraseField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    label = stringResource(R.string.backup_passphrase),
                    visible = visible,
                    imeAction = ImeAction.Done,
                    supporting = if (wrongPassphrase) {
                        stringResource(R.string.backup_error_passphrase)
                    } else {
                        null
                    },
                    isError = wrongPassphrase,
                )
                ShowPassphraseRow(visible = visible, onChange = { visible = it })
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(passphrase) }, enabled = passphrase.isNotEmpty()) {
                Text(stringResource(R.string.backup_open))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.backup_cancel)) }
        },
    )
}

@Composable
private fun PassphraseField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    visible: Boolean,
    imeAction: ImeAction,
    supporting: String?,
    isError: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        isError = isError,
        visualTransformation = if (visible) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            autoCorrectEnabled = false,
            imeAction = imeAction,
        ),
        supportingText = supporting?.let { { Text(it) } },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ShowPassphraseRow(visible: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = visible, onValueChange = onChange, role = Role.Checkbox),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Checkbox(checked = visible, onCheckedChange = null)
        Text(
            stringResource(R.string.backup_show_passphrase),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun strengthText(strength: PassphraseStrength): String = stringResource(
    when (strength) {
        PassphraseStrength.WEAK -> R.string.backup_strength_weak
        PassphraseStrength.FAIR -> R.string.backup_strength_fair
        PassphraseStrength.STRONG -> R.string.backup_strength_strong
    },
)

@Composable
private fun WorkingDialog(step: BackupStep) {
    AlertDialog(
        // Not dismissible: the export or restore finishes either way.
        onDismissRequest = {},
        confirmButton = {},
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    stringResource(
                        when (step) {
                            BackupStep.EXPORT -> R.string.backup_working_export
                            BackupStep.OPEN -> R.string.backup_working_open
                            BackupStep.RESTORE -> R.string.backup_working_restore
                        },
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                )
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        },
    )
}

@Composable
private fun ConfirmRestoreDialog(
    summary: BackupSummary,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val locale = appLocale()
    val day = dateFormatter("d MMMM yyyy", locale)
    val month = dateFormatter("LLL yyyy", locale)
    val created = summary.createdAt.atZone(ZoneId.systemDefault()).toLocalDate()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_restore_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    stringResource(R.string.backup_restore_made, day.format(created)),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Column {
                    Text(
                        pluralStringResource(
                            R.plurals.backup_receipts,
                            summary.receipts,
                            summary.receipts,
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    val first = summary.firstIssued
                    val last = summary.lastIssued
                    if (first != null && last != null) {
                        Text(
                            stringResource(
                                R.string.backup_restore_range,
                                month.format(first),
                                month.format(last),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = EksodaTheme.colors.ink2,
                        )
                    }
                    Text(
                        pluralStringResource(
                            R.plurals.backup_products,
                            summary.products,
                            summary.products,
                        ) + " · " + pluralStringResource(
                            R.plurals.backup_merchants,
                            summary.merchants,
                            summary.merchants,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = EksodaTheme.colors.ink2,
                    )
                }
                Text(
                    stringResource(R.string.backup_restore_warning),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    stringResource(R.string.backup_restore_confirm),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.backup_cancel)) }
        },
    )
}

@Composable
private fun MessageDialog(title: String, text: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.backup_ok)) }
        },
    )
}

@Preview
@Composable
private fun ExportPassphraseDialogPreview() {
    EksodaTheme { ExportPassphraseDialog(onConfirm = {}, onDismiss = {}) }
}

@Preview
@Composable
private fun ConfirmRestoreDialogPreview() {
    EksodaTheme {
        ConfirmRestoreDialog(
            summary = BackupSummary(
                createdAt = Instant.parse("2026-11-17T10:00:00Z"),
                householdName = "Σπίτι",
                receipts = 412,
                products = 830,
                merchants = 37,
                firstIssued = LocalDate.of(2025, 1, 3),
                lastIssued = LocalDate.of(2026, 11, 15),
            ),
            onConfirm = {},
            onDismiss = {},
        )
    }
}
