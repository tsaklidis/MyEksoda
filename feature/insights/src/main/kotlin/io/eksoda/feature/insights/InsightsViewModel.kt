package io.eksoda.feature.insights

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.eksoda.core.data.repository.AnalyticsRepository
import io.eksoda.core.model.CategorySpending
import io.eksoda.core.model.MonthlyTrend
import io.eksoda.core.model.Period
import io.eksoda.core.model.SpendingSummary
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

@HiltViewModel
class InsightsViewModel @Inject constructor(
    private val analyticsRepository: AnalyticsRepository,
    clock: Clock,
) : ViewModel() {

    var uiState by mutableStateOf(InsightsUiState(selectedPeriod = Period.current(clock)))
        private set

    init {
        loadInsights()
    }

    fun onPeriodChange(period: Period) {
        uiState = uiState.copy(selectedPeriod = period)
        loadInsights()
    }

    fun loadInsights() {
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true, hasError = false)
            val period = uiState.selectedPeriod
            val summary = async { analyticsRepository.getSummary(period) }
            val categories = async { analyticsRepository.getSpendingByCategory(period) }
            val trend = async { analyticsRepository.getMonthlyTrend(TREND_MONTHS) }
            val results = listOf(summary.await(), categories.await(), trend.await())
            uiState = uiState.copy(
                isLoading = false,
                summary = summary.await().getOrNull(),
                categorySpending = categories.await().getOrNull(),
                trend = trend.await().getOrNull(),
                hasError = results.any { it.isFailure },
            )
        }
    }

    private companion object {
        const val TREND_MONTHS = 12
    }
}

data class InsightsUiState(
    val selectedPeriod: Period,
    val summary: SpendingSummary? = null,
    val categorySpending: CategorySpending? = null,
    val trend: MonthlyTrend? = null,
    val isLoading: Boolean = false,
    val hasError: Boolean = false,
)
