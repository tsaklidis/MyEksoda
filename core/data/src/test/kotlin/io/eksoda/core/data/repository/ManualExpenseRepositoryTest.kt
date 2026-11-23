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
    private val health = CategorySeed.categoryId("pharmacy-and-health")
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
        payee = payee,
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
    fun `a category budget counts manual expenses and goes over`() = runTest {
        goals.setBudget(services, BigDecimal("600")).getOrThrow()
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
    fun `editing changes the amount, day, payee and category`() = runTest {
        val id = manual.add(rent()).getOrThrow()
        manual.update(
            id,
            ManualExpense(BigDecimal("80"), LocalDate.of(2026, 11, 20), "Dr. Papadopoulou", health),
        ).getOrThrow()
        assertThat(categoryTotal(services)).isNull()
        assertThat(categoryTotal(health)?.toPlainString()).isEqualTo("80.00")
        val detail = checkNotNull(app.repository.getReceipt(id).first())
        assertThat(detail.receipt.merchant.name).isEqualTo("Dr. Papadopoulou")
        assertThat(detail.receipt.issuedDate).isEqualTo(LocalDate.of(2026, 11, 20))
        assertThat(detail.userNote).isEqualTo("")
    }

    @Test
    fun `an imported receipt cannot be edited as a manual expense`() = runTest {
        val imported = app.repository.getReceipts(ReceiptFilter()).asSnapshot().single().id
        val result = manual.update(imported, rent())
        assertThat(result.exceptionOrNull()).isNotNull().isInstanceOf(IllegalStateException::class)
    }

    @Test
    fun `the same payee typed differently is one shop, and is suggested first`() = runTest {
        manual.add(rent(payee = "Landlord")).getOrThrow()
        manual.add(rent(payee = "  landlord ")).getOrThrow()
        manual.add(rent(amount = "40", payee = "Dentist")).getOrThrow()
        // Two payees, keyed by their normalized names, not three.
        assertThat(
            app.count("SELECT COUNT(*) FROM merchant WHERE vat_number LIKE 'manual:%'"),
        ).isEqualTo(2L)
        assertThat(manual.payees.first()).containsExactly("Landlord", "Dentist")
    }

    @Test
    fun `wrong input is refused and nothing is stored`() = runTest {
        val receipts = app.count("SELECT COUNT(*) FROM receipt")
        assertThat(manual.add(rent(payee = "   ")).isFailure).isTrue()
        assertThat(manual.add(rent(amount = "0")).isFailure).isTrue()
        assertThat(manual.add(rent(amount = "-5")).isFailure).isTrue()
        assertThat(manual.add(rent(amount = "20000000")).isFailure).isTrue()
        assertThat(manual.add(rent().copy(categoryId = "no-such-category")).isFailure).isTrue()
        assertThat(manual.add(rent(payee = "x".repeat(81))).isFailure).isTrue()
        assertThat(app.count("SELECT COUNT(*) FROM receipt")).isEqualTo(receipts)
    }

    @Test
    fun `amounts are kept to the cent, today's expense keeps the time it was added`() = runTest {
        val today = LocalDate.of(2026, 11, 22)
        val id = manual.add(rent(amount = "12.345").copy(date = today)).getOrThrow()
        val detail = checkNotNull(app.repository.getReceipt(id).first())
        assertThat(detail.receipt.totalAmount.amount.toPlainString()).isEqualTo("12.35")
        assertThat(detail.receipt.issuedAt.toInstant().toEpochMilli()).isEqualTo(ImportHarness.NOW)
    }

    @Test
    fun `deleting one leaves a tombstone without a natural key`() = runTest {
        val id = manual.add(rent()).getOrThrow()
        app.repository.deleteReceipt(id).getOrThrow()
        assertThat(app.count("SELECT COUNT(*) FROM receipt WHERE id = ?", id)).isEqualTo(0L)
        assertThat(
            app.text("SELECT natural_key FROM tombstone WHERE entity = 'receipt' AND id = ?", id),
        ).isNull()
        assertThat(app.count("SELECT COUNT(*) FROM tombstone WHERE id = ?", id)).isEqualTo(1L)
    }

    @Test
    fun `a category in use by a manual expense is in use`() = runTest {
        manual.add(rent()).getOrThrow()
        assertThat(app.database.categoryDao().inUse(services)).isTrue()
    }
}
