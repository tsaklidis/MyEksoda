package io.eksoda.core.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import io.eksoda.core.data.imports.ImportHarness
import io.eksoda.core.database.entity.BillEntity
import io.eksoda.core.model.BillKind
import io.eksoda.core.model.BillsMonth
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.YearMonth

/** Bills, fixed and variable, month by month. Today is 2026-11-22. */
@RunWith(AndroidJUnit4::class)
class BillRepositoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var app: ImportHarness
    private lateinit var bills: BillRepositoryImpl

    @Before
    fun setUp() {
        app = ImportHarness(folder.root)
        val database = app.database
        val analytics = AnalyticsRepositoryImpl(
            database,
            database.analyticsDao(),
            database.categoryDao(),
            database.householdDao(),
            HouseholdZone(database.householdDao()),
            app.clock,
            app.dispatcher,
        )
        bills = BillRepositoryImpl(
            database.billDao(),
            database.householdDao(),
            database.tombstoneDao(),
            analytics,
            app.coordinator,
            app.clock,
        )
    }

    @After
    fun tearDown() {
        if (::app.isInitialized) app.close()
    }

    private suspend fun month(month: YearMonth = NOV): BillsMonth = bills.getMonth(month).first()

    /** A bill paid since September, as if it had been added then. */
    private suspend fun sinceSeptember(kind: String, amount: Long?): String {
        app.database.billDao().upsert(
            BillEntity("b-old", "Rent", kind, amount, "2026-09", null, 0, 1, 1),
        )
        return "b-old"
    }

    @Test
    fun `a fixed bill costs its amount every month from the one it was added in`() = runTest {
        assertThat(bills.currentMonth()).isEqualTo(NOV)
        bills.add(" Rent ", BillKind.FIXED, BigDecimal("650")).getOrThrow()
        bills.add("Internet", BillKind.FIXED, BigDecimal("30")).getOrThrow()
        val november = month()
        assertThat(november.lines.map { it.bill.name }).containsExactly("Rent", "Internet")
        assertThat(november.total.toPlainString()).isEqualTo("680.00")
        assertThat(november.toEnter).isEqualTo(0)
        assertThat(november.current).isTrue()
        assertThat(november.currency).isEqualTo("EUR")
        assertThat(november.lines.all { it.editable && !it.changed }).isTrue()
        // Not before it was added.
        assertThat(month(OCT).lines).isEmpty()
    }

    @Test
    fun `a variable bill waits for this month's amount`() = runTest {
        val power = bills.add("Electricity", BillKind.VARIABLE, BigDecimal("70")).getOrThrow()
        assertThat(month().toEnter).isEqualTo(1)
        assertThat(month().lines.single().amount).isNull()
        bills.setAmount(power, NOV, BigDecimal("92.4")).getOrThrow()
        assertThat(month().lines.single().amount?.toPlainString()).isEqualTo("92.40")
        assertThat(month().toEnter).isEqualTo(0)
        // Cleared again, it waits once more and leaves a tombstone for the other phones.
        bills.setAmount(power, NOV, null).getOrThrow()
        assertThat(month().toEnter).isEqualTo(1)
        assertThat(app.count("SELECT COUNT(*) FROM tombstone WHERE entity = 'bill_month'"))
            .isEqualTo(1L)
    }

    @Test
    fun `a fixed bill can differ this month, and its usual amount takes it back`() = runTest {
        val tv = bills.add("TV", BillKind.FIXED, BigDecimal("13")).getOrThrow()
        bills.setAmount(tv, NOV, BigDecimal("18")).getOrThrow()
        val line = month().lines.single()
        assertThat(line.amount?.toPlainString()).isEqualTo("18.00")
        assertThat(line.changed).isTrue()
        bills.setAmount(tv, NOV, BigDecimal("13")).getOrThrow()
        assertThat(month().lines.single().changed).isFalse()
        assertThat(app.count("SELECT COUNT(*) FROM bill_month")).isEqualTo(0L)
    }

    @Test
    fun `past months are closed`() = runTest {
        val rent = sinceSeptember(BillEntity.FIXED, 65_000)
        val power = bills.add("Electricity", BillKind.VARIABLE, null).getOrThrow()
        assertThat(bills.setAmount(rent, OCT, BigDecimal("700")).isFailure).isTrue()
        // October is past its grace days for variable bills too.
        assertThat(bills.setAmount(power, OCT, BigDecimal("60")).isFailure).isTrue()
        assertThat(bills.setAmount(rent, NOV, BigDecimal("-1")).isFailure).isTrue()
        val october = month(OCT)
        assertThat(october.current).isFalse()
        assertThat(october.lines.single().editable).isFalse()
        assertThat(october.total.toPlainString()).isEqualTo("650.00")
    }

    @Test
    fun `a new amount leaves the past months as they were`() = runTest {
        val rent = sinceSeptember(BillEntity.FIXED, 65_000)
        bills.update(rent, "Rent", BillKind.FIXED, BigDecimal("700")).getOrThrow()
        assertThat(month(SEP).total.toPlainString()).isEqualTo("650.00")
        assertThat(month(OCT).total.toPlainString()).isEqualTo("650.00")
        assertThat(month().total.toPlainString()).isEqualTo("700.00")
        // A name change alone writes no months.
        val before = app.count("SELECT COUNT(*) FROM bill_month")
        bills.update(rent, "Home rent", BillKind.FIXED, BigDecimal("700")).getOrThrow()
        assertThat(app.count("SELECT COUNT(*) FROM bill_month")).isEqualTo(before)
        assertThat(month().lines.single().bill.name).isEqualTo("Home rent")
    }

    @Test
    fun `a removed bill stays in the months it was paid in`() = runTest {
        val rent = sinceSeptember(BillEntity.FIXED, 65_000)
        bills.remove(rent).getOrThrow()
        assertThat(month().lines).isEmpty()
        assertThat(month(OCT).total.toPlainString()).isEqualTo("650.00")

        val gym = bills.add("Gym", BillKind.FIXED, BigDecimal("40")).getOrThrow()
        bills.remove(gym).getOrThrow()
        assertThat(app.count("SELECT COUNT(*) FROM bill WHERE id = '$gym'")).isEqualTo(0L)
        assertThat(app.count("SELECT COUNT(*) FROM tombstone WHERE entity = 'bill'")).isEqualTo(1L)
    }

    @Test
    fun `wrong input is refused`() = runTest {
        assertThat(bills.add("  ", BillKind.FIXED, BigDecimal("10")).isFailure).isTrue()
        assertThat(bills.add("Rent", BillKind.FIXED, null).isFailure).isTrue()
        assertThat(bills.add("Rent", BillKind.FIXED, BigDecimal.ZERO).isFailure).isTrue()
        assertThat(bills.add("Rent", BillKind.FIXED, BigDecimal("2000000")).isFailure).isTrue()
        assertThat(bills.update("no-such-bill", "Rent", BillKind.FIXED, BigDecimal("1")).isFailure)
            .isTrue()
        assertThat(bills.setAmount("no-such-bill", NOV, BigDecimal("1")).isFailure).isTrue()
        assertThat(bills.remove("no-such-bill").isFailure).isTrue()
        assertThat(month().lines).isEmpty()
    }

    private companion object {
        val SEP: YearMonth = YearMonth.of(2026, 9)
        val OCT: YearMonth = YearMonth.of(2026, 10)
        val NOV: YearMonth = YearMonth.of(2026, 11)
    }
}
