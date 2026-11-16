package io.eksoda.feature.products.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import io.eksoda.core.designsystem.component.EksodaButton
import io.eksoda.core.model.Product
import io.eksoda.feature.products.R

/**
 * Search products and pick one ([selected] null: a tap picks) or several (a tap toggles, then
 * [onConfirm]). [excludeId] hides the product being merged into.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProductPickerSheet(
    title: String,
    query: String,
    onQueryChange: (String) -> Unit,
    items: LazyPagingItems<Product>,
    onPick: (Product) -> Unit,
    onDismiss: () -> Unit,
    header: String? = null,
    excludeId: String? = null,
    selected: Set<String>? = null,
    confirmText: String? = null,
    onConfirm: () -> Unit = {},
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(SHEET_HEIGHT)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .semantics { heading() },
            )
            if (header != null) {
                Text(
                    text = header,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            PickerSearchField(query, onQueryChange)
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(count = items.itemCount, key = items.itemKey { it.id }) { index ->
                    val product = items[index] ?: return@items
                    if (product.id == excludeId) return@items
                    val checked = selected?.contains(product.id)
                    ListItem(
                        headlineContent = { Text(product.name) },
                        supportingContent = productSupporting(product)?.let { { Text(it) } },
                        leadingContent = { CategoryBadge(product.category?.slug) },
                        trailingContent = checked?.let {
                            { Checkbox(checked = it, onCheckedChange = null) }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { onPick(product) },
                    )
                }
            }
            if (selected != null && confirmText != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                    EksodaButton(
                        onClick = onConfirm,
                        text = confirmText,
                        enabled = selected.isNotEmpty(),
                    )
                }
            }
        }
    }
}

/** "Delta · bought 12 times". */
@Composable
internal fun productSupporting(product: Product): String? {
    val parts = listOfNotNull(
        product.brand,
        product.packageQuantity?.let { quantity ->
            product.packageUnit?.let { packageLabel(quantity, it) }
        },
        if (product.lineCount > 0) {
            pluralStringResource(
                R.plurals.product_bought,
                product.lineCount,
                product.lineCount,
            )
        } else {
            null
        },
    )
    return parts.joinToString(" · ").ifEmpty { null }
}

@Composable
private fun PickerSearchField(query: String, onChange: (String) -> Unit) {
    TextField(
        value = query,
        onValueChange = onChange,
        placeholder = { Text(stringResource(R.string.products_search)) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onChange("") }) {
                    Icon(Icons.Default.Close, stringResource(R.string.search_clear))
                }
            }
        },
        singleLine = true,
        shape = CircleShape,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
    )
}

private const val SHEET_HEIGHT = 0.9f
