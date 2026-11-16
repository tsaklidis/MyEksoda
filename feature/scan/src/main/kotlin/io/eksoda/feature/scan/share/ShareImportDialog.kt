package io.eksoda.feature.scan.share

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.eksoda.feature.scan.ImportFeedback
import io.eksoda.feature.scan.R
import io.eksoda.feature.scan.ReceiptLinks
import io.eksoda.feature.scan.feedbackText

/**
 * "Import this receipt?" for a link shared from another app (android-app.md §6.3.6). The dialog
 * names the website, never the link itself (§8.1).
 */
@Composable
fun ShareImportDialog(
    sharedText: String,
    onDismiss: () -> Unit,
    onViewReceipt: (String) -> Unit,
    viewModel: ShareImportViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val link = remember(sharedText) { ReceiptLinks.extract(sharedText) }
    val dismiss = {
        viewModel.reset()
        onDismiss()
    }

    if (link == null) {
        AlertDialog(
            onDismissRequest = dismiss,
            title = { Text(stringResource(R.string.share_no_link_title)) },
            text = { Text(stringResource(R.string.share_no_link_body)) },
            confirmButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.ok)) } },
        )
        return
    }

    when (val current = state) {
        ShareImportUiState.Confirm -> AlertDialog(
            onDismissRequest = dismiss,
            title = { Text(stringResource(R.string.share_title)) },
            text = {
                val host = ReceiptLinks.host(link)
                if (host != null) Text(stringResource(R.string.share_body, host))
            },
            confirmButton = {
                TextButton(onClick = { viewModel.import(link) }) {
                    Text(stringResource(R.string.share_import))
                }
            },
            dismissButton = {
                TextButton(onClick = dismiss) { Text(stringResource(R.string.scan_cancel)) }
            },
        )

        ShareImportUiState.Saving -> AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.share_title)) },
            text = { CircularProgressIndicator() },
            confirmButton = {},
        )

        is ShareImportUiState.Done -> AlertDialog(
            onDismissRequest = dismiss,
            text = { Text(feedbackText(current.feedback)) },
            confirmButton = {
                val feedback = current.feedback
                if (feedback is ImportFeedback.AlreadyImported) {
                    TextButton(
                        onClick = {
                            viewModel.reset()
                            onViewReceipt(feedback.receiptId)
                        },
                    ) { Text(stringResource(R.string.feedback_view)) }
                } else {
                    TextButton(onClick = dismiss) { Text(stringResource(R.string.ok)) }
                }
            },
            dismissButton = if (current.feedback is ImportFeedback.AlreadyImported) {
                { TextButton(onClick = dismiss) { Text(stringResource(R.string.ok)) } }
            } else {
                null
            },
        )
    }
}
