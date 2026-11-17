package io.eksoda.feature.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.eksoda.core.data.repository.BackupError
import io.eksoda.core.data.repository.BackupRepository
import io.eksoda.core.data.repository.HouseholdRepository
import io.eksoda.core.data.repository.RestorePlan
import io.eksoda.core.data.repository.SettingsRepository
import io.eksoda.core.data.repository.backupError
import io.eksoda.core.model.BackupSummary
import io.eksoda.core.model.Household
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

data class SettingsUiState(
    val household: Household? = null,
    val notifyImports: Boolean = false,
    val backup: BackupDialog = BackupDialog.None,
)

/** The export and import steps that show a dialog. */
sealed interface BackupDialog {
    data object None : BackupDialog

    /** Choose and repeat the passphrase, then the file. */
    data object ExportPassphrase : BackupDialog

    data class ImportPassphrase(val wrongPassphrase: Boolean = false) : BackupDialog

    data class Working(val step: BackupStep) : BackupDialog

    data class ConfirmRestore(val summary: BackupSummary) : BackupDialog

    data object Exported : BackupDialog

    data object Restored : BackupDialog

    data class Failed(val step: BackupStep, val error: BackupError) : BackupDialog
}

enum class BackupStep { EXPORT, OPEN, RESTORE }

sealed interface SettingsEvent {
    data class NotifyImportsChanged(val enabled: Boolean) : SettingsEvent

    data object ExportClicked : SettingsEvent

    data class ExportPassphraseSet(val passphrase: String) : SettingsEvent

    /** The document the user created for the backup; null when they cancelled. */
    data class ExportFileChosen(val uri: Uri?) : SettingsEvent

    data object ImportClicked : SettingsEvent

    /** The backup file the user picked; null when they cancelled. */
    data class ImportFileChosen(val uri: Uri?) : SettingsEvent

    data class ImportPassphraseEntered(val passphrase: String) : SettingsEvent

    data object RestoreConfirmed : SettingsEvent

    data object BackupDialogDismissed : SettingsEvent
}

sealed interface SettingsEffect {
    /** Open the system "save as" screen with this file name. */
    data class ChooseExportFile(val fileName: String) : SettingsEffect

    data object ChooseImportFile : SettingsEffect
}

/** Settings (`android-app.md` §6.10): language, notifications, backup, the household, catalog links. */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    households: HouseholdRepository,
    private val settings: SettingsRepository,
    private val backups: BackupRepository,
    private val clock: Clock,
) : ViewModel() {
    private val backup = MutableStateFlow<BackupDialog>(BackupDialog.None)
    private val _effects = Channel<SettingsEffect>(Channel.BUFFERED)
    val effects: Flow<SettingsEffect> = _effects.receiveAsFlow()

    // Held in memory only between the dialog and the file picker; never in saved state.
    private var exportPassphrase: String? = null
    private var importSource: Uri? = null
    private var plan: RestorePlan? = null

    val uiState: StateFlow<SettingsUiState> =
        combine(households.household, settings.notifyImports, backup) { household, notify, dialog ->
            SettingsUiState(household = household, notifyImports = notify, backup = dialog)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), SettingsUiState())

    fun onEvent(event: SettingsEvent) {
        when (event) {
            is SettingsEvent.NotifyImportsChanged ->
                viewModelScope.launch { settings.setNotifyImports(event.enabled) }
            SettingsEvent.ExportClicked -> backup.value = BackupDialog.ExportPassphrase
            is SettingsEvent.ExportPassphraseSet -> {
                exportPassphrase = event.passphrase
                backup.value = BackupDialog.None
                _effects.trySend(SettingsEffect.ChooseExportFile(fileName()))
            }
            is SettingsEvent.ExportFileChosen -> export(event.uri)
            SettingsEvent.ImportClicked -> _effects.trySend(SettingsEffect.ChooseImportFile)
            is SettingsEvent.ImportFileChosen -> {
                importSource = event.uri
                if (event.uri != null) backup.value = BackupDialog.ImportPassphrase()
            }
            is SettingsEvent.ImportPassphraseEntered -> open(event.passphrase)
            SettingsEvent.RestoreConfirmed -> restore()
            SettingsEvent.BackupDialogDismissed -> dismiss()
        }
    }

    private fun export(uri: Uri?) {
        val passphrase = exportPassphrase
        exportPassphrase = null
        if (uri == null || passphrase == null) return
        backup.value = BackupDialog.Working(BackupStep.EXPORT)
        viewModelScope.launch {
            backups.export(uri, passphrase)
                .onSuccess { backup.value = BackupDialog.Exported }
                .onFailure { backup.value = BackupDialog.Failed(BackupStep.EXPORT, it.backupError()) }
        }
    }

    private fun open(passphrase: String) {
        val source = importSource ?: return
        backup.value = BackupDialog.Working(BackupStep.OPEN)
        viewModelScope.launch {
            backups.open(source, passphrase)
                .onSuccess {
                    plan = it
                    backup.value = BackupDialog.ConfirmRestore(it.summary)
                }
                .onFailure {
                    val error = it.backupError()
                    backup.value = if (error == BackupError.WRONG_PASSPHRASE) {
                        BackupDialog.ImportPassphrase(wrongPassphrase = true)
                    } else {
                        BackupDialog.Failed(BackupStep.OPEN, error)
                    }
                }
        }
    }

    private fun restore() {
        val restoring = plan ?: return
        plan = null
        importSource = null
        backup.value = BackupDialog.Working(BackupStep.RESTORE)
        viewModelScope.launch {
            backups.restore(restoring)
                .onSuccess { backup.value = BackupDialog.Restored }
                .onFailure {
                    backup.value = BackupDialog.Failed(BackupStep.RESTORE, it.backupError())
                }
        }
    }

    private fun dismiss() {
        // A running export or restore keeps its dialog until it ends.
        if (backup.value is BackupDialog.Working) return
        exportPassphrase = null
        importSource = null
        plan = null
        backup.value = BackupDialog.None
    }

    private fun fileName() = "eksoda-backup-${LocalDate.now(clock)}.eksoda"

    private companion object {
        const val STOP_TIMEOUT = 5_000L
    }
}
