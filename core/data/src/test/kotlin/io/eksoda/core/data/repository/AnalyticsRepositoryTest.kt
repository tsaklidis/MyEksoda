package io.eksoda.core.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isLessThan
import assertk.assertions.isNull
import assertk.assertions.isTrue
import io.eksoda.core.data.imports.ImportHarness
import io.eksoda.core.data.imports.ImportHarness.Companion.KOGIMOU
import io.eksoda.core.data.imports.ImportHarness.Companion.LIDL
import io.eksoda.core.data.imports.ImportHarness.Companion.METRO_SAMPLE
import io.eksoda.core.data.imports.ImportHarness.Companion.MYMARKET
import io.eksoda.core.model.Period
import io.eksoda.core.model.SpendingBucketKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

/**
 * Insights over four imported receipts: METRO 21.10 (Nov 21) and 9.15 (Nov 7), a grill house
 * 22.90 (Nov 20), LIDL 75.48 (Jul 13). Today is 2026-11-22.
 */
@RunWith(AndroidJUnit4::class)
class AnalyticsRepositoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var app: ImportHarness
    private lateinit var analytics: AnalyticsRepositoryImpl
    private lateinit var grillJob: String
    private val november = Period.Month(YearMonth.of(2026, 11))

    @Before
    fun setUp() = runTest {
        app = ImportHarness(folder.root)
        listOf(MYMARKET, METRO_SAMPLE, KOGIMOU).forEach(app.fetcher::serve)
        app.fetcher.serveEntersoft(LIDL)
        app.import(MYMARKET)
        app.import(METRO_SAMPLE)
        grillJob = app.import(KOGIMOU)
        app.import(LIDL)
        val database = app.database
        analytics = AnalyticsRepositoryImpl(
            database,
            database.analyticsDao(),
            database.categoryDao(),
            database.householdDao(),
            HouseholdZone(database.householdDao()),
            app.clock,
            app.dispatcher,
        )
    }

    @After
    fun tearDown() = app.close()

    @Test
    fun `the month's totals add up its receipts`() = runTest {
        assertThat(analytics.today()).isEqualTo(LocalDate.of(2026, 11, 22))
        val summary = analytics.getSummary(november).getOrThrow()
        val current = summary.current
        assertThat(current.spending.toPlainString()).isEqualTo("53.15")
        assertThat(listOf(current.receiptCount, current.salesCount)).isEqualTo(listOf(3, 3))
        // 53.15 / 3 = 17.716…
        assertThat(current.averageSale?.toPlainString()).isEqualTo("17.72")
        assertThat(current.creditNotesTotal.toPlainString()).isEqualTo("0.00")
        // The 30 days before November: no receipts.
        assertThat(summary.previous.from).isEqualTo(LocalDate.of(2026, 10, 2))
        assertThat(summary.previous.spending.toPlainString()).isEqualTo("0.00")
        assertThat(summary.previous.averageSale).isNull()
    }

    @Test
    fun `a receipt left out of the analytics is not counted`() = runTest {
        app.repository.setExcludedFromAnalytics(app.receiptOf(grillJob), true).getOrThrow()
        val current = analytics.getSummary(november).getOrThrow().current
        assertThat(current.spending.toPlainString()).isEqualTo("30.25")
        assertThat(current.receiptCount).isEqualTo(2)
    }

    @Test
    fun `the monthly trend fills in the empty months`() = runTest {
        val trend = analytics.getMonthlyTrend(6).getOrThrow()
        assertThat(trend.months.map { it.month }.first()).isEqualTo(YearMonth.of(2026, 6))
        assertThat(trend.months.map { it.total.toPlainString() })
            .isEqualTo(listOf("0.00", "75.48", "0.00", "0.00", "0.00", "53.15"))
        assertThat(trend.months.map { it.receipts }).isEqualTo(listOf(0, 1, 0, 0, 0, 3))
    }

    @Test
    fun `spending by shop puts both METRO receipts under one shop`() = runTest {
        val shops = analytics.getSpendingByMerchant(november).getOrThrow()
        val rows = shops.map {
            Triple(it.total.toPlainString(), it.receipts, it.share?.toPlainString())
        }
        assertThat(rows)
            .isEqualTo(listOf(Triple("30.25", 2, "56.91"), Triple("22.90", 1, "43.09")))
    }

    @Test
    fun `category buckets add up to the spending, and each opens to its total`() = runTest {
        val spending = analytics.getSpendingByCategory(november).getOrThrow()
        assertThat(spending.total.toPlainString()).isEqualTo("53.15")
        val sum = spending.buckets.fold(BigDecimal.ZERO) { total, bucket -> total + bucket.total }
        assertThat(sum.compareTo(spending.total)).isEqualTo(0)
        val categories = spending.buckets.filter {
            it.kind == SpendingBucketKind.CATEGORY || it.kind == SpendingBucketKind.UNCATEGORIZED
        }
        assertThat(categories.isNotEmpty()).isTrue()
        for (bucket in categories) {
            val detail = analytics.getCategoryDetail(bucket.category?.id, november).getOrThrow()
            assertThat(detail.total.compareTo(bucket.total), name = bucket.toString()).isEqualTo(0)
            val shops = detail.shops.fold(BigDecimal.ZERO) { total, shop -> total + shop.total }
            assertThat(shops.compareTo(detail.total), name = bucket.toString()).isEqualTo(0)
            assertThat(detail.previousTotal.toPlainString()).isEqualTo("0")
        }
    }

    @Test
    fun `an unknown category is an error, not a crash`() = runTest {
        assertThat(analytics.getCategoryDetail("no-such-category", november).isFailure).isTrue()
    }

    @Test
    fun `top products are the period's largest spends, by amount or by receipts`() = runTest {
        val byAmount = analytics.getTopProducts(november, byCount = false, limit = 3).getOrThrow()
        assertThat(byAmount.size).isEqualTo(3)
        val spends = byAmount.map { it.spend }
        assertThat(spends).isEqualTo(spends.sortedDescending())
        val byCount = analytics.getTopProducts(november, byCount = true, limit = 50).getOrThrow()
        assertThat(byCount.size).isLessThan(51)
        assertThat(byCount.all { it.receipts >= 1 }).isTrue()
    }

    @Test
    fun `the month pace runs day by day up to today`() = runTest {
        val pace = analytics.getMonthPace().getOrThrow()
        assertThat(pace.current.size).isEqualTo(30)
        // Nov 7: 9.15; Nov 20: + 22.90; Nov 21: + 21.10; nothing after today (Nov 22).
        assertThat(pace.current[6]?.toPlainString()).isEqualTo("9.15")
        assertThat(pace.current[19]?.toPlainString()).isEqualTo("32.05")
        assertThat(pace.current[21]?.toPlainString()).isEqualTo("53.15")
        assertThat(pace.current[22]).isNull()
        assertThat(pace.spentSoFar.toPlainString()).isEqualTo("53.15")
        // October had no receipts.
        assertThat(pace.previous.size).isEqualTo(31)
        assertThat(pace.previousBySameDay.signum()).isEqualTo(0)
        assertThat(pace.changePercent).isNull()
    }

    @Test
    fun `the screens are told about changes`() = runTest {
        assertThat(analytics.changes().first()).isEqualTo(Unit)
    }
}
