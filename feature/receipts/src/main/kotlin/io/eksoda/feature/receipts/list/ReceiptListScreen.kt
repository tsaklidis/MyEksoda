package io.eksoda.feature.receipts.list

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import io.eksoda.core.model.DocumentKind
import io.eksoda.core.model.Money
import io.eksoda.core.model.ReceiptOrdering
import io.eksoda.core.ui.component.DelayedProgressIndicator
import io.eksoda.core.ui.component.EmptyState
import io.eksoda.feature.receipts.R
import java.math.BigDecimal
import java.time.LocalDate

@Composable
fun ReceiptListRoute(
    onReceiptClick: (String) -> Unit,
    onScanClick: () -> Unit,
    onImportsClick: () -> Unit,
    viewModel: ReceiptListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val items = viewModel.items.collectAsLazyPagingItems()
    val snackbarHostState = remember { SnackbarHostState() }
    val excludedText = stringResource(R.string.swipe_excluded)
    val includedText = stringResource(R.string.swipe_included)
    val deletedText = stringResource(R.string.swipe_deleted)
    val failedText = stringResource(R.string.detail_save_failed)
    val undoText = stringResource(R.string.undo)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ReceiptListEffect.ExcludedChanged -> {
                    val result = snackbarHostState.showSnackbar(
                        message = if (effect.excluded) excludedText else includedText,
                        actionLabel = undoText,
                        duration = SnackbarDuration.Short,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.onEvent(
                            ReceiptListEvent.ExcludedChanged(effect.receiptId, !effect.excluded),
                        )
                    }
                }

                ReceiptListEffect.Deleted -> snackbarHostState.showSnackbar(deletedText)

                ReceiptListEffect.Failed -> snackbarHostState.showSnackbar(failedText)
            }
        }
    }
    ReceiptListScreen(
        state = state,
        items = items,
        onEvent = viewModel::onEvent,
        onReceiptClick = onReceiptClick,
        onScanClick = onScanClick,
        onImportsClick = onImportsClick,
        snackbarHostState = snackbarHostState,
    )
}

