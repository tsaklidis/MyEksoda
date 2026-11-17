package io.eksoda.feature.scan

import io.eksoda.core.model.ImportSubmission

/** What the user is told after submitting a receipt link. */
sealed interface ImportFeedback {
    /** Saved and importing. [number]: how many receipts this scan session sent. */
    data class Importing(val number: Int) : ImportFeedback

    /** Saved; it imports when the phone is online. */
    data class SavedOffline(val number: Int) : ImportFeedback

    data class AlreadyImported(val receiptId: String) : ImportFeedback

    data object AlreadyPending : ImportFeedback

    /** Not a receipt link Eksoda can read; [code] is the engine's error code. */
    data class Rejected(val code: String) : ImportFeedback

    /** A QR code that is not an https link. */
    data object NotAReceipt : ImportFeedback

    /** The chosen photo has no QR code. */
    data object NoCodeInPhoto : ImportFeedback

    /** Nothing was saved (an unexpected error). */
    data object Failed : ImportFeedback
}

/** [number] is used only when the submission was queued. */
internal fun ImportSubmission.toFeedback(number: Int): ImportFeedback = when (this) {
    is ImportSubmission.Queued ->
        if (offline) ImportFeedback.SavedOffline(number) else ImportFeedback.Importing(number)

    is ImportSubmission.AlreadyImported -> ImportFeedback.AlreadyImported(receiptId)

    is ImportSubmission.AlreadyPending -> ImportFeedback.AlreadyPending

    is ImportSubmission.Rejected -> ImportFeedback.Rejected(code)
}
