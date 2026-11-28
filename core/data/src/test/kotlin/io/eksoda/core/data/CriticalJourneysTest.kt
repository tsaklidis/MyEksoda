package io.eksoda.core.data

import androidx.paging.testing.asSnapshot
import androidx.test.ext.junit.runners.AndroidJUnit4
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import io.eksoda.core.data.imports.Fixtures
import io.eksoda.core.data.imports.ImportHarness
import io.eksoda.core.data.imports.ImportHarness.Companion.KOGIMOU
import io.eksoda.core.data.imports.ImportHarness.Companion.MYMARKET
import io.eksoda.core.data.repository.AnalyticsRepositoryImpl
import io.eksoda.core.data.repository.HouseholdZone
import io.eksoda.core.model.ImportSubmission
import io.eksoda.core.model.IssueTime
import io.eksoda.core.model.Period
import io.eksoda.core.model.ReceiptFilter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

/**
 * What the app is for, end to end through the real pipeline (ImportService → WorkManager →
 * ImportWorker → engine → Persister → repositories) on the fixtures: a scanned receipt from
 * every provider is stored as its parser reads it, and shows, counts and goes away on the screens
 * that read it. A failure here means a user would see a wrong or missing receipt.
 */
@RunWith(AndroidJUnit4::class)
class CriticalJourneysTest {
    @get:Rule
    val folder = TemporaryFolder()

    private var apps = 0

    /** A fresh app (its own database and WorkManager) for [block], closed after it. */
    private fun <T> withApp(block: suspend (ImportHarness) -> T): T {
        val app = ImportHarness(File(folder.root, "app-${apps++}").apply { mkdirs() })
        return try {
            runBlocking { block(app) }
        } finally {
            app.close()
        }
    }

    @Test
    fun `a receipt from every provider imports through the app as its parser reads it`() {
        val fixtures = Fixtures.withExpected()
        // Every provider has at least one sample, so a new one is checked here too.
        val providers = fixtures.map { it.substringBefore('/') }.toSet()
        assertThat(providers)
            .isEqualTo(setOf("aade_fhm", "entersoft", "epsilon", "impact", "parochos", "s1ecos"))

        val mismatches = fixtures.flatMap { fixture ->
            withApp { app -> importAndCompare(app, fixture) }
        }
        assertThat(mismatches).isEqualTo(emptyList())
    }

    /** What differs between the stored receipt and [fixture]'s expected output; empty if none. */
    private suspend fun importAndCompare(app: ImportHarness, fixture: String): List<String> {
        when {
            fixture.startsWith("entersoft/") -> app.fetcher.serveEntersoft(fixture)
            Fixtures.isXml(fixture) -> app.fetcher.serveXml(fixture)
            else -> app.fetcher.serve(fixture)
        }
        val jobId = app.import(fixture)
        val job = checkNotNull(app.jobs.get(jobId))
        if (job.status != "completed" || job.outcome != "created") {
            return listOf(
                "$fixture: the import ended ${job.status}/${job.outcome} (${job.lastErrorCode})",
            )
        }
        val detail = checkNotNull(app.repository.getReceipt(checkNotNull(job.receiptId)).first())
        val expected = Json.parseToJsonElement(Fixtures.expected(fixture)).jsonObject
        val document = expected.getValue("document").jsonObject
        val gross = BigDecimal(expected.getValue("totals").jsonObject.text("gross"))
        val issueTime = when (expected.getValue("coverage").jsonObject.text("issue_time")) {
            "approximate" -> IssueTime.APPROXIMATE
            "date_only" -> IssueTime.DATE_ONLY
            else -> IssueTime.EXACT
        }
        val checks = listOf(
            "total" to (detail.receipt.totalAmount.amount.compareTo(gross) == 0),
            "lines" to (detail.items.size == expected.getValue("lines").jsonArray.size),
            "issue date" to (detail.receipt.issuedDate.toString() == document.text("issued_date")),
            "issue time" to (detail.issueTime == issueTime),
            "mark" to (detail.mark == expected.getValue("identity").jsonObject.text("mark")),
            "provider" to (detail.providerCode == fixture.substringBefore('/')),
            // A scanned receipt keeps its link on this phone, and only here.
            "link" to (detail.hasLink && !detail.receipt.fromOtherPhone),
        )
        return checks.filterNot { it.second }.map { "$fixture: ${it.first} differs" }
    }

    @Test
    fun `a receipt shows in the list, on Home and in Insights, and leaves them all when deleted`() =
        withApp { app ->
            listOf(MYMARKET, KOGIMOU).forEach(app.fetcher::serve)
            val mymarket = app.receiptOf(app.import(MYMARKET))
            app.receiptOf(app.import(KOGIMOU))
            val analytics = analytics(app)
            val november = Period.Month(YearMonth.of(2026, 11))
            suspend fun listed() =
                app.repository.getReceipts(ReceiptFilter()).asSnapshot().map { it.id }.toSet()
            suspend fun spent() =
                analytics.getSummary(november).getOrThrow().current.spending.toPlainString()

            // 21.10 + 22.90.
            assertThat(listed().size).isEqualTo(2)
            assertThat(app.repository.getRecentReceipts(3).first().size).isEqualTo(2)
            assertThat(spent()).isEqualTo("44.00")
            assertThat(analytics.getSpendingByCategory(november).getOrThrow().total.toPlainString())
                .isEqualTo("44.00")

            app.repository.deleteReceipt(mymarket).getOrThrow()
            assertThat(mymarket in listed()).isEqualTo(false)
            assertThat(app.repository.getRecentReceipts(3).first().size).isEqualTo(1)
            assertThat(spent()).isEqualTo("22.90")

            // Scanning it again brings it back, counted once.
            val again = app.repository.importReceipt(Fixtures.url(MYMARKET)).getOrThrow()
            app.runWorker((again as ImportSubmission.Queued).jobId)
            assertThat(spent()).isEqualTo("44.00")
            assertThat(app.repository.importReceipt(Fixtures.url(MYMARKET)).getOrThrow())
                .isEqualTo(ImportSubmission.AlreadyImported(app.receiptOf(again.jobId)))
        }

    @Test
    fun `a receipt left out of the stats stays in the list but not in the totals`() =
        withApp { app ->
            app.fetcher.serve(MYMARKET)
            val id = app.receiptOf(app.import(MYMARKET))
            val analytics = analytics(app)
            val today = LocalDate.of(2026, 11, 22)
            val month = Period.Range(today.withDayOfMonth(1), today)
            suspend fun counted() = analytics.getSummary(month).getOrThrow().current.receiptCount
            assertThat(counted()).isGreaterThan(0)

            app.repository.setExcludedFromAnalytics(id, true).getOrThrow()
            assertThat(counted()).isEqualTo(0)
            assertThat(analytics.getMonthPace().getOrThrow().spentSoFar.signum()).isEqualTo(0)
            val listed = app.repository.getReceipts(ReceiptFilter()).asSnapshot().single()
            assertThat(listed.excludedFromAnalytics).isEqualTo(true)
        }

    private fun analytics(app: ImportHarness) = AnalyticsRepositoryImpl(
        app.database,
        app.database.analyticsDao(),
        app.database.categoryDao(),
        app.database.householdDao(),
        HouseholdZone(app.database.householdDao()),
        app.clock,
        app.dispatcher,
    )
}

private fun JsonObject.text(key: String): String? = get(key)?.jsonPrimitive?.contentOrNull
