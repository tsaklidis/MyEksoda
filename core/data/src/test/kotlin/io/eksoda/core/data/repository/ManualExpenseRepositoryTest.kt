package io.eksoda.core.data.repository

import androidx.paging.testing.asSnapshot
import androidx.test.ext.junit.runners.AndroidJUnit4
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import io.eksoda.core.data.imports.ImportHarness
import io.eksoda.core.data.imports.ImportHarness.Companion.KOGIMOU
import io.eksoda.core.database.CategorySeed
import io.eksoda.core.model.BudgetLevel
import io.eksoda.core.model.IssueTime
import io.eksoda.core.model.ManualExpense
import io.eksoda.core.model.ManualKind
import io.eksoda.core.model.Option
import io.eksoda.core.model.Period
import io.eksoda.core.model.ReceiptFilter
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

/**
 * Expenses typed in by hand, next to one imported receipt (a grill house, 22.90 on 2026-11-11).
 * Today is 2026-11-22. Rent goes under "services", the doctor under "pharmacy and health".
 */
@RunWith(AndroidJUnit4::class)
class ManualExpenseRepositoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var app: ImportHarness
    private lateinit var manual: ManualExpenseRepositoryImpl
    private lateinit var analytics: AnalyticsRepositoryImpl
    private lateinit var goals: GoalsRepositoryImpl
    private val services = CategorySeed.categoryId("services")
    private val november = Period.Range(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 22))

    @Before
    fun setUp() = runTest {
        app = ImportHarness(folder.root)
        val database = app.database
        HouseholdRepositoryImpl(database.householdDao(), app.coordinator, app.clock)
            .createHousehold("Home", "EUR", "Europe/Athens").getOrThrow()
        app.fetcher.serve(KOGIMOU)
        app.import(KOGIMOU)
        val zone = HouseholdZone(database.householdDao())
        manual = ManualExpenseRepositoryImpl(
            database.receiptDao(),
            database.merchantDao(),
            database.householdDao(),
            database.categoryDao(),
            app.coordinator,
            zone,
            app.clock,
        )
        analytics = AnalyticsRepositoryImpl(
            database,
            database.analyticsDao(),
            database.categoryDao(),
            database.householdDao(),
            zone,
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
            zone,
            app.coordinator,
            app.clock,
            app.dispatcher,
        )
    }

    @After
    fun tearDown() {
        if (::app.isInitialized) app.close()
    }

    private fun rent(amount: String = "650", payee: String = "Landlord") = ManualExpense(
        amount = BigDecimal(amount),
        date = LocalDate.of(2026, 11, 5),
        kind = ManualKind.SERVICE,
        description = payee,
        categoryId = services,
        note = "November",
    )

    private suspend fun categoryTotal(categoryId: String): BigDecimal? =
        analytics.getSpendingByCategory(november).getOrThrow().buckets
            .firstOrNull { it.kind == SpendingBucketKind.CATEGORY && it.category?.id == categoryId }
            ?.total

    @Test
    fun `a manual expense counts in the month's spending and in its category`() = runTest {
        val before = analytics.getSummary(november).getOrThrow().current.spending
        manual.add(rent()).getOrThrow()
        val after = analytics.getSummary(november).getOrThrow().current.spending
        assertThat((after - before).toPlainString()).isEqualTo("650.00")
        assertThat(categoryTotal(services)?.toPlainString()).isEqualTo("650.00")
        val detail = analytics.getCategoryDetail(services, november).getOrThrow()
        assertThat(detail.unitemized.toPlainString()).isEqualTo("650.00")
        assertThat(detail.shops.map { it.name }).containsExactly("Landlord")
    }

    @Test
    fun `it shows in the list as typed in, with its category, never as from another phone`() =
        runTest {
            val id = manual.add(rent()).getOrThrow()
            val row = app.repository.getReceipts(ReceiptFilter()).asSnapshot().first { it.id == id }
            assertThat(row.manual).isTrue()
            assertThat(row.fromOtherPhone).isFalse()
            assertThat(row.categoryKey).isEqualTo("services")
            assertThat(row.itemCount).isEqualTo(0)
            assertThat(row.merchant.name).isEqualTo("Landlord")
            assertThat(row.issuedDate).isEqualTo(LocalDate.of(2026, 11, 5))
            // The category filter finds it by its own category.
            val filtered = app.repository.getReceipts(
                ReceiptFilter(category = Option(services, "services")),
            ).asSnapshot()
            assertThat(filtered.map { it.id }).containsExactly(id)
        }

    @Test
    fun `its detail has no lines, no time and no link, and keeps its category for editing`() =
        runTest {
            val id = manual.add(rent()).getOrThrow()
            val detail = checkNotNull(app.repository.getReceipt(id).first())
            assertThat(detail.hasLineItems).isFalse()
            assertThat(detail.issueTime).isEqualTo(IssueTime.DATE_ONLY)
            assertThat(detail.hasLink).isFalse()
            assertThat(detail.categoryId).isEqualTo(services)
            assertThat(detail.userNote).isEqualTo("November")
            assertThat(detail.receipt.manual).isTrue()
        }

    @Test
    fun `without a category it still counts in the month, and keeps its kind`() = runTest {
        val before = analytics.getSpendingByCategory(november).getOrThrow().total
        val id = manual.add(
            rent(amount = "35", payee = "Shoes").copy(kind = ManualKind.PRODUCT, categoryId = null),
        ).getOrThrow()
        val after = analytics.getSpendingByCategory(november).getOrThrow().total
        assertThat((after - before).toPlainString()).isEqualTo("35.00")
        assertThat(categoryTotal(services)).isNull()
        assertThat(app.count("SELECT COUNT(*) FROM receipt WHERE manual_kind = 'product'"))
            .isEqualTo(1L)
        manual.update(id, rent(amount = "35", payee = "Shoes").copy(kind = ManualKind.OTHER))
            .getOrThrow()
        assertThat(app.count("SELECT COUNT(*) FROM receipt WHERE manual_kind = 'other'"))
            .isEqualTo(1L)
        assertThat(categoryTotal(services)?.toPlainString()).isEqualTo("35.00")
    }

    @Test
    fun `a category limit counts manual expenses and goes over`() = runTest {
        goals.setCategoryLimit(services, BigDecimal("600")).getOrThrow()
        manual.add(rent()).getOrThrow()
        val budget = goals.getMonthBudgets().getOrThrow().categories
            .first { it.category?.id == services }
        assertThat(budget.level).isEqualTo(BudgetLevel.OVER)
        assertThat(budget.left.toPlainString()).isEqualTo("-50.00")
    }

    @Test
    fun `a typed-in expense never counts toward the e-payment target`() = runTest {
        val before = goals.getEPayments().getOrThrow().paid
        manual.add(rent()).getOrThrow()
        assertThat(goals.getEPayments().getOrThrow().paid).isEqualTo(before)
    }

    @Test
    fun `a category in use by a manual expense is in use`() = runTest {
        manual.add(rent()).getOrThrow()
        assertThat(app.database.categoryDao().inUse(services)).isTrue()
    }
}
