package io.eksoda.feature.household.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import dagger.hilt.android.lifecycle.HiltViewModel
import io.eksoda.core.data.repository.SyncRepository
import io.eksoda.core.model.SyncEvent
import io.eksoda.core.model.SyncJoinResult
import io.eksoda.core.model.SyncPeer
import io.eksoda.core.model.SyncStatus
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import java.security.GeneralSecurityException
import javax.inject.Inject

data class SyncUiState(
    val loading: Boolean = true,
    val enabled: Boolean = false,
    /** This phone holds a household key: it can add phones, and leave. */
    val hasKey: Boolean = false,
    val peers: List<SyncPeer> = emptyList(),
    /** Listening, phones found, syncing: while the screen is open. */
    val status: SyncStatus = SyncStatus(),
    val dialog: SyncDialog = SyncDialog.None,
    /** The camera is reading another phone's household code. */
    val scanning: Boolean = false,
)

sealed interface SyncDialog {
    data object None : SyncDialog

    /**
     * The household code, shown as a QR code. It holds the household key: it lives only while
     * the dialog is open, and never in saved state or a log line.
     */
    class Code(val text: String) : SyncDialog {
        override fun toString() = "Code(…)"
    }

    /** Joining another household while this phone has paired phones. */
    data object ConfirmJoin : SyncDialog

    data object ConfirmLeave : SyncDialog
}

sealed interface SyncScreenEvent {
    data class EnabledChanged(val enabled: Boolean) : SyncScreenEvent

    data object SyncNowClicked : SyncScreenEvent

    data object AddPhoneClicked : SyncScreenEvent

    data object JoinClicked : SyncScreenEvent

    data object JoinConfirmed : SyncScreenEvent

    /** A QR code the camera read; its text may be a household key, so it is never printed. */
    class CodeScanned(val text: String) : SyncScreenEvent {
        override fun toString() = "CodeScanned(…)"
    }

    data object ScanCancelled : SyncScreenEvent

    data object LeaveClicked : SyncScreenEvent

    data object LeaveConfirmed : SyncScreenEvent

    data object DialogDismissed : SyncScreenEvent
}

sealed interface SyncEffect {
    data class Joined(val phoneName: String) : SyncEffect

    data object NotACode : SyncEffect

    data object OwnCode : SyncEffect

    data object JoinFailed : SyncEffect

    data object CodeFailed : SyncEffect

    data object Left : SyncEffect

    data object LeaveFailed : SyncEffect

    /** Sync could not be turned on or off; it stays as it was. */
    data object SwitchFailed : SyncEffect
}

/**
 * The Sync screen: the switch, the household's phones (add, join, leave) and the history, all
 * local to this phone.
 */
@HiltViewModel
class SyncViewModel @Inject constructor(private val sync: SyncRepository) : ViewModel() {
    private val local = MutableStateFlow(Local())
    private val _effects = Channel<SyncEffect>(Channel.BUFFERED)
    val effects: Flow<SyncEffect> = _effects.receiveAsFlow()

    val state: StateFlow<SyncUiState> =
        combine(
            sync.enabled,
            sync.hasHouseholdKey,
            sync.peers,
            sync.status,
            local,
        ) { enabled, key, peers, status, ui ->
            SyncUiState(
                loading = false,
                enabled = enabled,
                hasKey = key,
                peers = peers,
                status = status,
                dialog = ui.dialog,
                scanning = ui.scanning,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), SyncUiState())

    /** Readable whether sync is on or off: the history stays after turning it off. */
    val history: Flow<PagingData<SyncEvent>> = sync.getHistory().cachedIn(viewModelScope)

    fun onEvent(event: SyncScreenEvent) {
        when (event) {
            is SyncScreenEvent.EnabledChanged -> setEnabled(event.enabled)

            // The outcome shows in the history.
            SyncScreenEvent.SyncNowClicked -> viewModelScope.launch { sync.syncNow() }

            SyncScreenEvent.AddPhoneClicked -> viewModelScope.launch {
                sync.pairingCode()
                    .onSuccess { code -> local.update { it.copy(dialog = SyncDialog.Code(code)) } }
                    .onFailure { _effects.send(SyncEffect.CodeFailed) }
            }

            SyncScreenEvent.JoinClicked -> if (state.value.peers.isEmpty()) {
                startScanning()
            } else {
                local.update { it.copy(dialog = SyncDialog.ConfirmJoin) }
            }

            SyncScreenEvent.JoinConfirmed -> startScanning()

            is SyncScreenEvent.CodeScanned -> join(event.text)

            SyncScreenEvent.ScanCancelled -> local.update { it.copy(scanning = false) }

            SyncScreenEvent.LeaveClicked ->
                local.update { it.copy(dialog = SyncDialog.ConfirmLeave) }

            SyncScreenEvent.LeaveConfirmed -> leave()

            SyncScreenEvent.DialogDismissed -> local.update { it.copy(dialog = SyncDialog.None) }
        }
    }

    /**
     * The switch. When the settings file cannot be written, or (turning sync on) the household
     * key cannot be made in the Keystore, sync stays as it was and a message says so.
     */
    private fun setEnabled(enabled: Boolean) {
        viewModelScope.launch {
            try {
                sync.setEnabled(enabled)
            } catch (ignored: IOException) {
                _effects.send(SyncEffect.SwitchFailed)
            } catch (ignored: GeneralSecurityException) {
                _effects.send(SyncEffect.SwitchFailed)
            }
        }
    }

    private fun startScanning() {
        local.update { it.copy(dialog = SyncDialog.None, scanning = true, joining = false) }
    }

    /** The camera reports one code many times a second: only the first is joined. */
    private fun join(text: String) {
        val current = local.value
        if (!current.scanning || current.joining) return
        local.value = current.copy(joining = true)
        viewModelScope.launch {
            val result = sync.join(text)
            local.update { it.copy(scanning = false, joining = false) }
            _effects.send(
                when (result) {
                    is SyncJoinResult.Joined -> SyncEffect.Joined(result.phoneName)
                    SyncJoinResult.NotACode -> SyncEffect.NotACode
                    SyncJoinResult.OwnCode -> SyncEffect.OwnCode
                    SyncJoinResult.Failed -> SyncEffect.JoinFailed
                },
            )
        }
    }

    private fun leave() {
        local.update { it.copy(dialog = SyncDialog.None) }
        viewModelScope.launch {
            _effects.send(
                if (sync.leave().isSuccess) SyncEffect.Left else SyncEffect.LeaveFailed,
            )
        }
    }

    private data class Local(
        val dialog: SyncDialog = SyncDialog.None,
        val scanning: Boolean = false,
        val joining: Boolean = false,
    )

    private companion object {
        const val STOP_TIMEOUT = 5_000L
    }
}
