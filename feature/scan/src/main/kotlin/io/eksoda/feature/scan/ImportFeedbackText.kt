package io.eksoda.feature.scan

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.eksoda.core.ui.text.importErrorRes

/** The chip text for a submission result. */
@Composable
internal fun feedbackText(feedback: ImportFeedback): String = when (feedback) {
    is ImportFeedback.Importing -> stringResource(R.string.feedback_importing, feedback.number)

    is ImportFeedback.SavedOffline ->
        stringResource(R.string.feedback_saved_offline, feedback.number)

    is ImportFeedback.AlreadyImported -> stringResource(R.string.feedback_already_imported)

    ImportFeedback.AlreadyPending -> stringResource(R.string.feedback_already_pending)

    is ImportFeedback.Rejected -> stringResource(importErrorRes(feedback.code))

    ImportFeedback.NotAReceipt -> stringResource(R.string.feedback_not_a_receipt)

    ImportFeedback.NoCodeInPhoto -> stringResource(R.string.feedback_no_code_in_photo)

    ImportFeedback.Failed -> stringResource(R.string.feedback_failed)
}

internal val ImportFeedback.isError: Boolean
    get() = this is ImportFeedback.Rejected ||
        this == ImportFeedback.NotAReceipt ||
        this == ImportFeedback.NoCodeInPhoto ||
        this == ImportFeedback.Failed
