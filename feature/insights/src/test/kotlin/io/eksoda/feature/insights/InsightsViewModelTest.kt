package io.eksoda.feature.insights

import assertk.assertThat
import assertk.assertions.isEqualTo
import io.eksoda.core.data.repository.AnalyticsRepository
import io.eksoda.core.data.repository.GoalsRepository
import io.eksoda.core.data.repository.HouseholdRepository
import io.eksoda.core.data.repository.SettingsRepository
import io.eksoda.core.model.Budget
import io.eksoda.core.model.CategoryDetail
import io.eksoda.core.model.CategorySpending
import io.eksoda.core.model.EPaymentProgress
import io.eksoda.core.model.Household
import io.eksoda.core.model.MerchantSpending
import io.eksoda.core.model.MonthBudgets
import io.eksoda.core.model.MonthPace
import io.eksoda.core.model.MonthlyTrend
import io.eksoda.core.model.Period
import io.eksoda.core.model.SpendingSummary
import io.eksoda.core.model.TopProduct
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.math.BigDecimal
import java.time.LocalDate

/** Folding a card: remembered in the settings, and harmless when they cannot be written. */
@OptIn(ExperimentalCoroutinesApi::class)
class InsightsViewModelTest {
    private val settings = FoldsOnly()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() =
        InsightsViewModel(UnusedAnalytics, UnusedGoals, UnusedHouseholds, settings)

    @Test
    fun `folding a card is remembered`() = runTest {
        viewModel().onEvent(InsightsEvent.CardToggled(InsightsCard.TREND))
        assertThat(settings.foldedInsightsCards.value).isEqualTo(setOf("TREND"))
    }

    @Test
    fun `a fold that cannot be saved leaves the card open, and the app running`() = runTest {
        settings.failWrites = true
        // An exception that escaped the view model would fail this test.
        viewModel().onEvent(InsightsEvent.CardToggled(InsightsCard.TREND))
        assertThat(settings.foldedInsightsCards.value).isEqualTo(emptySet())
    }
}

private class FoldsOnly : SettingsRepository {
    override val notifyImports: Flow<Boolean> = flowOf(false)
    override val hideContent: Flow<Boolean> = flowOf(false)
    override val foldedInsightsCards = MutableStateFlow(emptySet<String>())

    /** The settings file cannot be written (full or failing storage). */
    var failWrites = false

    override suspend fun setNotifyImports(enabled: Boolean) = error("not used")

    override suspend fun setHideContent(enabled: Boolean) = error("not used")

    override suspend fun toggleInsightsCardFolded(key: String) {
        if (failWrites) throw IOException("cannot write")
        foldedInsightsCards.value = foldedInsightsCards.value.let {
            if (key in it) it - key else it + key
        }
    }
}

/** The cards' data is not what these tests are about. */
private object UnusedAnalytics : AnalyticsRepository {
    override fun changes(): Flow<Unit> = emptyFlow()

    override suspend fun today(): LocalDate = error("not used")

    override suspend fun getSummary(period: Period): Result<SpendingSummary> = error("not used")

    override suspend fun getMonthlyTrend(months: Int): Result<MonthlyTrend> = error("not used")

    override suspend fun getSpendingByCategory(period: Period): Result<CategorySpending> =
        error("not used")

    override suspend fun getCategoryDetail(
        categoryId: String?,
        period: Period,
    ): Result<CategoryDetail> = error("not used")

    override suspend fun getSpendingByMerchant(period: Period): Result<List<MerchantSpending>> =
        error("not used")

    override suspend fun getTopProducts(
        period: Period,
        byCount: Boolean,
        limit: Int,
    ): Result<List<TopProduct>> = error("not used")

    override suspend fun getMonthPace(): Result<MonthPace> = error("not used")
}

private object UnusedGoals : GoalsRepository {
    override fun getBudgets(): Flow<List<Budget>> = emptyFlow()

    override fun categoryBudgetLimit(budgets: List<Budget>, categoryId: String): BigDecimal? =
        error("not used")

    override suspend fun setBudget(categoryId: String?, amount: BigDecimal?): Result<Unit> =
        error("not used")

    override suspend fun getMonthBudgets(spending: CategorySpending?): Result<MonthBudgets> =
        error("not used")

    override suspend fun getEPayments(): Result<EPaymentProgress> = error("not used")
}

private object UnusedHouseholds : HouseholdRepository {
    override val household: Flow<Household?> = flowOf(null)

    override suspend fun createHousehold(
        name: String,
        defaultCurrency: String,
        timezone: String,
    ): Result<Household> = error("not used")

    override suspend fun updateHousehold(
        name: String,
        defaultCurrency: String,
        timezone: String,
    ): Result<Unit> = error("not used")

    override suspend fun setAnnualIncome(income: BigDecimal?): Result<Unit> = error("not used")
}
