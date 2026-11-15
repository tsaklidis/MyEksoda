package io.eksoda.feature.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.eksoda.core.data.repository.AnalyticsRepository
import io.eksoda.core.data.repository.HouseholdRepository
import io.eksoda.core.model.Period
import io.eksoda.core.model.SpendingSummary
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val analyticsRepository: AnalyticsRepository,
    private val householdRepository: HouseholdRepository,
    private val clock: Clock,
) : ViewModel() {

    var uiState by mutableStateOf(HomeUiState())
        private set

    init {
        loadDashboard()
    }

    fun loadDashboard() {
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true, hasError = false)
            val household = householdRepository.household.filterNotNull().first()
            val result = analyticsRepository.getSummary(Period.current(clock))
            uiState = uiState.copy(
                isLoading = false,
                householdName = household.name,
                summary = result.getOrNull() ?: uiState.summary,
                hasError = result.isFailure,
            )
        }
    }
}

data class HomeUiState(
    val summary: SpendingSummary? = null,
    val householdName: String = "",
    val isLoading: Boolean = false,
    val hasError: Boolean = false,
)
