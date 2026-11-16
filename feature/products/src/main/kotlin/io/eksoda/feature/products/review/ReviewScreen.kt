package io.eksoda.feature.products.review

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import io.eksoda.core.designsystem.theme.CardShape
import io.eksoda.core.designsystem.theme.EksodaTheme
import io.eksoda.core.model.LineType
import io.eksoda.core.model.ProductDraft
import io.eksoda.core.model.ReviewItem
import io.eksoda.core.ui.component.DelayedProgressIndicator
import io.eksoda.core.ui.component.EmptyState
import io.eksoda.core.ui.component.MoneyText
import io.eksoda.core.ui.text.appLocale
import io.eksoda.core.ui.text.dateFormatter
import io.eksoda.core.ui.text.formatQuantity
import io.eksoda.feature.products.R
import io.eksoda.feature.products.common.ProductFormSheet
import io.eksoda.feature.products.common.ProductPickerSheet
import io.eksoda.feature.products.common.catalogErrorRes
import io.eksoda.feature.products.common.unitLabel

@Composable
fun ReviewRoute(
    onBack: () -> Unit,
    onReceiptClick: (String) -> Unit,
    viewModel: ReviewViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val items = viewModel.items.collectAsLazyPagingItems()
    val snackbarHostState = remember { SnackbarHostState() }
    // The line being matched (a product search) or turned into a new product.
    var matching by rememberSaveable { mutableStateOf<String?>(null) }
    var creating by rememberSaveable { mutableStateOf<String?>(null) }
    var creatingName by rememberSaveable { mutableStateOf("") }
    val resources = LocalResources.current
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            val message = when (effect) {
                ReviewEffect.Matched -> {
                    creating = null
                    resources.getString(R.string.review_matched)
                }

                ReviewEffect.Reclassified -> resources.getString(R.string.review_reclassified)

                is ReviewEffect.Failed -> resources.getString(catalogErrorRes(effect.error))
            }
            snackbarHostState.showSnackbar(message)
        }
    }
    ReviewScreen(
        items = items,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onReceiptClick = onReceiptClick,
        onMatchClick = { item ->
            viewModel.onEvent(ReviewEvent.PickerQueryChanged(item.description))
            matching = item.id
        },
        onCreateClick = { item ->
            creatingName = item.description
            creating = item.id
        },
        onReclassify = { item, type -> viewModel.onEvent(ReviewEvent.Reclassify(item.id, type)) },
    )
    matching?.let { itemId ->
        var query by rememberSaveable(itemId) { mutableStateOf("") }
        val pickerItems = viewModel.pickerItems.collectAsLazyPagingItems()
        ProductPickerSheet(
            title = stringResource(R.string.review_match),
            header = stringResource(R.string.review_match_hint),
            query = query,
            onQueryChange = {
                query = it
                viewModel.onEvent(ReviewEvent.PickerQueryChanged(it))
            },
            items = pickerItems,
            onPick = { product ->
                matching = null
                viewModel.onEvent(ReviewEvent.Link(itemId, product.id))
            },
            onDismiss = { matching = null },
        )
    }
    creating?.let { itemId ->
        ProductFormSheet(
            title = stringResource(R.string.product_new),
            initial = ProductDraft(name = creatingName),
            categories = state.categories,
            onSave = { viewModel.onEvent(ReviewEvent.Create(itemId, it)) },
            onDismiss = {
                creating = null
                viewModel.onEvent(ReviewEvent.FormClosed)
            },
            saving = state.saving,
            errorText = if (state.formFailed) {
                stringResource(catalogErrorRes(state.formError))
            } else {
                null
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    items: LazyPagingItems<ReviewItem>,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onReceiptClick: (String) -> Unit,
    onMatchClick: (ReviewItem) -> Unit,
    onCreateClick: (ReviewItem) -> Unit,
    onReclassify: (ReviewItem, LineType) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.review_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            val refresh = items.loadState.refresh
            when {
                refresh is LoadState.Loading && items.itemCount == 0 ->
                    DelayedProgressIndicator(modifier = Modifier.align(Alignment.Center))

                items.itemCount == 0 && refresh is LoadState.NotLoading -> EmptyState(
                    title = stringResource(R.string.review_done_title),
                    description = stringResource(R.string.review_done_body),
                    icon = Icons.Outlined.TaskAlt,
                )

                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item(key = "intro") {
                        Text(
                            text = stringResource(R.string.review_intro),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    items(count = items.itemCount, key = items.itemKey { it.id }) { index ->
                        val item = items[index] ?: return@items
                        ReviewCard(
                            item = item,
                            onReceiptClick = { onReceiptClick(item.receiptId) },
                            onMatchClick = { onMatchClick(item) },
                            onCreateClick = { onCreateClick(item) },
                            onReclassify = { onReclassify(item, it) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReviewCard(
    item: ReviewItem,
    onReceiptClick: () -> Unit,
    onMatchClick: () -> Unit,
    onCreateClick: () -> Unit,
    onReclassify: (LineType) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = appLocale()
    Card(
        onClick = onReceiptClick,
        modifier = modifier.fillMaxWidth(),
        shape = CardShape,
        colors = CardDefaults.cardColors(
            containerColor = EksodaTheme.colors.panel,
            contentColor = EksodaTheme.colors.ink,
        ),
        border = BorderStroke(1.dp, EksodaTheme.colors.line),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                MoneyText(item.grossAmount, style = MaterialTheme.typography.titleMedium)
            }
            val quantity = item.quantity?.let { formatQuantity(it, locale) }
            Text(
                text = listOfNotNull(
                    item.merchantName,
                    item.issuedAt.format(dateFormatter("d MMM yyyy", locale)),
                    quantity?.let { "$it ${unitLabel(item.unit)}" },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            item.merchantProductCode?.let {
                Text(
                    text = stringResource(R.string.review_code, it),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalButton(onClick = onMatchClick) {
                    Icon(Icons.Default.Link, contentDescription = null)
                    Text(
                        stringResource(R.string.review_match),
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
                OutlinedButton(onClick = onCreateClick) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text(
                        stringResource(R.string.review_new),
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
                NotAProductMenu(onReclassify)
            }
        }
    }
}

/** "Not a product": a discount, a fee or something else. */
@Composable
private fun NotAProductMenu(onSelect: (LineType) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) { Text(stringResource(R.string.review_not_product)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            listOf(
                LineType.DISCOUNT to R.string.line_discount,
                LineType.FEE to R.string.line_fee,
                LineType.OTHER to R.string.line_other,
            ).forEach { (type, label) ->
                DropdownMenuItem(
                    text = { Text(stringResource(label)) },
                    onClick = {
                        open = false
                        onSelect(type)
                    },
                )
            }
        }
    }
}
