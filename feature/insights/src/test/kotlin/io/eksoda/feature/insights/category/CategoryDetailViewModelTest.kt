package io.eksoda.feature.insights.category

import androidx.lifecycle.SavedStateHandle
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import io.eksoda.core.model.CategoryDetail
import io.eksoda.core.testing.FakeAnalyticsRepository
import io.eksoda.core.testing.MainDispatcherRule
import io.eksoda.core.testing.keepCollecting
import io.eksoda.core.testing.testCategory
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class CategoryDetailViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val analytics = FakeAnalyticsRepository()

    private fun viewModel(categoryId: String?) = CategoryDetailViewModel(
        analytics,
        SavedStateHandle(
            mapOf(
                "categoryId" to categoryId,
                "from" to "2026-11-01",
                "to" to "2026-11-20",
            ),
        ),
    )

    @Test
    fun `the category is loaded over the period it was opened with`() = runTest {
        analytics.categoryDetail = Result.success(detail())
        val viewModel = viewModel("groceries")
        keepCollecting(viewModel.state)
        assertThat(viewModel.state.value).isEqualTo(CategoryDetailUiState.Loaded(detail()))
        assertThat(analytics.periods)
            .containsExactly("detail groceries Range(start=2026-11-01, end=2026-11-20)")
    }

    @Test
    fun `the uncategorized row asks for no category`() = runTest {
        analytics.categoryDetail = Result.success(detail())
        keepCollecting(viewModel(null).state)
        assertThat(analytics.periods)
            .containsExactly("detail null Range(start=2026-11-01, end=2026-11-20)")
    }

    @Test
    fun `a failure shows as failed, and a retry or a change loads it again`() = runTest {
        val viewModel = viewModel("groceries")
        keepCollecting(viewModel.state)
        assertThat(viewModel.state.value).isEqualTo(CategoryDetailUiState.Failed)
        analytics.categoryDetail = Result.success(detail())
        viewModel.retry()
        assertThat(viewModel.state.value).isEqualTo(CategoryDetailUiState.Loaded(detail()))
        analytics.change()
        assertThat(analytics.loads).isEqualTo(3)
    }

    private fun detail() = CategoryDetail(
        category = testCategory("groceries", system = true),
        from = LocalDate.parse("2026-11-01"),
        to = LocalDate.parse("2026-11-20"),
        currency = "EUR",
        total = BigDecimal("12.00"),
        share = null,
        previousTotal = BigDecimal.ZERO,
        previousFrom = LocalDate.parse("2026-10-12"),
        previousTo = LocalDate.parse("2026-10-31"),
        products = emptyList(),
        unitemized = BigDecimal.ZERO,
        shops = emptyList(),
    )
}
