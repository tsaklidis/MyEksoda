package io.eksoda.feature.home.bills

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import io.eksoda.core.data.repository.BillRepository
import io.eksoda.core.model.BillKind
import io.eksoda.core.model.BillsMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.YearMonth

/** The bills screen: which month it shows, and what its changes ask of the repository. */
@OptIn(ExperimentalCoroutinesApi::class)
class BillsViewModelTest {
    private val bills = FakeBills()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `it opens on the current month, with the three before it to look back on`() = runTest {
        val viewModel = BillsViewModel(bills)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.state.collect {}
        }
        val state = viewModel.state.value
        assertThat(state.loading).isFalse()
        assertThat(state.month?.month).isEqualTo(NOV)
        assertThat(state.months).containsExactly(
            YearMonth.of(2026, 8),
            YearMonth.of(2026, 9),
            YearMonth.of(2026, 10),
            NOV,
        )
    }

    @Test
    fun `an amount is set in the month shown`() = runTest {
        val viewModel = BillsViewModel(bills)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.state.collect {}
        }
        viewModel.onEvent(BillsEvent.MonthPicked(OCT))
        assertThat(viewModel.state.value.month?.month).isEqualTo(OCT)
        viewModel.onEvent(BillsEvent.SetAmount("b1", BigDecimal("61")))
        assertThat(bills.amounts).containsExactly(Triple("b1", OCT, BigDecimal("61")))
    }

    @Test
    fun `a refused change is told, not swallowed`() = runTest {
        bills.fail = true
        val viewModel = BillsViewModel(bills)
        viewModel.onEvent(BillsEvent.Add(BillDraft("Rent", BillKind.FIXED, BigDecimal("650"))))
        assertThat(viewModel.effects.first()).isEqualTo(BillsEffect.SaveFailed)
        viewModel.onEvent(BillsEvent.Remove("b1"))
        assertThat(viewModel.effects.first()).isEqualTo(BillsEffect.SaveFailed)
    }

    private class FakeBills : BillRepository {
        var fail = false
        val amounts = mutableListOf<Triple<String, YearMonth, BigDecimal?>>()

        private fun result(): Result<Unit> =
            if (fail) Result.failure(IllegalStateException("refused")) else Result.success(Unit)

        override fun getMonth(month: YearMonth): Flow<BillsMonth> =
            flowOf(BillsMonth("EUR", month, emptyList(), BigDecimal.ZERO, 0, month == NOV))

        override suspend fun currentMonth(): YearMonth = NOV

        override suspend fun add(
            name: String,
            kind: BillKind,
            amount: BigDecimal?,
        ): Result<String> = result().map { "b2" }

        override suspend fun update(
            id: String,
            name: String,
            kind: BillKind,
            amount: BigDecimal?,
        ): Result<Unit> = result()

        override suspend fun setAmount(
            id: String,
            month: YearMonth,
            amount: BigDecimal?,
        ): Result<Unit> {
            amounts += Triple(id, month, amount)
            return result()
        }

        override suspend fun remove(id: String): Result<Unit> = result()
    }

    private companion object {
        val OCT: YearMonth = YearMonth.of(2026, 10)
        val NOV: YearMonth = YearMonth.of(2026, 11)
    }
}
