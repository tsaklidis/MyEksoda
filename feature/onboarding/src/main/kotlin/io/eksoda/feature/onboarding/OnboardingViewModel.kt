package io.eksoda.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.eksoda.core.data.repository.HouseholdRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OnboardingUiState(
    val name: String = "",
    val currency: String = DEFAULT_CURRENCY,
    val timezone: String = DEFAULT_TIMEZONE,
    val creating: Boolean = false,
    val failed: Boolean = false,
) {
    val canCreate: Boolean get() = name.isNotBlank() && !creating

    companion object {
        const val DEFAULT_CURRENCY = "EUR"
        const val DEFAULT_TIMEZONE = "Europe/Athens"
    }
}

sealed interface OnboardingEvent {
    data class NameChanged(val name: String) : OnboardingEvent

    data object Create : OnboardingEvent
}

/**
 * Creating the household (docs/android-standalone.md §11). The app leaves onboarding by itself
 * once the household exists. Restoring a backup and the app lock come with L7.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(private val households: HouseholdRepository) : ViewModel() {
    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    fun onEvent(event: OnboardingEvent) {
        when (event) {
            is OnboardingEvent.NameChanged -> _state.update { it.copy(name = event.name, failed = false) }
            OnboardingEvent.Create -> create()
        }
    }

    private fun create() {
        val current = _state.value
        if (!current.canCreate) return
        _state.update { it.copy(creating = true, failed = false) }
        viewModelScope.launch {
            val result = households.createHousehold(current.name, current.currency, current.timezone)
            _state.update { it.copy(creating = false, failed = result.isFailure) }
        }
    }
}
