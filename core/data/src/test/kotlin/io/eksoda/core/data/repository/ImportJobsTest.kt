package io.eksoda.core.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.paging.testing.asSnapshot
import androidx.test.ext.junit.runners.AndroidJUnit4
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import io.eksoda.core.data.imports.Fixtures
import io.eksoda.core.data.imports.ImportHarness
import io.eksoda.core.data.imports.ImportHarness.Companion.MYMARKET
import io.eksoda.core.model.ImportStatus
import io.eksoda.core.model.ImportSubmission
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowNetworkCapabilities
import java.math.BigDecimal
import java.time.Instant

/**
 * What the scan and imports screens are told about a link: the submission, and each job in the
 * imports list. Job rows are edited in SQL to the states a retrying or failed import is in.
 */
@RunWith(AndroidJUnit4::class)
class ImportJobsTest {
    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var app: ImportHarness
    private lateinit var jobId: String
    private lateinit var receiptId: String

    private val repository get() = app.repository
    private val db get() = app.database.openHelper.writableDatabase

    @Before
    fun setUp() = runTest {
        app = ImportHarness(folder.root)
        app.fetcher.serve(MYMARKET)
        jobId = app.import(MYMARKET)
        receiptId = app.receiptOf(jobId)
    }

    @After
    fun tearDown() {
        if (::app.isInitialized) app.close()
    }

    @Test
    fun `a link already imported opens its receipt instead of importing it again`() = runTest {
        val again = repository.importReceipt(Fixtures.url(MYMARKET)).getOrThrow()
        assertThat(again).isEqualTo(ImportSubmission.AlreadyImported(receiptId))
    }

    @Test
    fun `a queued import says whether the phone is offline`() = runTest {
        app.fetcher.serve(ImportHarness.METRO_SAMPLE)
        val manager = app.context.getSystemService(Context.CONNECTIVITY_SERVICE)
            as ConnectivityManager
        // No validated network: the import waits for one.
        shadowOf(manager).setNetworkCapabilities(manager.activeNetwork, null)
        val offline = repository.importReceipt(Fixtures.url(ImportHarness.METRO_SAMPLE))
        assertThat((offline.getOrThrow() as ImportSubmission.Queued).offline).isTrue()

        val validated = ShadowNetworkCapabilities.newInstance().also {
            shadowOf(it).addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }
        shadowOf(manager).setNetworkCapabilities(manager.activeNetwork, validated)
        app.fetcher.serve(ImportHarness.KOGIMOU)
        val online = repository.importReceipt(Fixtures.url(ImportHarness.KOGIMOU))
        assertThat((online.getOrThrow() as ImportSubmission.Queued).offline).isFalse()
    }

    @Test
    fun `a pending import with an error shows when it tries again`() = runTest {
        val next = ImportHarness.NOW + 60_000
        db.execSQL(
            "UPDATE import_job SET status = 'pending', outcome = NULL, last_error_code = " +
                "'network_error', next_attempt_at = ?, finished_at = NULL WHERE id = ?",
            arrayOf<Any>(next, jobId),
        )
        val job = repository.getImportJobs(ImportStatus.PENDING).asSnapshot().single()
        assertThat(job.errorCode).isEqualTo("network_error")
        assertThat(job.nextAttemptAt).isEqualTo(Instant.ofEpochMilli(next))
        assertThat(job.outcome).isNull()
        assertThat(job.finishedAt).isNull()
    }

    @Test
    fun `a failed or new import has no time to try again`() = runTest {
        val next = ImportHarness.NOW + 60_000
        // Failed: no next attempt, whatever the row still holds.
        db.execSQL(
            "UPDATE import_job SET status = 'failed', last_error_code = 'format_changed', " +
                "next_attempt_at = ? WHERE id = ?",
            arrayOf<Any>(next, jobId),
        )
        val failed = repository.getImportJobs(ImportStatus.FAILED).asSnapshot().single()
        assertThat(failed.nextAttemptAt).isNull()
        assertThat(failed.errorCode).isEqualTo("format_changed")
        // Pending without an error: waiting for its first run, not for a retry.
        db.execSQL(
            "UPDATE import_job SET status = 'pending', last_error_code = NULL WHERE id = ?",
            arrayOf(jobId),
        )
        val waiting = repository.getImportJobs(ImportStatus.PENDING).asSnapshot().single()
        assertThat(waiting.nextAttemptAt).isNull()
        // An unknown outcome code is no outcome.
        db.execSQL("UPDATE import_job SET outcome = 'merged' WHERE id = ?", arrayOf(jobId))
        assertThat(repository.getImportJobs(null).asSnapshot().single().outcome).isNull()
    }

    @Test
    fun `an imported job carries its receipt's merchant and total`() = runTest {
        val job = repository.getImportJobs(ImportStatus.COMPLETED).asSnapshot().single()
        assertThat(job.receiptId).isEqualTo(receiptId)
        assertThat(job.merchantName).isNotNull()
        assertThat(job.total?.amount).isEqualTo(BigDecimal("21.10"))
        assertThat(job.finishedAt).isNotNull()
    }
}
