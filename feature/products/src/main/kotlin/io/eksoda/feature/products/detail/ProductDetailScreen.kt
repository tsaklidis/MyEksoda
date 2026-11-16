package io.eksoda.feature.products.detail

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import io.eksoda.core.designsystem.component.OverlineText
import io.eksoda.core.designsystem.component.SectionCard
import io.eksoda.core.designsystem.component.groupedItem
import io.eksoda.core.designsystem.text.monoNumbers
import io.eksoda.core.designsystem.theme.BadgeShape
import io.eksoda.core.designsystem.theme.EksodaTheme
import io.eksoda.core.model.ComparisonReason
import io.eksoda.core.model.ProductDraft
import io.eksoda.core.ui.component.DelayedProgressIndicator
import io.eksoda.core.ui.component.EmptyState
import io.eksoda.core.ui.text.categoryName
import io.eksoda.feature.products.R
import io.eksoda.feature.products.common.ProductFormSheet
import io.eksoda.feature.products.common.ProductPickerSheet
import io.eksoda.feature.products.common.catalogErrorRes
import io.eksoda.feature.products.common.packageLabel

@Composable
fun ProductDetailRoute(
    onBack: () -> Unit,
    onReceiptClick: (String) -> Unit,
    viewModel: ProductDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var editing by rememberSaveable { mutableStateOf(false) }
    var picking by rememberSaveable { mutableStateOf(false) }
    var mergeIds by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var confirmMerge by rememberSaveable { mutableStateOf(false) }
    val resources = LocalResources.current
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            val message = when (effect) {
                ProductDetailEffect.Saved -> {
                    editing = false
                    resources.getString(R.string.product_saved)
                }

                is ProductDetailEffect.Merged -> {
                    mergeIds = emptyList()
                    resources.getQuantityString(
                        R.plurals.product_merged,
                        effect.count,
                        effect.count,
                    )
                }

                is ProductDetailEffect.Failed -> resources.getString(catalogErrorRes(effect.error))
            }
            snackbarHostState.showSnackbar(message)
        }
    }
    ProductDetailScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onEditClick = { editing = true },
        onMergeClick = {
            mergeIds = emptyList()
            picking = true
        },
        onUnitSelect = { viewModel.onEvent(ProductDetailEvent.UnitSelected(it)) },
        onReceiptClick = onReceiptClick,
    )
    val loaded = state as? ProductDetailUiState.Loaded ?: return
    if (editing) {
        val product = loaded.product
        ProductFormSheet(
            title = stringResource(R.string.product_edit),
            initial = ProductDraft(
                name = product.name,
                brand = product.brand.orEmpty(),
                categoryId = product.category?.id,
                packageQuantity = product.packageQuantity,
                packageUnit = product.packageUnit,
            ),
            categories = loaded.categories,
            onSave = { viewModel.onEvent(ProductDetailEvent.SaveRequested(it)) },
            onDismiss = {
                editing = false
                viewModel.onEvent(ProductDetailEvent.FormClosed)
            },
            warnPackageChange = true,
            saving = loaded.form.saving,
            errorText = if (loaded.form.failed) {
                stringResource(catalogErrorRes(loaded.form.error))
            } else {
                null
            },
        )
    }
    if (picking) {
        var query by rememberSaveable { mutableStateOf("") }
        val items = viewModel.pickerItems.collectAsLazyPagingItems()
        ProductPickerSheet(
            title = stringResource(R.string.merge_title),
            header = stringResource(R.string.merge_hint, loaded.product.name),
            query = query,
            onQueryChange = {
                query = it
                viewModel.onEvent(ProductDetailEvent.PickerQueryChanged(it))
            },
            items = items,
            excludeId = loaded.product.id,
            selected = mergeIds.toSet(),
            onPick = { product ->
                mergeIds = if (product.id in mergeIds) {
                    mergeIds - product.id
                } else {
                    mergeIds + product.id
                }
            },
            confirmText = stringResource(R.string.merge_action),
            onConfirm = {
                picking = false
                confirmMerge = true
            },
            onDismiss = { picking = false },
        )
    }
    if (confirmMerge) {
        AlertDialog(
            onDismissRequest = { confirmMerge = false },
            title = { Text(stringResource(R.string.merge_title)) },
            text = {
                Text(
                    pluralStringResource(
                        R.plurals.merge_confirm,
                        mergeIds.size,
                        mergeIds.size,
                        loaded.product.name,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmMerge = false
                        viewModel.onEvent(ProductDetailEvent.MergeConfirmed(mergeIds.toSet()))
                    },
                ) { Text(stringResource(R.string.merge_action)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmMerge = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

/** Product detail (`android-app.md` §6.6.2), in the redesign's layout (ui-redesign.md §5). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductDetailScreen(
    state: ProductDetailUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onEditClick: () -> Unit,
    onMergeClick: () -> Unit,
    onUnitSelect: (String) -> Unit,
    onReceiptClick: (String) -> Unit,
) {
    val loaded = state as? ProductDetailUiState.Loaded
    Scaffold(
        topBar = {
            TopAppBar(
                // The name is the header of the content (ui-redesign.md §5), not the bar's title.
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    if (loaded != null) {
                        IconButton(onClick = onEditClick) {
                            Icon(Icons.Default.Edit, stringResource(R.string.product_edit))
                        }
                        MoreMenu(onMergeClick)
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (state) {
                ProductDetailUiState.Loading ->
                    DelayedProgressIndicator(modifier = Modifier.align(Alignment.Center))

                ProductDetailUiState.Missing -> EmptyState(
                    title = stringResource(R.string.product_missing),
                    description = null,
                    icon = Icons.Outlined.Inventory2,
                    actionText = stringResource(R.string.back),
                    onActionClick = onBack,
                )

                is ProductDetailUiState.Loaded -> ProductDetailContent(
                    state = state,
                    onEditClick = onEditClick,
                    onUnitSelect = onUnitSelect,
                    onReceiptClick = onReceiptClick,
                )
            }
        }
    }
}

@Composable
private fun ProductDetailContent(
    state: ProductDetailUiState.Loaded,
    onEditClick: () -> Unit,
    onUnitSelect: (String) -> Unit,
    onReceiptClick: (String) -> Unit,
) {
    val comparison = state.comparison
    val stats = state.stats
    val unit = state.unit
    val entries = state.history.entries
    val priced = remember(entries, unit, stats?.currency) {
        if (unit != null && stats != null) {
            pricedEntries(entries, unit, stats.currency)
        } else {
            emptyList()
        }
    }
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "header") { Header(state) }
        if (stats != null && unit != null) {
            item(key = "units") { UnitSwitch(comparison.availableUnits, unit, onUnitSelect) }
            item(key = "price") { PriceCard(stats, priced) }
            item(key = "trend") { PriceTrendCard(stats, priced) }
        }
        if (comparison.reason != ComparisonReason.NO_PRICES) {
            item(key = "comparison") {
                SectionCard(title = stringResource(R.string.comparison_title)) {
                    ComparisonContent(comparison, onEditClick)
                }
            }
        }
        if (entries.isNotEmpty()) {
            item(key = "history-title") {
                Text(
                    text = stringResource(R.string.history_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .padding(top = 8.dp, start = 4.dp)
                        .semantics { heading() },
                )
            }
            // The purchases as one grouped panel, still one lazy row each.
            itemsIndexed(entries, key = { _, entry -> entry.itemId }) { index, entry ->
                PurchaseRow(
                    entry = entry,
                    thisMonth = state.thisMonth,
                    onClick = { onReceiptClick(entry.receiptId) },
                    modifier = Modifier.groupedItem(index, entries.size),
                )
            }
        } else {
            item(key = "no-history") {
                Text(
                    text = stringResource(R.string.history_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** "DAIRY AND EGGS · DELTA", then the name with its package size (§5). */
@Composable
private fun Header(state: ProductDetailUiState.Loaded) {
    val product = state.product
    val kicker = listOfNotNull(product.category?.let { categoryName(it) }, product.brand)
    val packageText = product.packageQuantity?.let { quantity ->
        product.packageUnit?.let { packageLabel(quantity, it) }
    }
    val colors = EksodaTheme.colors
    Column(
        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp, start = 4.dp, end = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (kicker.isNotEmpty()) OverlineText(kicker.joinToString(" · "))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = product.name,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f, fill = false).semantics { heading() },
            )
            if (packageText != null) {
                Text(
                    text = monoNumbers(packageText),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.ink2,
                    modifier = Modifier
                        .border(1.dp, colors.line, BadgeShape)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun MoreMenu(onMergeClick: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Default.MoreVert, stringResource(R.string.more_options))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.merge_menu)) },
                onClick = {
                    open = false
                    onMergeClick()
                },
            )
        }
    }
}
