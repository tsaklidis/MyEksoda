package io.eksoda.feature.scan.share

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.eksoda.core.designsystem.theme.EksodaTheme
import io.eksoda.feature.scan.R
import io.eksoda.feature.scan.ReceiptLinks

/**
 * "Paste a link" from the Add sheet: a field with the receipt link (filled from the clipboard
 * when it holds one), then the same import as a link shared from another app
 * ([ShareImportDialog]). The link itself is never kept anywhere but the field.
 */
@Composable
fun PasteLinkImportDialog(onDismiss: () -> Unit, onViewReceipt: (String) -> Unit) {
    val clipboard = LocalClipboardManager.current
    var text by remember {
        mutableStateOf(clipboard.getText()?.text?.let(ReceiptLinks::extract).orEmpty())
    }
    var submitted by remember { mutableStateOf<String?>(null) }
    val link = submitted
    if (link != null) {
        ShareImportDialog(sharedText = link, onDismiss = onDismiss, onViewReceipt = onViewReceipt)
        return
    }
    val notALink = text.isNotBlank() && ReceiptLinks.extract(text) == null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.paste_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.paste_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = EksodaTheme.colors.ink2,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.paste_label)) },
                    singleLine = true,
                    isError = notALink,
                    supportingText = if (notALink) {
                        { Text(stringResource(R.string.paste_not_a_link)) }
                    } else {
                        null
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { submitted = text },
                enabled = text.isNotBlank() && !notALink,
            ) { Text(stringResource(R.string.paste_import)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.scan_cancel)) }
        },
    )
}
