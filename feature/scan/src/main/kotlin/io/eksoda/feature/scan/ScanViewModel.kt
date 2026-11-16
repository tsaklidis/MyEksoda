package io.eksoda.feature.scan

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.eksoda.core.data.repository.ReceiptRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ScanUiState(
    /** The latest result, shown as a chip above the bottom bar. */
    val feedback: ImportFeedback? = null,
    /** Receipts saved in this session, for "Done (3)". */
    val sentCount: Int = 0,
    val torchOn: Boolean = false,
    val pasteOpen: Boolean = false,
    val readingPhoto: Boolean = false,
)

sealed interface ScanEvent {
    data class CodeScanned(val text: String) : ScanEvent

    data object TorchToggled : ScanEvent

    data object PasteOpened : ScanEvent

    data object PasteDismissed : ScanEvent

    data class LinkPasted(val text: String) : ScanEvent

    data class PhotoPicked(val uri: Uri) : ScanEvent

    data object FeedbackShown : ScanEvent
}

sealed interface ScanEffect {
    /** A light tick when a receipt is saved (android-app.md §6.3.1). */
    data object Saved : ScanEffect
}

/**
 * Continuous scanning (android-app.md §6.3, docs/android-standalone.md §7.1). Each new code is
 * submitted at once and the camera keeps scanning; the same code again in this session is
 * ignored. Links stay in memory only and are never logged (§8.1).
 */
@HiltViewModel
class ScanViewModel @Inject constructor(
    private val receipts: ReceiptRepository,
    private val photoDecoder: QrImageDecoder,
) : ViewModel() {
    private val _state = MutableStateFlow(ScanUiState())
    val state: StateFlow<ScanUiState> = _state.asStateFlow()

    private val _effects = Channel<ScanEffect>(Channel.BUFFERED)
    val effects: Flow<ScanEffect> = _effects.receiveAsFlow()

    /** Codes seen in this session: the camera reports the same code many times a second. */
    private val seen = mutableSetOf<String>()

    fun onEvent(event: ScanEvent) {
        when (event) {
            is ScanEvent.CodeScanned -> onCode(event.text)
            ScanEvent.TorchToggled -> _state.update { it.copy(torchOn = !it.torchOn) }
            ScanEvent.PasteOpened -> _state.update { it.copy(pasteOpen = true) }
            ScanEvent.PasteDismissed -> _state.update { it.copy(pasteOpen = false) }
            is ScanEvent.LinkPasted -> onPasted(event.text)
            is ScanEvent.PhotoPicked -> onPhoto(event.uri)
            ScanEvent.FeedbackShown -> _state.update { it.copy(feedback = null) }
        }
    }

    private fun onCode(text: String) {
        if (!seen.add(text)) return
        if (!ReceiptLinks.isHttpsLink(text)) {
            show(ImportFeedback.NotAReceipt)
            return
        }
        submit(text, forgetOnFailure = true)
    }

    private fun onPasted(text: String) {
        _state.update { it.copy(pasteOpen = false) }
        val link = ReceiptLinks.extract(text)
        if (link == null) {
            show(ImportFeedback.Rejected(INVALID_URL))
        } else {
            seen += link
            submit(link, forgetOnFailure = false)
        }
    }

    private fun onPhoto(uri: Uri) {
        _state.update { it.copy(readingPhoto = true) }
        viewModelScope.launch {
            val text = runCatching { photoDecoder.decode(uri) }.getOrNull()
            _state.update { it.copy(readingPhoto = false) }
            when {
                text == null -> show(ImportFeedback.NoCodeInPhoto)

                ReceiptLinks.isHttpsLink(text) -> {
                    seen += text
                    submit(text, forgetOnFailure = false)
                }

                else -> show(ImportFeedback.NotAReceipt)
            }
        }
    }

    /** [forgetOnFailure]: after an unexpected error, scanning the code again retries it. */
    private fun submit(link: String, forgetOnFailure: Boolean) {
        viewModelScope.launch {
            val result = receipts.importReceipt(link)
            val submission = result.getOrNull()
            if (submission == null) {
                if (forgetOnFailure) seen -= link
                show(ImportFeedback.Failed)
                return@launch
            }
            val feedback = submission.toFeedback(number = _state.value.sentCount + 1)
            val saved = feedback is ImportFeedback.Importing ||
                feedback is ImportFeedback.SavedOffline
            _state.update {
                it.copy(feedback = feedback, sentCount = it.sentCount + if (saved) 1 else 0)
            }
            if (saved) _effects.send(ScanEffect.Saved)
        }
    }

    private fun show(feedback: ImportFeedback) {
        _state.update { it.copy(feedback = feedback) }
    }

    private companion object {
        const val INVALID_URL = "invalid_url"
    }
}
