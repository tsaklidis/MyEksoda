package io.eksoda.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.eksoda.R
import io.eksoda.core.designsystem.theme.EksodaTheme

/**
 * What "+" opens: the three ways to add an expense (docs/design/ui-redesign.md §5). Scanning the
 * QR code comes first: it is what most people do.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddSheet(
    onDismiss: () -> Unit,
    onScan: () -> Unit,
    onByHand: () -> Unit,
    onPaste: () -> Unit,
) {
    val colors = EksodaTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.panel,
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                stringResource(R.string.add_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp).semantics {
                    heading()
                },
            )
            AddRow(Icons.Outlined.QrCodeScanner, R.string.add_scan, R.string.add_scan_hint, onScan)
            AddRow(Icons.Outlined.Edit, R.string.add_by_hand, R.string.add_by_hand_hint, onByHand)
            AddRow(Icons.Outlined.Link, R.string.add_paste, R.string.add_paste_hint, onPaste)
        }
    }
}

@Composable
private fun AddRow(icon: ImageVector, label: Int, supporting: Int, onClick: () -> Unit) {
    val colors = EksodaTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier.size(44.dp).background(colors.tile, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = colors.ink)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(label), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(supporting),
                style = MaterialTheme.typography.bodySmall,
                color = colors.ink2,
            )
        }
    }
}