/** Receipts with search and filters (android-app.md §6.4.1–6.4.2). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptListScreen(
    state: ReceiptListUiState,
    items: LazyPagingItems<ReceiptListItem>,
    onEvent: (ReceiptListEvent) -> Unit,
    onReceiptClick: (String) -> Unit,
    onScanClick: () -> Unit,
    onImportsClick: () -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    var sheetOpen by rememberSaveable { mutableStateOf(false) }
    var pendingDelete by rememberSaveable { mutableStateOf<String?>(null) }
    // The search text lives here while it is typed; the ViewModel gets every change and
    // applies it after a pause. A TextField fed back through a Flow drops keystrokes.
    var query by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(Unit) { onEvent(ReceiptListEvent.QueryChanged(query)) }
    // "Today" and "Yesterday" stay right when the app is left open past midnight.
    var today by remember { mutableStateOf(LocalDate.now()) }
    LifecycleResumeEffect(Unit) {
        today = LocalDate.now()
        onPauseOrDispose { }
    }
    val listState = rememberLazyListState()
    val fabExpanded by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }
    val filter = state.filter
    // A large title that collapses as the list scrolls up.
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.receipts_title)) },
                scrollBehavior = scrollBehavior,
                actions = {
                    SortMenu(filter.ordering) { onEvent(ReceiptListEvent.OrderingChanged(it)) }
                    IconButton(onClick = { sheetOpen = true }) {
                        BadgedBox(badge = { if (filter.hasFilters) Badge() }) {
                            Icon(Icons.Default.FilterList, stringResource(R.string.filter_title))
                        }
                    }
                    IconButton(onClick = onImportsClick) {
                        Icon(Icons.Default.History, stringResource(R.string.receipts_imports))
                    }
                },
            )
        },
        floatingActionButton = {
            // Collapses to the icon once the list scrolls, so it covers less of the rows.
            ExtendedFloatingActionButton(
                onClick = onScanClick,
                expanded = fabExpanded,
                icon = {
                    Icon(
                        Icons.Default.QrCodeScanner,
                        contentDescription = if (fabExpanded) {
                            null
                        } else {
                            stringResource(R.string.receipts_scan)
                        },
                    )
                },
                text = { Text(stringResource(R.string.receipts_scan)) },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            SearchField(query) {
                query = it
                onEvent(ReceiptListEvent.QueryChanged(it))
            }
            ActiveFilterChips(
                filter = filter,
                today = today,
                onChange = { onEvent(ReceiptListEvent.FilterChanged(it)) },
                onClear = { onEvent(ReceiptListEvent.FiltersCleared) },
            )
            ReceiptListContent(
                items = items,
                listState = listState,
                today = today,
                filtered = filter.hasFilters || !filter.query.isNullOrBlank(),
                onReceiptClick = onReceiptClick,
                onDeleteRequest = { pendingDelete = it },
                onExcludedChange = { id, excluded ->
                    onEvent(ReceiptListEvent.ExcludedChanged(id, excluded))
                },
                onScanClick = onScanClick,
                // "No match" may come from the search text alone: clear it with the filters.
                onClearFilters = {
                    query = ""
                    onEvent(ReceiptListEvent.QueryChanged(""))
                    onEvent(ReceiptListEvent.FiltersCleared)
                },
            )
        }
    }

    if (sheetOpen) {
        ReceiptFilterSheet(
            filter = filter,
            options = state.options,
            today = today,
            onApply = {
                sheetOpen = false
                onEvent(ReceiptListEvent.FilterChanged(it))
            },
            onDismiss = { sheetOpen = false },
            onMerchantPicked = { onEvent(ReceiptListEvent.SheetMerchantChanged(it)) },
            onProductSearch = { onEvent(ReceiptListEvent.ProductSearched(it)) },
        )
    }

    pendingDelete?.let { receiptId ->
        DeleteDialog(
            onConfirm = {
                pendingDelete = null
                onEvent(ReceiptListEvent.DeleteConfirmed(receiptId))
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

/** The same confirmation as on Receipt detail (android-app.md §6.4.3). */
@Composable
private fun DeleteDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.detail_delete_title)) },
        text = { Text(stringResource(R.string.detail_delete_body)) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) { Text(stringResource(R.string.detail_delete)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun ReceiptListContent(
    items: LazyPagingItems<ReceiptListItem>,
    listState: LazyListState,
    today: LocalDate,
    filtered: Boolean,
    onReceiptClick: (String) -> Unit,
    onDeleteRequest: (String) -> Unit,
    onExcludedChange: (String, Boolean) -> Unit,
    onScanClick: () -> Unit,
    onClearFilters: () -> Unit,
) {
    val refresh = items.loadState.refresh
    Box(modifier = Modifier.fillMaxSize()) {
        when {
            refresh is LoadState.Loading && items.itemCount == 0 ->
                DelayedProgressIndicator(modifier = Modifier.align(Alignment.Center))

            refresh is LoadState.Error && items.itemCount == 0 -> Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.receipts_load_failed))
                TextButton(onClick = items::retry) { Text(stringResource(R.string.retry)) }
            }

            items.itemCount == 0 && filtered -> EmptyState(
                title = stringResource(R.string.receipts_no_match),
                description = stringResource(R.string.receipts_no_match_hint),
                icon = Icons.Default.Search,
                actionText = stringResource(R.string.filter_clear),
                onActionClick = onClearFilters,
            )

            items.itemCount == 0 -> EmptyState(
                title = stringResource(R.string.receipts_empty),
                description = stringResource(R.string.receipts_empty_hint),
                icon = Icons.AutoMirrored.Filled.ReceiptLong,
                actionText = stringResource(R.string.receipts_scan),
                onActionClick = onScanClick,
            )

            else -> ReceiptLazyList(
                items = items,
                listState = listState,
                today = today,
                onReceiptClick = onReceiptClick,
                onDeleteRequest = onDeleteRequest,
                onExcludedChange = onExcludedChange,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReceiptLazyList(
    items: LazyPagingItems<ReceiptListItem>,
    listState: LazyListState,
    today: LocalDate,
    onReceiptClick: (String) -> Unit,
    onDeleteRequest: (String) -> Unit,
    onExcludedChange: (String, Boolean) -> Unit,
) {
    val dayTotals = remember(items.itemSnapshotList) { dayTotals(items) }
    // A receipt that finishes importing goes above the first row. The list keeps the old first
    // row in place, which would hide the new receipt; when the list was at the top, follow it.
    val firstKey = if (items.itemCount > 0) items.peek(0)?.key() else null
    var shownFirstKey by remember { mutableStateOf(firstKey) }
    LaunchedEffect(firstKey) {
        val added = shownFirstKey != null && firstKey != null && firstKey != shownFirstKey
        if (added && listState.firstVisibleItemIndex <= NEW_ROWS_FOLLOWED) {
            listState.animateScrollToItem(0)
        }
        if (firstKey != null) shownFirstKey = firstKey
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 88.dp),
    ) {
        // One item per row, so day headers can stick (§6.4.1). `peek` decides the kind without
        // loading; `items[index]` inside each item tells Paging which rows are on screen.
        for (index in 0 until items.itemCount) {
            when (val peeked = items.peek(index)) {
                is ReceiptListItem.Day -> stickyHeader(key = peeked.key(), contentType = DAY) {
                    items[index]
                    DayHeader(peeked.date, today, dayTotals[peeked.date])
                }

                is ReceiptListItem.Row -> item(key = peeked.key(), contentType = ROW) {
                    val receipt = (items[index] as? ReceiptListItem.Row)?.receipt ?: peeked.receipt
                    Column(modifier = Modifier.animateItem()) {
                        SwipeableReceiptRow(
                            receipt = receipt,
                            onClick = { onReceiptClick(receipt.id) },
                            onDeleteRequest = { onDeleteRequest(receipt.id) },
                            onExcludedToggle = {
                                onExcludedChange(receipt.id, !receipt.excludedFromAnalytics)
                            },
                        )
                        HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
                    }
                }

                null -> item(key = "placeholder:$index", contentType = PLACEHOLDER) {
                    items[index]
                }
            }
        }
        appendState(items)
    }
}

private fun LazyListScope.appendState(items: LazyPagingItems<ReceiptListItem>) {
    if (items.loadState.append is LoadState.Loading) {
        item {
            Box(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }
        }
    }
}

/** A new day header and its first receipt: the first row moves down by at most this much. */
private const val NEW_ROWS_FOLLOWED = 2

private const val DAY = "day"
private const val ROW = "row"
private const val PLACEHOLDER = "placeholder"

private fun ReceiptListItem.key(): String = when (this) {
    is ReceiptListItem.Day -> "day:$date"
    is ReceiptListItem.Row -> receipt.id
}

/**
 * Each day's total from the rows loaded so far (android-app.md §6.4.1: a convenience; Insights
 * has the real totals). Cancelled receipts don't count, refunds subtract. Days with more than
 * one currency get no total.
 */
private fun dayTotals(items: LazyPagingItems<ReceiptListItem>): Map<LocalDate, Money> =
    items.itemSnapshotList.items
        .filterIsInstance<ReceiptListItem.Row>()
        .map { it.receipt }
        .filter { !it.cancelled }
        .groupBy { it.issuedDate }
        .mapNotNull { (date, receipts) ->
            val currency = receipts.map { it.totalAmount.currency }.distinct().singleOrNull()
                ?: return@mapNotNull null
            val total = receipts.fold(BigDecimal.ZERO) { sum, receipt ->
                val amount = receipt.totalAmount.amount
                if (receipt.documentKind == DocumentKind.CREDIT_NOTE) sum - amount else sum + amount
            }
            date to Money(total, currency)
        }
        .toMap()

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    TextField(
        value = query,
        onValueChange = onChange,
        placeholder = { Text(stringResource(R.string.receipts_search)) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onChange("") }) {
                    Icon(Icons.Default.Close, stringResource(R.string.receipts_search_clear))
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

@Composable
private fun SortMenu(selected: ReceiptOrdering, onSelect: (ReceiptOrdering) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.AutoMirrored.Filled.Sort, stringResource(R.string.sort_title))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            ReceiptOrdering.entries.forEach { ordering ->
                DropdownMenuItem(
                    text = { Text(stringResource(ordering.label)) },
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

private val ReceiptOrdering.label: Int
    get() = when (this) {
        ReceiptOrdering.NEWEST -> R.string.sort_newest
        ReceiptOrdering.OLDEST -> R.string.sort_oldest
        ReceiptOrdering.HIGHEST_TOTAL -> R.string.sort_highest_total
        ReceiptOrdering.LOWEST_TOTAL -> R.string.sort_lowest_total
    }
