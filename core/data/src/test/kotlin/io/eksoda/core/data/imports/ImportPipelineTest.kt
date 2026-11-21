package io.eksoda.core.data.imports

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkInfo
import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import io.eksoda.core.data.imports.ImportHarness.Companion.LIDL
import io.eksoda.core.data.imports.ImportHarness.Companion.METRO_SAMPLE
import io.eksoda.core.data.imports.ImportHarness.Companion.MYMARKET
import io.eksoda.core.data.imports.ImportHarness.Companion.NOW
import io.eksoda.core.model.ImportSubmission
import io.eksoda.core.model.MatchStatus
import io.eksoda.core.model.PaymentMethod
import io.eksoda.engine.FormatChanged
import io.eksoda.engine.InternalError
import io.eksoda.engine.ReceiptNotFound
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.time.ZoneId

/** A scanned link through the whole import: job, worker, stored receipt, what the app shows. */
@RunWith(AndroidJUnit4::class)
class ImportPipelineTest {
    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var app: ImportHarness

    @Before
    fun setUp() {
        app = ImportHarness(folder.root)
        app.fetcher.serve(MYMARKET)
    }

    @After
    fun tearDown() = app.close()

    @Test
    fun `a scanned link is queued, imported, and stored with its lines and discount`() = runTest {
        val jobId = (app.service.submit(Fixtures.url(MYMARKET)) as ImportSubmission.Queued).jobId
        // One unique run per job, waiting for a network connection.
        val runs = app.workManager.getWorkInfosForUniqueWork("import-$jobId").get()
        assertThat(runs.single().state).isEqualTo(WorkInfo.State.ENQUEUED)
        assertThat(app.repository.pendingImportCount.first()).isEqualTo(1)

        app.runWorker(jobId)
        val job = checkNotNull(app.jobs.get(jobId))
        assertThat(listOf(job.status, job.outcome)).isEqualTo(listOf("completed", "created"))
        assertThat(app.repository.pendingImportCount.first()).isEqualTo(0)

        val detail = checkNotNull(app.repository.getReceipt(app.receiptOf(jobId)).first())
        assertThat(detail.receipt.totalAmount.amount.toPlainString()).isEqualTo("21.10")
        assertThat(detail.receipt.paymentMethod).isEqualTo(PaymentMethod.CARD)
        assertThat(detail.mark).isEqualTo("400016118377990")
        assertThat(detail.providerCode).isEqualTo("impact")
        assertThat(detail.warnings).isEmpty()
        assertThat(detail.hasLink).isTrue()
        assertThat(detail.items.map { it.lineNumber }).isEqualTo((1..7).toList())
        // 1.918 kg × 7.69 = 14.75, 7.38 off, 7.37 paid: stored in fixed point.
        val chicken = detail.items.single { it.lineNumber == 7 }
        assertThat(chicken.quantity?.toPlainString()).isEqualTo("1.9180")
        assertThat(chicken.unitPrice?.amount?.toPlainString()).isEqualTo("7.6900")
        assertThat(chicken.totalPrice.amount.toPlainString()).isEqualTo("7.37")
        assertThat(chicken.matchStatus).isEqualTo(MatchStatus.MATCHED)
        assertThat(chicken.productName).isNotNull()
        assertThat(
            app.count("SELECT discount_amount_minor FROM receipt_item WHERE line_number = 7"),
        )
            .isEqualTo(738)
        assertThat(app.count("SELECT discount_total_minor FROM receipt")).isEqualTo(738)
    }

    @Test
    fun `the same receipt is stored once`() = runTest {
        val receiptId = app.receiptOf(app.import(MYMARKET))
        assertThat(app.service.submit(Fixtures.url(MYMARKET)))
            .isEqualTo(ImportSubmission.AlreadyImported(receiptId))
        // Persisting the same receipt again (another link to it) finds the stored one.
        val parsed = app.engine.read(app.engine.resolve(Fixtures.url(MYMARKET)))
        val again = app.coordinator.write {
            app.persister.persist(parsed.receipt, parsed.issues, ZoneId.of("Europe/Athens"))
        }
        assertThat(listOf(again.outcome, again.receiptId))
            .isEqualTo(listOf(Persister.OUTCOME_DUPLICATE, receiptId))
        assertThat(app.count("SELECT COUNT(*) FROM receipt")).isEqualTo(1)
        assertThat(app.count("SELECT COUNT(*) FROM receipt_item")).isEqualTo(7)
    }

