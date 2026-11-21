package io.eksoda.core.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import io.eksoda.core.data.imports.Fixtures
import io.eksoda.core.data.imports.ImportHarness
import io.eksoda.core.data.imports.ImportHarness.Companion.MYMARKET
import io.eksoda.core.model.ImportSubmission
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

/** What the receipt screens do with a stored receipt. */
@RunWith(AndroidJUnit4::class)
class ReceiptRepositoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var app: ImportHarness
    private lateinit var jobId: String
    private lateinit var receiptId: String

    private val repository get() = app.repository

    @Before
    fun setUp() = runTest {
        app = ImportHarness(folder.root)
        app.fetcher.serve(MYMARKET)
        jobId = app.import(MYMARKET)
        receiptId = app.receiptOf(jobId)
    }

    @After
    fun tearDown() = app.close()

    private suspend fun detail() = repository.getReceipt(receiptId).first()

    @Test
    fun `a link Eksoda cannot read is rejected and stores no job`() = runTest {
        val submission = repository.importReceipt("https://shop.example.gr/receipt/1").getOrThrow()
        assertThat(submission).isEqualTo(ImportSubmission.Rejected("unsupported_provider"))
        assertThat(app.count("SELECT COUNT(*) FROM import_job")).isEqualTo(1)
    }

    @Test
    fun `a new link is queued`() = runTest {
        app.fetcher.serve(ImportHarness.METRO_SAMPLE)
        val submission = repository.importReceipt(Fixtures.url(ImportHarness.METRO_SAMPLE))
        assertThat(submission.getOrThrow()).isInstanceOf<ImportSubmission.Queued>()
        assertThat(repository.pendingImportCount.first()).isEqualTo(1)
    }

    @Test
    fun `the recent receipts show the new receipt`() = runTest {
        val recent = repository.getRecentReceipts(5).first().single()
        assertThat(recent.id).isEqualTo(receiptId)
        assertThat(recent.itemCount).isEqualTo(7)
        assertThat(recent.hasWarnings).isFalse()
        assertThat(recent.fromOtherPhone).isFalse()
    }

    @Test
    fun `a note is trimmed and kept`() = runTest {
        assertThat(repository.setNote(receiptId, "  for the office  ").isSuccess).isTrue()
        assertThat(detail()?.userNote).isEqualTo("for the office")
        assertThat(repository.setNote("no-such-receipt", "x").isFailure).isTrue()
    }

    @Test
    fun `a receipt can be left out of the analytics`() = runTest {
        assertThat(repository.setExcludedFromAnalytics(receiptId, true).isSuccess).isTrue()
        assertThat(detail()?.receipt?.excludedFromAnalytics).isEqualTo(true)
        assertThat(repository.setExcludedFromAnalytics(receiptId, false).isSuccess).isTrue()
        assertThat(detail()?.receipt?.excludedFromAnalytics).isEqualTo(false)
    }

    @Test
    fun `a receipt's store is one of its merchant's stores, or none`() = runTest {
        assertThat(detail()?.receipt?.store).isNotNull()
        assertThat(repository.setStore(receiptId, "another-merchants-store").isFailure).isTrue()
        assertThat(repository.setStore(receiptId, null).isSuccess).isTrue()
        assertThat(detail()?.receipt?.store).isNull()
    }

    @Test
    fun `the original link is the scanned one`() = runTest {
        assertThat(repository.originalLink(receiptId)).isEqualTo(Fixtures.canonicalUrl(MYMARKET))
    }

    @Test
    fun `a deleted receipt leaves a tombstone, and its link imports again`() = runTest {
        assertThat(repository.deleteReceipt(receiptId).isSuccess).isTrue()
        assertThat(detail()).isNull()
        assertThat(app.jobs.get(jobId)?.outcome).isEqualTo("receipt_deleted")
        assertThat(app.count("SELECT COUNT(*) FROM tombstone")).isEqualTo(1)
        assertThat(repository.deleteReceipt(receiptId).isFailure).isTrue()
        // Scanning it again starts the same job over.
        assertThat(app.service.submit(Fixtures.url(MYMARKET)))
            .isEqualTo(ImportSubmission.Queued(jobId))
    }
}
