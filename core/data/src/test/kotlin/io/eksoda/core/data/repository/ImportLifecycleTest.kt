package io.eksoda.core.data.repository

import androidx.paging.testing.asSnapshot
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import io.eksoda.core.data.imports.Fixtures
import io.eksoda.core.data.imports.ImportHarness
import io.eksoda.core.data.imports.ImportHarness.Companion.KOGIMOU
import io.eksoda.core.data.imports.ImportHarness.Companion.METRO_SAMPLE
import io.eksoda.core.data.imports.ImportHarness.Companion.MYMARKET
import io.eksoda.core.model.ImportOutcome
import io.eksoda.core.model.ImportStatus
import io.eksoda.core.model.ImportSubmission
import io.eksoda.engine.FormatChanged
import io.eksoda.engine.ReceiptNotFound
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

/**
 * An import job from submission to its end, through the repository the scan and imports screens
 * use: one job per receipt whatever is scanned again, retries that end coherently, the list and
 * counts following each step. Asserted on the stored rows and on what the repository returns.
 */
@RunWith(AndroidJUnit4::class)
class ImportLifecycleTest {
    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var app: ImportHarness

    private val repository get() = app.repository

    @Before
    fun setUp() {
        app = ImportHarness(folder.root)
        listOf(MYMARKET, METRO_SAMPLE, KOGIMOU).forEach(app.fetcher::serve)
    }

    @After
    fun tearDown() {
        if (::app.isInitialized) app.close()
    }

    private fun jobCount() = app.count("SELECT COUNT(*) FROM import_job")

    private suspend fun submit(fixture: String) =
        repository.importReceipt(Fixtures.url(fixture)).getOrThrow()

    private suspend fun queued(fixture: String) = (submit(fixture) as ImportSubmission.Queued).jobId

    @Test
    fun `a link scanned again while it waits is that job, not a second one`() = runTest {
        val jobId = queued(MYMARKET)
        assertThat(submit(MYMARKET)).isEqualTo(ImportSubmission.AlreadyPending(jobId))
        assertThat(submit(MYMARKET)).isEqualTo(ImportSubmission.AlreadyPending(jobId))
        assertThat(jobCount()).isEqualTo(1)
    }

    @Test
    fun `a failed link scanned again starts its own job over`() = runTest {
        app.fetcher.failure = FormatChanged()
        val jobId = queued(MYMARKET)
        app.runWorker(jobId)
        assertThat(app.jobs.get(jobId)?.status).isEqualTo("failed")

        assertThat((submit(MYMARKET) as ImportSubmission.Queued).jobId).isEqualTo(jobId)
        assertThat(jobCount()).isEqualTo(1)
        assertThat(app.jobs.get(jobId)?.status).isEqualTo("pending")
    }

    @Test
    fun `only a failed job can be retried`() = runTest {
        app.fetcher.failure = FormatChanged()
        val failed = queued(MYMARKET)
        app.runWorker(failed)
        app.fetcher.failure = null
        val pending = queued(METRO_SAMPLE)
        val completed = app.import(KOGIMOU)

        assertThat(repository.retryImport(failed).isSuccess).isTrue()
        for ((name, id) in listOf("pending" to pending, "completed" to completed, "gone" to "x")) {
            assertThat(repository.retryImport(id).isFailure, name = name).isTrue()
        }
        // The refused retries changed nothing.
        assertThat(app.jobs.get(completed)?.status).isEqualTo("completed")
        assertThat(app.count("SELECT COUNT(*) FROM receipt")).isEqualTo(1)
    }

    @Test
    fun `a retry that succeeds stores the receipt once and ends the job cleanly`() = runTest {
        app.fetcher.failure = FormatChanged()
        val jobId = queued(MYMARKET)
        app.runWorker(jobId)
        app.fetcher.failure = null

        repository.retryImport(jobId).getOrThrow()
        app.runWorker(jobId)
        // A second run of the same work, as WorkManager may start it, adds nothing.
        app.runWorker(jobId)

        val job = checkNotNull(app.jobs.get(jobId))
        assertThat(job.status).isEqualTo("completed")
        assertThat(job.outcome).isEqualTo("created")
        assertThat(job.receiptId).isNotNull()
        assertThat(app.count("SELECT COUNT(*) FROM receipt")).isEqualTo(1)
        // The list shows it done, with no error left over from the failed attempt.
        val listed = repository.getImportJobs(ImportStatus.COMPLETED).asSnapshot().single()
        assertThat(listed.errorCode).isNull()
        assertThat(listed.nextAttemptAt).isNull()
        assertThat(listed.outcome).isEqualTo(ImportOutcome.CREATED)
    }

    @Test
    fun `a retry that fails again is failed with the new error, and nothing stored`() = runTest {
        app.fetcher.failure = FormatChanged()
        val jobId = queued(MYMARKET)
        app.runWorker(jobId)

        repository.retryImport(jobId).getOrThrow()
        // A retry starts the schedule over.
        assertThat(app.jobs.get(jobId)?.attemptCount).isEqualTo(0)
        app.fetcher.failure = IllegalStateException("bug")
        app.runWorker(jobId)

        val job = checkNotNull(app.jobs.get(jobId))
        assertThat(job.status).isEqualTo("failed")
        assertThat(job.lastErrorCode).isEqualTo("internal_error")
        assertThat(job.receiptId).isNull()
        assertThat(job.attemptCount).isEqualTo(1)
        assertThat(app.count("SELECT COUNT(*) FROM receipt")).isEqualTo(0)
        val listed = repository.getImportJobs(ImportStatus.FAILED).asSnapshot().single()
        assertThat(listed.errorCode).isEqualTo("internal_error")
        assertThat(listed.nextAttemptAt).isNull()
    }

    @Test
    fun `each status lists its own jobs, and no status lists them all`() = runTest {
        app.fetcher.failure = FormatChanged()
        val failed = queued(MYMARKET)
        app.runWorker(failed)
        app.fetcher.failure = null
        val pending = queued(METRO_SAMPLE)
        val completed = app.import(KOGIMOU)

        suspend fun ids(status: ImportStatus?) =
            repository.getImportJobs(status).asSnapshot().map { it.id }
        assertThat(ids(ImportStatus.PENDING)).isEqualTo(listOf(pending))
        assertThat(ids(ImportStatus.FAILED)).isEqualTo(listOf(failed))
        assertThat(ids(ImportStatus.COMPLETED)).isEqualTo(listOf(completed))
        assertThat(ids(null)).containsExactlyInAnyOrder(pending, failed, completed)
    }

    @Test
    fun `the importing count and the recent receipts follow a job as it moves`() = runTest {
        // Room re-emits on every change to the table; the count is what is shown.
        repository.pendingImportCount.distinctUntilChanged().test {
            assertThat(awaitItem()).isEqualTo(0)
            app.fetcher.failure = ReceiptNotFound()
            val jobId = queued(MYMARKET)
            assertThat(awaitItem()).isEqualTo(1)
            // Waiting for its next attempt it is still importing.
            app.runWorker(jobId)
            expectNoEvents()
            val waiting = repository.getImportJobs(ImportStatus.PENDING).asSnapshot().single()
            assertThat(waiting.nextAttemptAt).isNotNull()

            app.fetcher.failure = null
            repository.getRecentReceipts(3).test {
                assertThat(awaitItem()).isEqualTo(emptyList())
                app.runWorker(jobId)
                assertThat(awaitItem().single().id).isEqualTo(app.receiptOf(jobId))
            }
            assertThat(awaitItem()).isEqualTo(0)
        }
    }
}
