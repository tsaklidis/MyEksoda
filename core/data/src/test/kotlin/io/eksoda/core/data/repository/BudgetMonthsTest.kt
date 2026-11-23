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

/** Budgets month by month: a new default keeps the past, this month can differ. Today is 2026-11-22. */
@RunWith(AndroidJUnit4::class)
class BudgetMonthsTest {
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
    fun `a new default leaves the past months as they were`() = runTest {
        // A budget set before this month, when nothing kept the months apart yet.
        app.database.budgetDao().upsert(
            BudgetEntity(BudgetEntity.TOTAL_ID, null, 50_000, NOW, NOW),
        )
        goals.setEveryday(BigDecimal("700")).getOrThrow()
        // The receipts start in July: July to October keep 500.
        assertThat(app.count("SELECT COUNT(*) FROM budget WHERE month IS NOT NULL")).isEqualTo(4)
        val july = goals.getMonthBudgets(YearMonth.of(2026, 7)).getOrThrow()
        assertThat(july.current).isFalse()
        assertThat(july.elapsedPercent.compareTo(BigDecimal(100))).isEqualTo(0)
        assertThat(july.total?.budget?.toPlainString()).isEqualTo("500.00")
        assertThat(july.total?.spent?.toPlainString()).isEqualTo("75.48")
        assertThat(goals.getMonthBudgets().getOrThrow().total?.budget?.toPlainString())
            .isEqualTo("700.00")

        // The same default again writes nothing; removing it keeps the past too.
        goals.setEveryday(BigDecimal("700")).getOrThrow()
        goals.setEveryday(null).getOrThrow()
        assertThat(goals.getMonthBudgets(YearMonth.of(2026, 8)).getOrThrow().total?.budget)
            .isNotNull()
        assertThat(goals.getMonthBudgets().getOrThrow().total).isNull()
        assertThat(app.count("SELECT COUNT(*) FROM tombstone WHERE entity = 'budget'"))
            .isEqualTo(1)
    }

    @Test
    fun `a month without a budget before keeps having none`() = runTest {
        val category = checkNotNull(topCategory().category)
        goals.setCategoryLimit(category.id, BigDecimal("30")).getOrThrow()
        val october = goals.getMonthBudgets(YearMonth.of(2026, 10)).getOrThrow()
        assertThat(october.isEmpty).isTrue()
        assertThat(goals.getMonthBudgets().getOrThrow().categories).hasSize(1)
        goals.setCategoryLimit(category.id, null).getOrThrow()
        assertThat(goals.getCategoryLimit(category.id).first()).isNull()
    }

    @Test
    fun `this month can differ, then go back to the default`() = runTest {
        goals.setEveryday(BigDecimal("700")).getOrThrow()
        goals.setEverydayThisMonth(BigDecimal("850")).getOrThrow()
        goals.setEverydayThisMonth(BigDecimal("860")).getOrThrow()
        val everyday = goals.getEveryday().first()
        assertThat(everyday.changed).isTrue()
        assertThat(everyday.default?.toPlainString()).isEqualTo("700.00")
        assertThat(everyday.amount?.toPlainString()).isEqualTo("860.00")
        val month = goals.getMonthBudgets().getOrThrow()
        assertThat(month.changed).isTrue()
        assertThat(month.total?.budget?.toPlainString()).isEqualTo("860.00")
        // Changing the default keeps this month's own amount.
        goals.setEveryday(BigDecimal("650")).getOrThrow()
        assertThat(goals.getEveryday().first().amount?.toPlainString()).isEqualTo("860.00")

        goals.setEverydayThisMonth(null).getOrThrow()
        goals.setEverydayThisMonth(null).getOrThrow()
        assertThat(goals.getEveryday().first().changed).isFalse()
        assertThat(goals.getMonthBudgets().getOrThrow().total?.budget?.toPlainString())
            .isEqualTo("650.00")
        assertThat(app.count("SELECT COUNT(*) FROM tombstone WHERE id = 'total:2026-11'"))
            .isEqualTo(1)
    }
}
