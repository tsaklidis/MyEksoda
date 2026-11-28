package io.eksoda.feature.home

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import io.eksoda.core.testing.FakeAnalyticsRepository
import io.eksoda.core.testing.FakeGoalsRepository
import io.eksoda.core.testing.FakeReceiptRepository
import io.eksoda.core.testing.MainDispatcherRule
import io.eksoda.core.testing.keepCollecting
import io.eksoda.core.testing.testCategorySpending
import io.eksoda.core.testing.testMonthBudgets
import io.eksoda.core.testing.testReceipt
import io.eksoda.core.testing.testSummary
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class HomeViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val analytics = FakeAnalyticsRepository(today = LocalDate.parse("2026-11-20"))
    private val goals = FakeGoalsRepository()
    private val receipts = FakeReceiptRepository()

    private fun viewModel() = HomeViewModel(analytics, goals, receipts)

    private fun everythingLoads() {
        analytics.summary = Result.success(testSummary(spent = "42.00", needsReview = 3))
        analytics.categories = Result.success(testCategorySpending())
    }

    @Test
    fun `home shows this month so far, what needs attention and the latest receipts`() = runTest {
        everythingLoads()
        goals.monthBudgets = Result.success(testMonthBudgets(over = 2, within = 1))
        receipts.pending.value = 1
        receipts.receipts.value = (1..5).map { testReceipt("r$it") }
        val viewModel = viewModel()
        keepCollecting(viewModel.uiState)
        val state = viewModel.uiState.value
        assertThat(state.loading).isFalse()
        assertThat(state.today).isEqualTo(LocalDate.parse("2026-11-20"))
        assertThat(state.summary?.current?.spending?.toPlainString()).isEqualTo("42.00")
        assertThat(state.needsReview).isEqualTo(3)
        assertThat(state.importing).isEqualTo(1)
        assertThat(state.budgetsOver).isEqualTo(2)
        assertThat(state.recent.map { it.id }).containsExactly("r1", "r2", "r3")
        // The month is the 1st to today.
        assertThat(analytics.periods).containsExactly(
            "summary Range(start=2026-11-01, end=2026-11-20)",
            "categories Range(start=2026-11-01, end=2026-11-20)",
        )
    }

    @Test
    fun `the budgets reuse the month's category spending instead of querying it again`() = runTest {
        everythingLoads()
        val viewModel = viewModel()
        keepCollecting(viewModel.uiState)
        assertThat(goals.spendingPassed).containsExactly(testCategorySpending())
    }

    @Test
    fun `a figure that fails is left out and said so, the rest still show`() = runTest {
        analytics.summary = Result.success(testSummary())
        // The category spending, the pace and the budgets fail.
        val viewModel = viewModel()
        keepCollecting(viewModel.uiState)
        val state = viewModel.uiState.value
        assertThat(state.failed).isTrue()
        assertThat(state.summary).isEqualTo(testSummary())
        assertThat(state.categories).isNull()
        assertThat(state.pace).isNull()
        // A budget alert that cannot be computed is not an error, only absent.
        assertThat(state.budgetsOver).isEqualTo(0)
        // The budgets were asked without the spending they would have reused.
        assertThat(goals.spendingPassed).containsExactly(null)
    }

    @Test
    fun `a change to the data or a retry loads the figures again`() = runTest {
        val viewModel = viewModel()
        keepCollecting(viewModel.uiState)
        assertThat(viewModel.uiState.value.failed).isTrue()
        everythingLoads()
        analytics.change()
        assertThat(
            viewModel.uiState.value.summary,
        ).isEqualTo(testSummary(spent = "42.00", needsReview = 3))
        val loads = analytics.loads
        viewModel.retry()
        assertThat(analytics.loads).isEqualTo(loads + 1)
    }

    @Test
    fun `the greeting follows the time of day`() {
        val greetings = mapOf(
            "04:59" to Greeting.EVENING,
            "05:00" to Greeting.MORNING,
            "11:59" to Greeting.MORNING,
            "12:00" to Greeting.DAY,
            "17:59" to Greeting.DAY,
            "18:00" to Greeting.EVENING,
            "23:59" to Greeting.EVENING,
        )
        for ((time, greeting) in greetings) {
            assertThat(greetingAt(LocalTime.parse(time)), name = time).isEqualTo(greeting)
        }
    }
}
