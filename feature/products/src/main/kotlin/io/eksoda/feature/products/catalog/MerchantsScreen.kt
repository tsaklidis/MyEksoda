package io.eksoda.feature.products.catalog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.eksoda.core.designsystem.component.MerchantAvatar
import io.eksoda.core.model.MerchantSummary
import io.eksoda.core.ui.component.DelayedProgressIndicator
import io.eksoda.core.ui.component.EmptyState
import io.eksoda.core.ui.text.categoryName
import io.eksoda.core.ui.text.searchKey
import io.eksoda.feature.products.R

/** Every merchant, the most frequent first, searched on the device (`android-app.md` §6.8). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MerchantsRoute(
    onBack: () -> Unit,
    onMerchantClick: (String) -> Unit,
    viewModel: MerchantsViewModel = hiltViewModel(),
) {
    val merchants by viewModel.merchants.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.merchants_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            MerchantSearchField(query) { query = it }
            Box(modifier = Modifier.fillMaxSize()) {
                val list = merchants
                val key = searchKey(query)
                val shown = remember(list, key) {
                    list?.filter { key.isEmpty() || searchKey(it.name).contains(key) }
                }
                when {
                    shown == null ->
                        DelayedProgressIndicator(modifier = Modifier.align(Alignment.Center))

                    shown.isEmpty() -> EmptyState(
                        title = if (query.isBlank()) {
                            stringResource(R.string.merchants_empty)
                        } else {
                            stringResource(R.string.merchants_no_match)
                        },
                        description = null,
                        icon = Icons.Outlined.Storefront,
                    )

                    else -> LazyColumn {
                        items(shown, key = { it.id }) { merchant ->
                            MerchantRow(merchant, onClick = { onMerchantClick(merchant.id) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MerchantRow(merchant: MerchantSummary, onClick: () -> Unit) {
    val receipts = pluralStringResource(
        R.plurals.merchant_receipts,
        merchant.receiptCount,
        merchant.receiptCount,
    )
    val category = merchant.defaultCategory?.let { categoryName(it) }
    ListItem(
        headlineContent = { Text(merchant.name) },
        supportingContent = { Text(listOfNotNull(receipts, category).joinToString(" · ")) },
        leadingContent = { MerchantAvatar(merchant.id, merchant.name) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun MerchantSearchField(query: String, onChange: (String) -> Unit) {
    TextField(
        value = query,
        onValueChange = onChange,
        placeholder = { Text(stringResource(R.string.merchants_search)) },
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
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
    )
}
