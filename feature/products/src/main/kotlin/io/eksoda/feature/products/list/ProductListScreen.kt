package io.eksoda.feature.products.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.RateReview
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import io.eksoda.core.designsystem.theme.EksodaTheme
import io.eksoda.core.model.Category
import io.eksoda.core.model.Merchant
import io.eksoda.core.model.Product
import io.eksoda.core.model.ProductDraft
import io.eksoda.core.model.ProductOrdering
import io.eksoda.core.ui.component.DelayedProgressIndicator
import io.eksoda.core.ui.component.EmptyState
import io.eksoda.core.ui.text.categoryName
import io.eksoda.feature.products.R
import io.eksoda.feature.products.common.CategoryBadge
import io.eksoda.feature.products.common.ProductFormSheet
import io.eksoda.feature.products.common.catalogErrorRes
import io.eksoda.feature.products.common.productSupporting

@Composable
fun ProductListRoute(
    onProductClick: (String) -> Unit,
    onReviewClick: () -> Unit,
    onCategoriesClick: () -> Unit,
    onMerchantsClick: () -> Unit,
    viewModel: ProductListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val items = viewModel.items.collectAsLazyPagingItems()
    var creating by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ProductListEffect.Created -> {
                    creating = false
                    onProductClick(effect.productId)
                }
            }
        }
    }
    ProductListScreen(
        state = state,
        items = items,
        onEvent = viewModel::onEvent,
        onProductClick = onProductClick,
        onReviewClick = onReviewClick,
        onCategoriesClick = onCategoriesClick,
        onMerchantsClick = onMerchantsClick,
        onCreateClick = { creating = true },
    )
    if (creating) {
        ProductFormSheet(
            title = stringResource(R.string.product_new),
            initial = ProductDraft(name = ""),
            categories = state.categories,
            onSave = { viewModel.onEvent(ProductListEvent.CreateRequested(it)) },
            onDismiss = {
                creating = false
                viewModel.onEvent(ProductListEvent.FormClosed)
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

/** Every product, with price history one tap away. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductListScreen(
    state: ProductListUiState,
    items: LazyPagingItems<Product>,
    onEvent: (ProductListEvent) -> Unit,
    onProductClick: (String) -> Unit,
    onReviewClick: () -> Unit,
    onCategoriesClick: () -> Unit,
    onMerchantsClick: () -> Unit,
    onCreateClick: () -> Unit,
) {
    // The search text lives here while it is typed; the ViewModel debounces it.
    var query by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(Unit) { onEvent(ProductListEvent.QueryChanged(query)) }
    val listState = rememberLazyListState()
    val fabExpanded by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val filter = state.filter

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.products_title)) },
                scrollBehavior = scrollBehavior,
                actions = {
                    // Items to match are attention items: terracotta, never red (ui-redesign.md §1).
                    val badgeColor = EksodaTheme.colors.spend
                    IconButton(onClick = onReviewClick) {
                        BadgedBox(
                            badge = {
                                if (state.reviewCount > 0) {
                                    Badge(containerColor = badgeColor) {
                                        Text("${state.reviewCount}")
                                    }
                                }
                            },
                        ) {
                            Icon(Icons.Outlined.RateReview, stringResource(R.string.review_title))
                        }
                    }
                    SortMenu(filter.ordering) { onEvent(ProductListEvent.OrderingChanged(it)) }
                    OverflowMenu(onCategoriesClick, onMerchantsClick)
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreateClick,
                expanded = fabExpanded,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.product_new)) },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            SearchField(query) {
                query = it
                onEvent(ProductListEvent.QueryChanged(it))
            }
            FilterRow(
                category = filter.category,
                merchant = filter.merchant,
                categories = state.categories,
                merchants = state.merchants,
                onCategory = { onEvent(ProductListEvent.CategoryChanged(it)) },
                onMerchant = { onEvent(ProductListEvent.MerchantChanged(it)) },
            )
            ProductListContent(
                items = items,
                filtered = query.isNotBlank() || filter.category != null || filter.merchant != null,
                listState = listState,
                onProductClick = onProductClick,
                onClearFilters = {
                    query = ""
                    onEvent(ProductListEvent.QueryChanged(""))
                    onEvent(ProductListEvent.CategoryChanged(null))
                    onEvent(ProductListEvent.MerchantChanged(null))
                },
            )
        }
    }
}

@Composable
private fun ProductListContent(
    items: LazyPagingItems<Product>,
    filtered: Boolean,
    listState: LazyListState,
    onProductClick: (String) -> Unit,
    onClearFilters: () -> Unit,
) {
    val refresh = items.loadState.refresh
    Box(modifier = Modifier.fillMaxSize()) {
        when {
            refresh is LoadState.Loading && items.itemCount == 0 ->
                DelayedProgressIndicator(modifier = Modifier.align(Alignment.Center))

            items.itemCount == 0 && filtered -> EmptyState(
                title = stringResource(R.string.products_no_match),
                description = stringResource(R.string.products_no_match_hint),
                icon = Icons.Default.Search,
                actionText = stringResource(R.string.filter_clear),
                onActionClick = onClearFilters,
            )

            items.itemCount == 0 && refresh is LoadState.NotLoading -> EmptyState(
                title = stringResource(R.string.products_empty_title),
                description = stringResource(R.string.products_empty_body),
                icon = Icons.Outlined.Inventory2,
            )

            else -> LazyColumn(
                state = listState,
                contentPadding = PaddingValues(bottom = 88.dp),
            ) {
                items(
                    count = items.itemCount,
                    key = items.itemKey { it.id },
                    contentType = items.itemContentType { "product" },
                ) { index ->
                    val product = items[index] ?: return@items
                    ProductRow(
                        product = product,
                        onClick = { onProductClick(product.id) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}

@Composable
private fun ProductRow(product: Product, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ListItem(
        headlineContent = { Text(product.name, maxLines = 2) },
        supportingContent = productSupporting(product)?.let { { Text(it, maxLines = 1) } },
        leadingContent = { CategoryBadge(product.category?.slug) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun FilterRow(
    category: Category?,
    merchant: Merchant?,
    categories: List<Category>,
    merchants: List<Merchant>,
    onCategory: (Category?) -> Unit,
    onMerchant: (Merchant?) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val categoryLabels = categories.map { it to categoryName(it) }
            .sortedBy { it.second.lowercase() }
        MenuChip(
            label = category?.let { categoryName(it) } ?: stringResource(R.string.filter_category),
            selected = category != null,
            options = categoryLabels,
            onSelect = onCategory,
        )
        MenuChip(
            label = merchant?.let { stringResource(R.string.filter_bought_at_name, it.name) }
                ?: stringResource(R.string.filter_bought_at),
            selected = merchant != null,
            options = merchants.map { it to it.name },
            onSelect = onMerchant,
        )
    }
}

/** A filter chip that opens a menu of [options]; tapping a set chip clears it. */
@Composable
private fun <T> MenuChip(
    label: String,
    selected: Boolean,
    options: List<Pair<T, String>>,
    onSelect: (T?) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = selected,
            onClick = { if (selected) onSelect(null) else open = true },
            label = { Text(label, maxLines = 1) },
            trailingIcon = {
                Icon(
                    if (selected) Icons.Default.Close else Icons.Default.ArrowDropDown,
                    contentDescription = if (selected) {
                        stringResource(R.string.filter_remove, label)
                    } else {
                        null
                    },
                    modifier = Modifier.size(18.dp),
                )
            },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (value, text) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = {
                        open = false
                        onSelect(value)
                    },
                )
            }
        }
    }
}

@Composable
private fun SortMenu(selected: ProductOrdering, onSelect: (ProductOrdering) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.AutoMirrored.Filled.Sort, stringResource(R.string.sort_title))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            ProductOrdering.entries.forEach { ordering ->
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(
                                when (ordering) {
                                    ProductOrdering.NAME -> R.string.sort_name
                                    ProductOrdering.MOST_BOUGHT -> R.string.sort_most_bought
                                },
                            ),
                        )
                    },
                    leadingIcon = { RadioButton(selected = ordering == selected, onClick = null) },
                    onClick = {
                        open = false
                        onSelect(ordering)
                    },
                )
            }
        }
    }
}

@Composable
private fun OverflowMenu(onCategoriesClick: () -> Unit, onMerchantsClick: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Default.MoreVert, stringResource(R.string.more_options))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.categories_title)) },
                onClick = {
                    open = false
                    onCategoriesClick()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.merchants_title)) },
                onClick = {
                    open = false
                    onMerchantsClick()
                },
            )
        }
    }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
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
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
    )
}
