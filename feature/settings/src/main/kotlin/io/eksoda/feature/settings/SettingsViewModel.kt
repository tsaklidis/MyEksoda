package io.eksoda.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.eksoda.core.data.repository.HouseholdRepository
import io.eksoda.core.data.repository.SettingsRepository
import io.eksoda.core.model.Household
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(val household: Household? = null, val notifyImports: Boolean = false)

sealed interface SettingsEvent {
    data class NotifyImportsChanged(val enabled: Boolean) : SettingsEvent
}

/** Settings (`android-app.md` §6.10): language, notifications, the household, catalog links. */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    households: HouseholdRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    val uiState: StateFlow<SettingsUiState> =
        combine(households.household, settings.notifyImports) { household, notify ->
            SettingsUiState(household = household, notifyImports = notify)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), SettingsUiState())

    fun onEvent(event: SettingsEvent) {
        when (event) {
            is SettingsEvent.NotifyImportsChanged ->
                viewModelScope.launch { settings.setNotifyImports(event.enabled) }
        }
    }

    private companion object {
        const val STOP_TIMEOUT = 5_000L
    }
}
