package io.eksoda.feature.insights

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import io.eksoda.core.model.MonthPace
import io.eksoda.core.model.Period
import io.eksoda.core.model.PeriodPreset
import io.eksoda.core.testing.FakeAnalyticsRepository
import io.eksoda.core.testing.FakeGoalsRepository
import io.eksoda.core.testing.FakeHouseholdRepository
import io.eksoda.core.testing.FakeSettingsRepository
import io.eksoda.core.testing.MainDispatcherRule
import io.eksoda.core.testing.keepCollecting
import io.eksoda.core.testing.testCategorySpending
import io.eksoda.core.testing.testHousehold
import io.eksoda.core.testing.testMonthBudgets
import io.eksoda.core.testing.testSummary
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

class InsightsViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val analytics = FakeAnalyticsRepository(today = LocalDate.parse("2026-11-20")).apply {
        summary = Result.success(testSummary())
        categories = Result.success(testCategorySpending())
    }
    private val goals = FakeGoalsRepository().apply {
        monthBudgets = Result.success(testMonthBudgets(over = 1))
    }
    private val households = FakeHouseholdRepository(testHousehold())
    private val settings = FakeSettingsRepository()

    private fun viewModel() = InsightsViewModel(analytics, goals, households, settings)

    @Test
    fun `this month shows every card, each loaded or failed on its own`() = runTest {
        val viewModel = viewModel()
        keepCollecting(viewModel.uiState)
        val state = viewModel.uiState.value
        assertThat(state.range).isEqualTo(Period.Range(day("2026-11-01"), day("2026-11-20")))
        assertThat(state.summary).isEqualTo(CardState.Ready(testSummary()))
        assertThat(state.categories).isEqualTo(CardState.Ready(testCategorySpending()))
        assertThat(state.budgets).isEqualTo(CardState.Ready(testMonthBudgets(over = 1)))
        // Not set up in the fake: they fail, alone.
        assertThat(state.pace).isEqualTo(CardState.Failed)
        assertThat(state.merchants).isEqualTo(CardState.Failed)
        assertThat(state.trend).isEqualTo(CardState.Failed)
        assertThat(state.ePayments).isEqualTo(CardState.Failed)
        assertThat(state.topBySpend).isEqualTo(CardState.Failed)
        // The budgets reuse the month's category spending.
        assertThat(goals.spendingPassed).containsExactly(testCategorySpending())
    }

    @Test
    fun `another period has no pace or budgets, and its own range`() = runTest {
        val viewModel = viewModel()
        keepCollecting(viewModel.uiState)
        viewModel.onEvent(
            InsightsEvent.PeriodSelected(InsightsPeriod.Preset(PeriodPreset.LAST_MONTH)),
        )
        val state = viewModel.uiState.value
        assertThat(state.period).isEqualTo(InsightsPeriod.Preset(PeriodPreset.LAST_MONTH))
        assertThat(state.range).isEqualTo(Period.Range(day("2026-10-01"), day("2026-10-31")))
        assertThat(state.pace).isNull()
        assertThat(state.budgets).isNull()
        assertThat(state.summary).isInstanceOf<CardState.Ready<*>>()

        val custom = InsightsPeriod.Custom(day("2026-03-02"), day("2026-03-09"))
        viewModel.onEvent(InsightsEvent.PeriodSelected(custom))
        assertThat(viewModel.uiState.value.range)
            .isEqualTo(Period.Range(day("2026-03-02"), day("2026-03-09")))
    }

    @Test
    fun `a retry or a change to the data loads the cards again`() = runTest {
        val viewModel = viewModel()
        keepCollecting(viewModel.uiState)
        val loads = analytics.loads
        viewModel.onEvent(InsightsEvent.Retry)
        assertThat(analytics.loads).isEqualTo(loads + 1)
        analytics.pace = Result.success(
            MonthPace(
                currency = "EUR",
                month = YearMonth.parse("2026-11"),
                today = day("2026-11-20"),
                current = emptyList(),
                previous = emptyList(),
                previousBySameDay = BigDecimal.ZERO,
            ),
        )
        analytics.change()
        assertThat(viewModel.uiState.value.pace).isNotNull().isInstanceOf<CardState.Ready<*>>()
    }

    @Test
    fun `folding a card is remembered, and unknown stored keys are ignored`() = runTest {
        settings.folded.value = setOf("NOT_A_CARD")
        val viewModel = viewModel()
        keepCollecting(viewModel.uiState)
        assertThat(viewModel.uiState.value.folded).isEqualTo(emptySet())
        val card = InsightsCard.entries.first()
        viewModel.onEvent(InsightsEvent.CardToggled(card))
        assertThat(viewModel.uiState.value.folded).isEqualTo(setOf(card))
        viewModel.onEvent(InsightsEvent.CardToggled(card))
        assertThat(viewModel.uiState.value.folded).isEqualTo(emptySet())
    }

    @Test
    fun `the annual income is saved, and a failed save says so`() = runTest {
        val viewModel = viewModel()
        viewModel.effects.test {
            viewModel.onEvent(InsightsEvent.IncomeSet(BigDecimal("18000")))
            expectNoEvents()
            households.failure = IllegalStateException("disk full")
            viewModel.onEvent(InsightsEvent.IncomeSet(null))
            assertThat(awaitItem()).isEqualTo(InsightsEffect.IncomeSaveFailed)
        }
        assertThat(
            households.calls,
        ).containsExactly("setAnnualIncome(18000)", "setAnnualIncome(null)")
    }

    private fun day(text: String) = LocalDate.parse(text)
}
