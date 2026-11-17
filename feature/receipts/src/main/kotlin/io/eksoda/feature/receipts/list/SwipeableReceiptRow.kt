package io.eksoda.feature.receipts.list

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.eksoda.core.model.Receipt
import io.eksoda.feature.receipts.R

/**
 * Swipe actions: swipe left asks to delete, swipe right includes or
 * excludes the receipt from stats. The row always springs back; the action happens elsewhere
 * (a confirmation, or a change with an Undo snackbar).
 */
@Composable
internal fun SwipeableReceiptRow(
    receipt: Receipt,
    onClick: () -> Unit,
    onDeleteRequest: () -> Unit,
    onExcludedToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentDelete = rememberUpdatedState(onDeleteRequest)
    val currentToggle = rememberUpdatedState(onExcludedToggle)
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.EndToStart -> currentDelete.value()
                SwipeToDismissBoxValue.StartToEnd -> currentToggle.value()
                SwipeToDismissBoxValue.Settled -> Unit
            }
            false
        },
    )
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        backgroundContent = {
            SwipeBackground(state.dismissDirection, receipt.excludedFromAnalytics)
        },
    ) {
        ReceiptRow(receipt, onClick)
    }
}

@Composable
private fun SwipeBackground(direction: SwipeToDismissBoxValue, excluded: Boolean) {
    if (direction == SwipeToDismissBoxValue.Settled) return
    val colors = MaterialTheme.colorScheme
    val deleting = direction == SwipeToDismissBoxValue.EndToStart
    val (background, content) = when (direction) {
        SwipeToDismissBoxValue.EndToStart -> colors.errorContainer to colors.onErrorContainer
        else -> colors.secondaryContainer to colors.onSecondaryContainer
    }
    Row(
        modifier = Modifier.fillMaxSize().background(background).padding(horizontal = 24.dp),
        horizontalArrangement = if (deleting) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val label = stringResource(
            when {
                deleting -> R.string.detail_delete
                excluded -> R.string.swipe_include
                else -> R.string.swipe_exclude
            },
        )
        Icon(
            imageVector = when {
                deleting -> Icons.Default.Delete
                excluded -> Icons.Default.Insights
                else -> Icons.Default.VisibilityOff
            },
            contentDescription = null,
            tint = content,
        )
        Text(
            text = label,
            color = content,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}
