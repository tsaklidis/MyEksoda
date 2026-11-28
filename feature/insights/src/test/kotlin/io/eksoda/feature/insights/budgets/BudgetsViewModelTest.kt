package io.eksoda.feature.insights.budgets

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import io.eksoda.core.model.Budget
import io.eksoda.core.testing.FakeCatalogRepository
import io.eksoda.core.testing.FakeGoalsRepository
import io.eksoda.core.testing.FakeHouseholdRepository
import io.eksoda.core.testing.MainDispatcherRule
import io.eksoda.core.testing.keepCollecting
import io.eksoda.core.testing.testCategory
import io.eksoda.core.testing.testCategorySummary
import io.eksoda.core.testing.testHousehold
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal

class BudgetsViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val goals = FakeGoalsRepository()
    private val catalog = FakeCatalogRepository().apply {
        categories.value = listOf(testCategorySummary("groceries"), testCategorySummary("fuel"))
    }
    private val households = FakeHouseholdRepository(testHousehold())
    private val viewModel = BudgetsViewModel(goals, catalog, households)

    @Test
    fun `every category is listed with its budget, and the month's total apart`() = runTest {
        goals.budgets.value = listOf(
            Budget(null, BigDecimal("500")),
            Budget(testCategory("groceries"), BigDecimal("300")),
        )
        goals.limits = mapOf("groceries" to BigDecimal("500"), "fuel" to BigDecimal("200"))
        keepCollecting(viewModel.state)
        val state = viewModel.state.value
        assertThat(state.loading).isFalse()
        assertThat(state.currency).isEqualTo("EUR")
        assertThat(state.total).isEqualTo(BigDecimal("500"))
        assertThat(state.categories.map { it.category.id to it.amount }).containsExactly(
            "groceries" to BigDecimal("300"),
            "fuel" to null,
        )
        assertThat(state.categoriesTotal).isEqualTo(BigDecimal("300"))
        assertThat(state.limits).isEqualTo(goals.limits)
    }

    @Test
    fun `a budget is set or removed, and a refused one says so`() = runTest {
        viewModel.effects.test {
            viewModel.onEvent(BudgetsEvent.Set(null, BigDecimal("500")))
            viewModel.onEvent(BudgetsEvent.Set("fuel", null))
            expectNoEvents()
            goals.failure = IllegalArgumentException("category budgets exceed the month's")
            viewModel.onEvent(BudgetsEvent.Set("groceries", BigDecimal("900")))
            assertThat(awaitItem()).isEqualTo(BudgetsEffect.SaveFailed)
        }
        assertThat(goals.calls).containsExactly(
            "setBudget(null, 500)",
            "setBudget(fuel, null)",
            "setBudget(groceries, 900)",
        )
    }
}
