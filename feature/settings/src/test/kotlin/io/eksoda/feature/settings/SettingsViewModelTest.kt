package io.eksoda.feature.settings

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import io.eksoda.core.data.repository.BackupError
import io.eksoda.core.data.repository.BackupException
import io.eksoda.core.testing.FakeBackupRepository
import io.eksoda.core.testing.FakeHouseholdRepository
import io.eksoda.core.testing.FakeSettingsRepository
import io.eksoda.core.testing.FakeSyncRepository
import io.eksoda.core.testing.MainDispatcherRule
import io.eksoda.core.testing.keepCollecting
import io.eksoda.core.testing.testBackupSummary
import io.eksoda.core.testing.testHousehold
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** Settings, above all the export and restore steps: the only copy of the data off the phone. */
@RunWith(AndroidJUnit4::class)
class SettingsViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val households = FakeHouseholdRepository(testHousehold())
    private val settings = FakeSettingsRepository()
    private val backups = FakeBackupRepository()
    private val sync = FakeSyncRepository()
    private val clock = Clock.fixed(Instant.parse("2026-11-20T10:00:00Z"), ZoneOffset.UTC)
    private val viewModel = SettingsViewModel(households, settings, backups, sync, clock)
    private val dialog get() = viewModel.uiState.value.backup
    private val file = Uri.parse("content://documents/backup")

    @Test
    fun `the screen shows the household, the notification switch and the sync row`() = runTest {
        keepCollecting(viewModel.uiState)
        sync.on.value = true
        sync.last.value = Instant.parse("2026-11-19T08:00:00Z")
        viewModel.onEvent(SettingsEvent.NotifyImportsChanged(true))
        val state = viewModel.uiState.value
        assertThat(state.household).isEqualTo(testHousehold())
        assertThat(state.notifyImports).isTrue()
        assertThat(state.sync).isEqualTo(SyncSummary(true, Instant.parse("2026-11-19T08:00:00Z")))
    }

    @Test
    fun `an export asks for a passphrase, then a dated file, then writes the backup`() = runTest {
        keepCollecting(viewModel.uiState)
        viewModel.effects.test {
            viewModel.onEvent(SettingsEvent.ExportClicked)
            assertThat(dialog).isEqualTo(BackupDialog.ExportPassphrase)
            viewModel.onEvent(SettingsEvent.ExportPassphraseSet("correct horse battery"))
            assertThat(awaitItem())
                .isEqualTo(SettingsEffect.ChooseExportFile("eksoda-backup-2026-11-20.eksoda"))
            assertThat(dialog).isEqualTo(BackupDialog.None)
        }
        viewModel.onEvent(SettingsEvent.ExportFileChosen(file))
        assertThat(dialog).isEqualTo(BackupDialog.Exported)
        assertThat(backups.steps).containsExactly("export")
    }

    @Test
    fun `no file, or no passphrase, is no export`() = runTest {
        keepCollecting(viewModel.uiState)
        viewModel.onEvent(SettingsEvent.ExportPassphraseSet("correct horse battery"))
        viewModel.onEvent(SettingsEvent.ExportFileChosen(null))
        // The passphrase is forgotten with the cancelled picker.
        viewModel.onEvent(SettingsEvent.ExportFileChosen(file))
        assertThat(backups.steps).isEqualTo(emptyList())
        assertThat(dialog).isEqualTo(BackupDialog.None)
    }

    @Test
    fun `a failed export says which step failed and why`() = runTest {
        keepCollecting(viewModel.uiState)
        backups.exportResult = Result.failure(BackupException(BackupError.FILE_ERROR))
        viewModel.onEvent(SettingsEvent.ExportPassphraseSet("correct horse battery"))
        viewModel.onEvent(SettingsEvent.ExportFileChosen(file))
        assertThat(dialog).isEqualTo(BackupDialog.Failed(BackupStep.EXPORT, BackupError.FILE_ERROR))
    }

    @Test
    fun `a restore opens the file, shows what is in it, and replaces the data once confirmed`() =
        runTest {
            keepCollecting(viewModel.uiState)
            viewModel.effects.test {
                viewModel.onEvent(SettingsEvent.ImportClicked)
                assertThat(awaitItem()).isEqualTo(SettingsEffect.ChooseImportFile)
            }
            viewModel.onEvent(SettingsEvent.ImportFileChosen(file))
            assertThat(dialog).isEqualTo(BackupDialog.ImportPassphrase())
            viewModel.onEvent(SettingsEvent.ImportPassphraseEntered("correct horse battery"))
            assertThat(dialog).isEqualTo(BackupDialog.ConfirmRestore(testBackupSummary()))
            // Nothing is replaced before the user confirms.
            assertThat(backups.steps).containsExactly("open")
            viewModel.onEvent(SettingsEvent.RestoreConfirmed)
            assertThat(dialog).isEqualTo(BackupDialog.Restored)
            assertThat(backups.steps).containsExactly("open", "restore")
            // The plan is used once.
            viewModel.onEvent(SettingsEvent.RestoreConfirmed)
            assertThat(backups.steps).containsExactly("open", "restore")
        }

    @Test
    fun `a wrong passphrase asks again, other errors end the restore`() = runTest {
        keepCollecting(viewModel.uiState)
        viewModel.onEvent(SettingsEvent.ImportFileChosen(file))
        backups.openResult = Result.failure(BackupException(BackupError.WRONG_PASSPHRASE))
        viewModel.onEvent(SettingsEvent.ImportPassphraseEntered("wrong"))
        assertThat(dialog).isEqualTo(BackupDialog.ImportPassphrase(wrongPassphrase = true))
        backups.openResult = Result.failure(BackupException(BackupError.NEWER_VERSION))
        viewModel.onEvent(SettingsEvent.ImportPassphraseEntered("right"))
        assertThat(
            dialog,
        ).isEqualTo(BackupDialog.Failed(BackupStep.OPEN, BackupError.NEWER_VERSION))
        // Anything unexpected counts as a file error.
        backups.openResult = Result.failure(IllegalStateException("?"))
        viewModel.onEvent(SettingsEvent.ImportPassphraseEntered("right"))
        assertThat(dialog).isEqualTo(BackupDialog.Failed(BackupStep.OPEN, BackupError.FILE_ERROR))
    }

    @Test
    fun `a failed restore says so, and a cancelled picker opens nothing`() = runTest {
        keepCollecting(viewModel.uiState)
        viewModel.onEvent(SettingsEvent.ImportFileChosen(null))
        assertThat(dialog).isEqualTo(BackupDialog.None)
        viewModel.onEvent(SettingsEvent.ImportPassphraseEntered("x"))
        assertThat(backups.steps).isEqualTo(emptyList())

        viewModel.onEvent(SettingsEvent.ImportFileChosen(file))
        viewModel.onEvent(SettingsEvent.ImportPassphraseEntered("right"))
        backups.restoreResult = Result.failure(BackupException(BackupError.DAMAGED))
        viewModel.onEvent(SettingsEvent.RestoreConfirmed)
        assertThat(dialog).isEqualTo(BackupDialog.Failed(BackupStep.RESTORE, BackupError.DAMAGED))
    }

    @Test
    fun `dismissing forgets the open backup, so confirming restores nothing`() = runTest {
        keepCollecting(viewModel.uiState)
        viewModel.onEvent(SettingsEvent.ImportFileChosen(file))
        viewModel.onEvent(SettingsEvent.ImportPassphraseEntered("right"))
        viewModel.onEvent(SettingsEvent.BackupDialogDismissed)
        assertThat(dialog).isEqualTo(BackupDialog.None)
        viewModel.onEvent(SettingsEvent.RestoreConfirmed)
        assertThat(backups.steps).containsExactly("open")
    }

    @Test
    fun `the annual income is saved, and a failed save says so`() = runTest {
        viewModel.effects.test {
            viewModel.onEvent(SettingsEvent.AnnualIncomeChanged(BigDecimal("24000")))
            expectNoEvents()
            households.failure = IllegalStateException("disk full")
            viewModel.onEvent(SettingsEvent.AnnualIncomeChanged(null))
            assertThat(awaitItem()).isEqualTo(SettingsEffect.IncomeSaveFailed)
        }
        assertThat(
            households.calls,
        ).containsExactly("setAnnualIncome(24000)", "setAnnualIncome(null)")
    }
}