    @Test
    fun `receipts from one merchant share the merchant`() = runTest {
        app.fetcher.serve(METRO_SAMPLE)
        app.import(MYMARKET)
        app.import(METRO_SAMPLE)
        val recent = app.repository.getRecentReceipts(10).first()
        assertThat(recent.size).isEqualTo(2)
        assertThat(recent.map { it.merchant }.distinct().size).isEqualTo(1)
        assertThat(app.count("SELECT COUNT(*) FROM merchant")).isEqualTo(1)
    }

    @Test
    fun `an Entersoft receipt is read from the document its page frames`() = runTest {
        app.fetcher.serveEntersoft(LIDL)
        val detail =
            checkNotNull(app.repository.getReceipt(app.receiptOf(app.import(LIDL))).first())
        assertThat(detail.providerCode).isEqualTo("entersoft")
        assertThat(detail.items.size).isEqualTo(21)
        assertThat(detail.warnings).isEmpty()
        // Its line discounts are stored with the lines.
        assertThat(app.count("SELECT COUNT(*) FROM receipt_item WHERE discount_amount_minor > 0"))
            .isGreaterThan(0)
    }

    @Test
    fun `a receipt the provider does not have yet is tried again on the schedule`() = runTest {
        app.fetcher.failure = ReceiptNotFound()
        val jobId = (app.service.submit(Fixtures.url(MYMARKET)) as ImportSubmission.Queued).jobId
        app.runWorker(jobId)
        val job = checkNotNull(app.jobs.get(jobId))
        assertThat(job.status).isEqualTo(ImportService.STATUS_PENDING)
        assertThat(job.lastErrorCode).isEqualTo(ReceiptNotFound().code.code)
        // The first retry waits 60 s, ±10%.
        val wait = checkNotNull(job.nextAttemptAt) - NOW
        assertThat(wait in 54_000L..66_000L).isTrue()
        // It is queued behind the current run.
        assertThat(app.workManager.getWorkInfosForUniqueWork("import-$jobId").get().size)
            .isEqualTo(2)

        // The receipt appears: the next attempt imports it.
        app.fetcher.failure = null
        app.runWorker(jobId)
        assertThat(app.jobs.get(jobId)?.status).isEqualTo(ImportService.STATUS_COMPLETED)
    }

    @Test
    fun `a changed page fails at once, and a retry starts the job again`() = runTest {
        app.fetcher.failure = FormatChanged()
        val jobId = (app.service.submit(Fixtures.url(MYMARKET)) as ImportSubmission.Queued).jobId
        app.runWorker(jobId)
        val failed = checkNotNull(app.jobs.get(jobId))
        assertThat(listOf(failed.status, failed.lastErrorCode))
            .isEqualTo(listOf("failed", FormatChanged().code.code))
        assertThat(failed.appVersionAtFailure).isNotNull()

        assertThat(app.repository.retryImport(jobId).isSuccess).isTrue()
        assertThat(app.jobs.get(jobId)?.status).isEqualTo(ImportService.STATUS_PENDING)
        // Only a failed job can be retried.
        assertThat(app.repository.retryImport(jobId).exceptionOrNull())
            .isNotNull()
            .isInstanceOf<IllegalStateException>()
        // Scanning the link again while it waits does not add a job.
        assertThat(app.service.submit(Fixtures.url(MYMARKET)))
            .isEqualTo(ImportSubmission.AlreadyPending(jobId))
    }

    @Test
    fun `an unexpected error is recorded as an internal error`() = runTest {
        app.fetcher.failure = IllegalStateException("bug")
        val jobId = (app.service.submit(Fixtures.url(MYMARKET)) as ImportSubmission.Queued).jobId
        app.runWorker(jobId)
        val job = checkNotNull(app.jobs.get(jobId))
        assertThat(listOf(job.status, job.lastErrorCode))
            .isEqualTo(listOf("failed", InternalError().code.code))
        assertThat(job.receiptId).isNull()
    }

    @Test
    fun `a worker for a job that is gone or done changes nothing`() = runTest {
        app.runWorker("no-such-job")
        val jobId = app.import(MYMARKET)
        app.runWorker(jobId)
        assertThat(app.count("SELECT COUNT(*) FROM receipt")).isEqualTo(1)
        assertThat(app.jobs.get(jobId)?.attemptCount).isEqualTo(1)
    }
}
