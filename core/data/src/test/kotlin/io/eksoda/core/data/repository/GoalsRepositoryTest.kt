package io.eksoda.core.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import io.eksoda.core.data.imports.ImportHarness
import io.eksoda.core.data.imports.ImportHarness.Companion.KOGIMOU
import io.eksoda.core.data.imports.ImportHarness.Companion.LIDL
import io.eksoda.core.data.imports.ImportHarness.Companion.METRO_SAMPLE
import io.eksoda.core.data.imports.ImportHarness.Companion.MYMARKET
import io.eksoda.core.database.entity.BudgetEntity
import io.eksoda.core.model.BudgetLevel
import io.eksoda.core.model.Period
import io.eksoda.core.model.SpendingBucket
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
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth

/**
 * Budgets, limits and the e-payment target over four imported receipts, all paid by card: 53.15 in
 * November (METRO 21.10 and 9.15, a grill house 22.90) and LIDL 75.48 in July. Today is
 * 2026-11-22.
 */
@RunWith(AndroidJUnit4::class)
class GoalsRepositoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var app: ImportHarness
    private lateinit var analytics: AnalyticsRepositoryImpl
    private lateinit var goals: GoalsRepositoryImpl
    private lateinit var households: HouseholdRepositoryImpl
    private lateinit var grillJob: String
    private val monthSoFar = Period.Range(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 22))

    private companion object {
        val NOV: YearMonth = YearMonth.of(2026, 11)
        const val NOW = 1L
    }

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
        goals = GoalsRepositoryImpl(
            database.budgetDao(),
            database.categoryDao(),
            database.householdDao(),
            database.tombstoneDao(),
            database.analyticsDao(),
            analytics,
            HouseholdZone(database.householdDao()),
            app.coordinator,
            app.clock,
            app.dispatcher,
        )
        households = HouseholdRepositoryImpl(database.householdDao(), app.coordinator, app.clock)
    }

    @After
    fun tearDown() {
        // A failed setUp keeps its own error.
        if (::app.isInitialized) app.close()
    }

    /** The category this month's spending has the most of. */
    private suspend fun topCategory(): SpendingBucket =
        analytics.getSpendingByCategory(monthSoFar).getOrThrow().buckets
            .filter { it.kind == SpendingBucketKind.CATEGORY && it.category != null }
            .maxBy { it.total }

    @Test
    fun `without budgets the month has none, in the default currency`() = runTest {
        val month = goals.getMonthBudgets().getOrThrow()
        assertThat(month.isEmpty).isTrue()
        assertThat(month.currency).isEqualTo("EUR")
        assertThat(month.month).isEqualTo(NOV)
        assertThat(month.current).isTrue()
        assertThat(month.changed).isFalse()
        assertThat(month.overCount).isEqualTo(0)
        val everyday = goals.getEveryday().first()
        assertThat(everyday.default).isNull()
        assertThat(everyday.changed).isFalse()
        assertThat(everyday.amount).isNull()
    }

    @Test
    fun `the everyday budget is near once 80 percent of it is spent`() = runTest {
        goals.setEveryday(BigDecimal("60")).getOrThrow()
        val total = checkNotNull(goals.getMonthBudgets().getOrThrow().total)
        assertThat(total.spent.toPlainString()).isEqualTo("53.15")
        assertThat(total.left.toPlainString()).isEqualTo("6.85")
        assertThat(total.level).isEqualTo(BudgetLevel.NEAR)
        assertThat(total.category).isNull()
        assertThat(goals.getEveryday().first().amount?.toPlainString()).isEqualTo("60.00")
    }

    @Test
    fun `a category limit counts its spending, can be passed, and need not fit the budget`() =
        runTest {
            val top = topCategory()
            val category = checkNotNull(top.category)
            val half = top.total.divide(BigDecimal(2)).setScale(2, RoundingMode.DOWN)
            goals.setEveryday(BigDecimal("20")).getOrThrow()
            goals.setCategoryLimit(category.id, half).getOrThrow()
            // A limit above the everyday budget is fine: limits are set on their own.
            goals.setCategoryLimit(category.id, BigDecimal("500")).getOrThrow()
            goals.setCategoryLimit(category.id, half).getOrThrow()
            assertThat(goals.getCategoryLimit(category.id).first()?.compareTo(half)).isEqualTo(0)

            // The month's spending known already is used as it is.
            val spending = analytics.getSpendingByCategory(monthSoFar).getOrThrow()
            val month = goals.getMonthBudgets(spending = spending).getOrThrow()
            val progress = month.categories.single()
            assertThat(progress.category?.id).isEqualTo(category.id)
            assertThat(progress.spent.compareTo(top.total)).isEqualTo(0)
            assertThat(progress.level).isEqualTo(BudgetLevel.OVER)
            assertThat(month.overCount).isEqualTo(2)
        }

    @Test
    fun `budgets must be above 0, and a limit needs its category`() = runTest {
        assertThat(goals.setEveryday(BigDecimal.ZERO).exceptionOrNull())
            .isNotNull().isInstanceOf<IllegalArgumentException>()
        assertThat(goals.setEverydayThisMonth(BigDecimal("-1")).isFailure).isTrue()
        assertThat(goals.setCategoryLimit("no-such-category", BigDecimal("5")).isFailure).isTrue()
        assertThat(goals.getMonthBudgets().getOrThrow().isEmpty).isTrue()
    }

    @Test
    fun `without an income the year's card payments have no target`() = runTest {
        val progress = goals.getEPayments().getOrThrow()
        assertThat(progress.year).isEqualTo(2026)
        assertThat(progress.currency).isEqualTo("EUR")
        assertThat(progress.paid.toPlainString()).isEqualTo("128.63")
        assertThat(progress.income).isNull()
        assertThat(progress.target).isNull()
        assertThat(progress.remaining).isNull()
        assertThat(progress.perMonth).isNull()
        assertThat(progress.paidPercent).isNull()
        assertThat(progress.sharePercent.compareTo(BigDecimal(30))).isEqualTo(0)
    }

    @Test
    fun `the target is 30 percent of the income, less what was paid by card`() = runTest {
        households.createHousehold("Home", "EUR", "Europe/Athens").getOrThrow()
        households.setAnnualIncome(BigDecimal("20000")).getOrThrow()
        val progress = goals.getEPayments().getOrThrow()
        assertThat(progress.income?.toPlainString()).isEqualTo("20000.00")
        assertThat(progress.target?.toPlainString()).isEqualTo("6000.00")
        assertThat(progress.remaining?.toPlainString()).isEqualTo("5871.37")
        assertThat(progress.perMonth).isNotNull()
        assertThat(progress.paidPercent).isNotNull()
    }

    @Test
    fun `a receipt paid in several ways counts with its card part`() = runTest {
        val grill = app.receiptOf(grillJob)
        app.database.openHelper.writableDatabase.execSQL(
            "UPDATE receipt SET payment_method = 'mixed' WHERE id = ?",
            arrayOf(grill),
        )
        // Its one payment is by card: the whole 22.90 still counts.
        assertThat(goals.getEPayments().getOrThrow().paid.toPlainString()).isEqualTo("128.63")
        app.repository.setExcludedFromAnalytics(grill, true).getOrThrow()
        assertThat(goals.getEPayments().getOrThrow().paid.toPlainString()).isEqualTo("105.73")
    }
}
