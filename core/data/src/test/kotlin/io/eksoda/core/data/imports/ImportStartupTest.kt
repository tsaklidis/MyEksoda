package io.eksoda.core.data.imports

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkInfo
import assertk.assertThat
import assertk.assertions.isEqualTo
import io.eksoda.core.data.imports.ImportHarness.Companion.MYMARKET
import io.eksoda.core.model.ImportSubmission
import io.eksoda.engine.FormatChanged
import io.eksoda.engine.ReceiptNotFound
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
import java.io.IOException

/** What runs at every app start: queued imports, and the retry after an update. */
@RunWith(AndroidJUnit4::class)
class ImportStartupTest {
    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var app: ImportHarness
    private lateinit var scope: CoroutineScope
    private lateinit var startup: ImportStartup
    private lateinit var version: AppVersion

    @Before
    fun setUp() {
        app = ImportHarness(folder.root)
        app.fetcher.serve(MYMARKET)
        scope = CoroutineScope(SupervisorJob() + app.dispatcher)
        version = AppVersion(app.context)
        startup = startupWith("startup")
    }

    /** The app start with its preferences in [file]: another file is a start with them cleared. */
    private fun startupWith(file: String, writable: Boolean = true): ImportStartup {
        val preferences = PreferenceDataStoreFactory.create(scope = scope) {
            folder.root.resolve("$file.preferences_pb")
        }
        return ImportStartup(
            app.jobs,
            app.coordinator,
            ImportScheduler(app.workManager),
            if (writable) preferences else ReadOnly(preferences),
            version,
            app.clock,
        )
    }

    @After
    fun tearDown() {
        // A failed setUp keeps its own error.
        if (::scope.isInitialized) scope.cancel()
        if (::app.isInitialized) app.close()
    }

    private suspend fun status(jobId: String): String? = app.jobs.get(jobId)?.status

    /** The job failed as it would have with the version before this one. */
    private fun failedWithOlderVersion(jobId: String) {
        app.database.openHelper.writableDatabase.execSQL(
            "UPDATE import_job SET status = 'failed', app_version_at_failure = ? WHERE id = ?",
            arrayOf<Any>(version.code - 1, jobId),
        )
    }

    private fun runs(jobId: String): List<WorkInfo.State> =
        app.workManager.getWorkInfosForUniqueWork("import-$jobId").get().map { it.state }

    @Test
    fun `after an update, an import that failed on a changed page is tried once more`() = runTest {
        app.fetcher.failure = FormatChanged()
        val jobId = app.import(MYMARKET)
        assertThat(status(jobId)).isEqualTo("failed")
        failedWithOlderVersion(jobId)
        // The run queued when the link was scanned is gone: a queued run now is the startup's.
        app.workManager.cancelUniqueWork("import-$jobId")
        assertThat(runs(jobId)).isEqualTo(listOf(WorkInfo.State.CANCELLED))

        startup.run()
        assertThat(status(jobId)).isEqualTo("pending")
        assertThat(runs(jobId).last()).isEqualTo(WorkInfo.State.ENQUEUED)

        // It fails again, now with this version: that is recorded, so it is not retried, not
        // even by a start that forgot which version ran last.
        app.runWorker(jobId)
        assertThat(status(jobId)).isEqualTo("failed")
        assertThat(app.jobs.get(jobId)?.appVersionAtFailure).isEqualTo(version.code)
        startup.run()
        assertThat(status(jobId)).isEqualTo("failed")
        startupWith("cleared").run()
        assertThat(status(jobId)).isEqualTo("failed")
    }

    @Test
    fun `a failure no update can fix stays failed`() = runTest {
        app.fetcher.failure = FormatChanged()
        val jobId = app.import(MYMARKET)
        // The receipt was not found: a parser fix does not change that.
        app.database.openHelper.writableDatabase.execSQL(
            "UPDATE import_job SET last_error_code = ? WHERE id = ?",
            arrayOf(ReceiptNotFound().code.code, jobId),
        )
        failedWithOlderVersion(jobId)
        startup.run()
        assertThat(status(jobId)).isEqualTo("failed")
    }

    @Test
    fun `a settings file that cannot be written still queues the lost imports`() = runTest {
        val submission = app.service.submit(Fixtures.url(MYMARKET))
        val jobId = (submission as ImportSubmission.Queued).jobId
        app.workManager.cancelUniqueWork("import-$jobId")

        // Recording the version fails; the start goes on.
        startupWith("read-only", writable = false).run()
        assertThat(runs(jobId).last()).isEqualTo(WorkInfo.State.ENQUEUED)
        assertThat(status(jobId)).isEqualTo("pending")
    }

    @Test
    fun `a pending import whose run was lost is queued again`() = runTest {
        val submission = app.service.submit(Fixtures.url(MYMARKET))
        val jobId = (submission as ImportSubmission.Queued).jobId
        app.workManager.cancelUniqueWork("import-$jobId")
        assertThat(runs(jobId)).isEqualTo(listOf(WorkInfo.State.CANCELLED))

        startup.run()
        assertThat(runs(jobId).last()).isEqualTo(WorkInfo.State.ENQUEUED)
        assertThat(status(jobId)).isEqualTo("pending")
    }
}

/** Reads work; every write fails as on a full or failing disk. */
private class ReadOnly(delegate: DataStore<Preferences>) : DataStore<Preferences> {
    override val data = delegate.data

    override suspend fun updateData(
        transform: suspend (t: Preferences) -> Preferences,
    ): Preferences = throw IOException("cannot write")
}
