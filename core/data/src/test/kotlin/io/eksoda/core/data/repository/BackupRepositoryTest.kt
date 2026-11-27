package io.eksoda.core.data.repository

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkInfo
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import io.eksoda.core.data.backup.BackupFile
import io.eksoda.core.data.imports.Fixtures
import io.eksoda.core.data.imports.ImportHarness
import io.eksoda.core.data.imports.ImportHarness.Companion.KOGIMOU
import io.eksoda.core.data.imports.ImportHarness.Companion.METRO_SAMPLE
import io.eksoda.core.data.imports.ImportHarness.Companion.MYMARKET
import io.eksoda.core.data.imports.ImportScheduler
import io.eksoda.core.data.sync.VatRows.RIGHT
import io.eksoda.core.data.sync.VatRows.WRONG
import io.eksoda.core.data.sync.VatRows.household
import io.eksoda.core.model.ImportSubmission
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Export, open and restore of the encrypted backup file, on one imported receipt. */
@RunWith(AndroidJUnit4::class)
class BackupRepositoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var app: ImportHarness
    private lateinit var scope: CoroutineScope
    private lateinit var backups: BackupRepositoryImpl

    @Before
    fun setUp() = runTest {
        app = ImportHarness(folder.root)
        listOf(MYMARKET, METRO_SAMPLE, KOGIMOU).forEach(app.fetcher::serve)
        app.import(MYMARKET)
        scope = CoroutineScope(SupervisorJob() + app.dispatcher)
        backups = BackupRepositoryImpl(
            app.context,
            app.database,
            app.coordinator,
            app.jobs,
            ImportScheduler(app.workManager),
            app.clock,
            scope,
            app.dispatcher,
        )
    }

    @After
    fun tearDown() {
        // A failed setUp keeps its own error.
        if (::scope.isInitialized) scope.cancel()
        if (::app.isInitialized) app.close()
    }

    private fun file(name: String): File = folder.root.resolve(name)

    /** A file encrypted like a backup (with a quick key derivation) around [plaintext]. */
    private fun backupOf(name: String, plaintext: ByteArray): Uri {
        val bytes = BackupFile.encrypt(
            plaintext,
            PASSPHRASE,
            "test",
            Instant.ofEpochMilli(ImportHarness.NOW),
            iterations = 1_000,
        )
        return Uri.fromFile(file(name).apply { writeBytes(bytes) })
    }

    private fun zip(vararg entries: Pair<String, String>): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            entries.forEach { (name, text) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(text.toByteArray())
                zip.closeEntry()
            }
        }
        return bytes.toByteArray()
    }

    private fun runs(jobId: String): List<WorkInfo.State> =
        app.workManager.getWorkInfosForUniqueWork("import-$jobId").get().map { it.state }

    private fun schema(): Int = app.database.openHelper.readableDatabase.version

    private suspend fun queue(fixture: String): String =
        (app.service.submit(Fixtures.url(fixture)) as ImportSubmission.Queued).jobId

    @Test
    fun `an export opens with its passphrase and restores what was there`() = runTest {
        // A pending import is part of the backup.
        val pendingThen = queue(METRO_SAMPLE)
        val destination = Uri.fromFile(file("backup.eksoda"))
        val exported = backups.export(destination, PASSPHRASE).getOrThrow()
        assertThat(exported.receipts).isEqualTo(1)
        assertThat(exported.merchants).isEqualTo(1)
        assertThat(exported.createdAt).isEqualTo(app.clock.instant())

        // Later: that import ran, another one waits.
        app.runWorker(pendingThen)
        // Its run is over (the test ran the worker itself): a queued run later is the restore's.
        app.workManager.cancelUniqueWork("import-$pendingThen")
        val pendingNow = queue(KOGIMOU)
        assertThat(app.count("SELECT COUNT(*) FROM receipt")).isEqualTo(2)

        val plan = backups.open(destination, PASSPHRASE).getOrThrow()
        assertThat(plan.summary.receipts).isEqualTo(1)
        assertThat(plan.summary.householdName).isNull()
        backups.restore(plan).getOrThrow()
        assertThat(app.count("SELECT COUNT(*) FROM receipt")).isEqualTo(1)
        // The backup's pending import waits again; the one made after it is gone.
        val pending = app.jobs.pending().map { it.id }
        assertThat(pending).isEqualTo(listOf(pendingThen))
        assertThat(app.jobs.get(pendingNow)).isNull()
        // Its queued run stops, so it never runs for a job that is gone.
        assertThat(runs(pendingNow).last()).isEqualTo(WorkInfo.State.CANCELLED)
        assertThat(runs(pendingThen).last()).isEqualTo(WorkInfo.State.ENQUEUED)
    }

    @Test
    fun `a backup made before the VAT repair restores repaired`() = runTest {
        // Made before 0.3.10: a Sklavenitis receipt under its VAT followed by phone digits.
        app.database.openHelper.writableDatabase.household(right = "m1", wrong = "m2")
        val destination = Uri.fromFile(file("before.eksoda"))
        backups.export(destination, PASSPHRASE).getOrThrow()

        backups.restore(backups.open(destination, PASSPHRASE).getOrThrow()).getOrThrow()

        assertThat(app.count("SELECT COUNT(*) FROM merchant WHERE vat_number = '$WRONG'"))
            .isEqualTo(0)
        assertThat(app.count("SELECT COUNT(*) FROM receipt WHERE merchant_id = 'm1'"))
            .isEqualTo(2)
        assertThat(app.count("SELECT COUNT(*) FROM receipt WHERE issuer_vat = '$RIGHT'"))
            .isEqualTo(2)
    }

    @Test
    fun `a wrong passphrase does not open the backup`() = runTest {
        val destination = Uri.fromFile(file("backup.eksoda"))
        backups.export(destination, PASSPHRASE).getOrThrow()
        val opened = backups.open(destination, "another passphrase")
        assertThat(opened.exceptionOrNull().backupError()).isEqualTo(BackupError.WRONG_PASSPHRASE)
    }

    @Test
    fun `a file that is missing, or cannot be written, is a file error`() = runTest {
        val missing = backups.open(Uri.fromFile(file("missing.eksoda")), PASSPHRASE)
        assertThat(missing.exceptionOrNull().backupError()).isEqualTo(BackupError.FILE_ERROR)
        val folderUri = Uri.fromFile(folder.newFolder("a folder"))
        val written = backups.export(folderUri, PASSPHRASE)
        assertThat(written.exceptionOrNull().backupError()).isEqualTo(BackupError.FILE_ERROR)
        // Anything unexpected counts as a file error too.
        assertThat(null.backupError()).isEqualTo(BackupError.FILE_ERROR)
    }

    @Test
    fun `another file is not a backup`() = runTest {
        val other = Uri.fromFile(file("notes.txt").apply { writeText("shopping list") })
        val opened = backups.open(other, PASSPHRASE)
        assertThat(opened.exceptionOrNull().backupError()).isEqualTo(BackupError.NOT_A_BACKUP)
    }

    @Test
    fun `a backup of a newer schema is refused`() = runTest {
        val newer = zip("manifest.json" to manifest(schema() + 1, counts = "{}"))
        val opened = backups.open(backupOf("newer.eksoda", newer), PASSPHRASE)
        assertThat(opened.exceptionOrNull().backupError()).isEqualTo(BackupError.NEWER_VERSION)
    }

    @Test
    fun `a backup whose rows do not match its manifest is damaged`() = runTest {
        val broken = zip(
            "manifest.json" to manifest(schema(), counts = """{"receipt_item":1}"""),
            "receipt_item.ndjson" to "",
        )
        val opened = backups.open(backupOf("broken.eksoda", broken), PASSPHRASE)
        assertThat(opened.exceptionOrNull().backupError()).isEqualTo(BackupError.DAMAGED)
    }

    private fun manifest(schema: Int, counts: String) =
        """{"format":1,"schema":$schema,"counts":$counts,"household":{"name":null},""" +
            """"first_issued":null,"last_issued":null}"""

    private companion object {
        const val PASSPHRASE = "correct horse battery"
    }
}
