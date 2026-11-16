package io.eksoda.feature.receipts.detail

import android.content.ActivityNotFoundException
import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.eksoda.core.model.Option
import io.eksoda.core.model.ReceiptDetail
import io.eksoda.core.ui.component.DelayedProgressIndicator
import io.eksoda.core.ui.motion.SharedKeys
import io.eksoda.core.ui.motion.sharedBoundsIfAvailable
import io.eksoda.feature.receipts.R

/** Dialogs of the detail screen; one at a time. */
private enum class DetailDialog { OPEN_ORIGINAL, DELETE, NOTE, STORE }

@Composable
fun ReceiptDetailRoute(
    onBack: () -> Unit,
    onProductClick: (String) -> Unit,
    onMerchantClick: (String) -> Unit,
    viewModel: ReceiptDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val saveFailed = stringResource(R.string.detail_save_failed)
    val noBrowser = stringResource(R.string.detail_no_browser)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ReceiptDetailEffect.OpenLink -> {
                    val opened = openInCustomTab(context, effect.link)
                    if (!opened) snackbarHostState.showSnackbar(noBrowser)
                }

                ReceiptDetailEffect.Deleted -> onBack()

                ReceiptDetailEffect.SaveFailed -> snackbarHostState.showSnackbar(saveFailed)
            }
        }
    }
    ReceiptDetailScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        onProductClick = onProductClick,
        onMerchantClick = onMerchantClick,
        snackbarHostState = snackbarHostState,
    )
}

/**
 * Opens the receipt page in a Custom Tab, with sharing turned off: the link works like a
 * password (§8.1). It is never copied or put in a share intent.
 */
private fun openInCustomTab(context: Context, link: String): Boolean = try {
    CustomTabsIntent.Builder()
        .setShareState(CustomTabsIntent.SHARE_STATE_OFF)
        .build()
        .launchUrl(context, Uri.parse(link))
    true
} catch (_: ActivityNotFoundException) {
    false
}

/** Receipt detail (android-app.md §6.4.3). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptDetailScreen(
    state: ReceiptDetailUiState,
    onEvent: (ReceiptDetailEvent) -> Unit,
    onBack: () -> Unit,
    onProductClick: (String) -> Unit = {},
    onMerchantClick: (String) -> Unit = {},
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    var dialog by rememberSaveable { mutableStateOf<DetailDialog?>(null) }
    val loaded = state as? ReceiptDetailUiState.Loaded

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val receipt = loaded?.detail?.receipt
                    Text(
                        text = receipt?.merchant?.name.orEmpty(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        // The merchant's name opens the merchant (rename, default category).
                        modifier = if (receipt != null) {
                            Modifier
                                .sharedBoundsIfAvailable(SharedKeys.receiptMerchant(receipt.id))
                                .clickable { onMerchantClick(receipt.merchant.id) }
                        } else {
                            Modifier
                        },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = { if (loaded != null) OverflowMenu { dialog = it } },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (state) {
                ReceiptDetailUiState.Loading ->
                    DelayedProgressIndicator(modifier = Modifier.align(Alignment.Center))

                ReceiptDetailUiState.Missing -> Text(
                    text = stringResource(R.string.detail_missing),
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )

                is ReceiptDetailUiState.Loaded -> DetailContent(
                    detail = state.detail,
                    canPickStore = state.stores.isNotEmpty(),
                    onEvent = onEvent,
                    onDialog = { dialog = it },
                    onProductClick = onProductClick,
                )
            }
        }
    }

    if (loaded != null) {
        DetailDialogs(
            dialog = dialog,
            detail = loaded.detail,
            stores = loaded.stores,
            onEvent = onEvent,
            onClose = { dialog = null },
        )
    }
}

@Composable
private fun OverflowMenu(onDialog: (DetailDialog) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Default.MoreVert, stringResource(R.string.detail_more))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.detail_open_original)) },
                onClick = {
                    open = false
                    onDialog(DetailDialog.OPEN_ORIGINAL)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.detail_delete)) },
                onClick = {
                    open = false
                    onDialog(DetailDialog.DELETE)
                },
            )
        }
    }
}

@Composable
private fun DetailContent(
    detail: ReceiptDetail,
    canPickStore: Boolean,
    onEvent: (ReceiptDetailEvent) -> Unit,
    onDialog: (DetailDialog) -> Unit,
    onProductClick: (String) -> Unit,
) {
    val receipt = detail.receipt
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item { DetailHeader(detail) }
        if (receipt.cancelled) {
            item { Notice(stringResource(R.string.detail_cancelled), error = true) }
        }
        item { WarningsCard(detail.warnings) }
        item { SectionHeading(stringResource(R.string.detail_items)) }
        if (detail.hasLineItems) {
            items(detail.items, key = { it.id }) { item ->
                LineRow(item, onClick = item.productId?.let { id -> { onProductClick(id) } })
            }
        } else {
            item { Notice(stringResource(R.string.detail_totals_only)) }
        }
        item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
        item { PaymentAndVat(detail) }
        item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
        item {
            EditSection(
                note = detail.userNote,
                store = receipt.store,
                canPickStore = canPickStore,
                included = !receipt.excludedFromAnalytics,
                onEditNote = { onDialog(DetailDialog.NOTE) },
                onPickStore = { onDialog(DetailDialog.STORE) },
                onIncludedChange = { onEvent(ReceiptDetailEvent.IncludedChanged(it)) },
            )
        }
        item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
        item { DetailsSection(detail) }
    }
}

@Composable
private fun DetailDialogs(
    dialog: DetailDialog?,
    detail: ReceiptDetail,
    stores: List<Option>,
    onEvent: (ReceiptDetailEvent) -> Unit,
    onClose: () -> Unit,
) {
    val act: (ReceiptDetailEvent) -> Unit = {
        onClose()
        onEvent(it)
    }
    when (dialog) {
        DetailDialog.OPEN_ORIGINAL -> ConfirmDialog(
            title = R.string.detail_open_original_title,
            body = R.string.detail_open_original_body,
            confirm = R.string.detail_open_original_confirm,
            destructive = false,
            onConfirm = { act(ReceiptDetailEvent.OpenOriginalConfirmed) },
            onDismiss = onClose,
        )

        DetailDialog.DELETE -> ConfirmDialog(
            title = R.string.detail_delete_title,
            body = R.string.detail_delete_body,
            confirm = R.string.detail_delete,
            destructive = true,
            onConfirm = { act(ReceiptDetailEvent.DeleteConfirmed) },
            onDismiss = onClose,
        )

        DetailDialog.NOTE -> NoteDialog(
            initial = detail.userNote,
            onSave = { act(ReceiptDetailEvent.NoteSaved(it)) },
            onDismiss = onClose,
        )

        DetailDialog.STORE -> StoreDialog(
            selectedId = detail.receipt.store?.id,
            stores = stores,
            onSelect = { act(ReceiptDetailEvent.StoreChanged(it)) },
            onDismiss = onClose,
        )

        null -> Unit
    }
}

@Composable
private fun ConfirmDialog(
    title: Int,
    body: Int,
    confirm: Int,
    destructive: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = { Text(stringResource(body)) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = if (destructive) {
                    ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                } else {
                    ButtonDefaults.textButtonColors()
                },
            ) { Text(stringResource(confirm)) }
        },
        dismissButton = { CancelButton(onDismiss) },
    )
}

/** `user_note`, up to 2000 characters (android-app.md §6.4.3). */
@Composable
private fun NoteDialog(initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.detail_note)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(MAX_NOTE_LENGTH) },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(text) }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { CancelButton(onDismiss) },
    )
}

/** One of the merchant's stores, or none (android-app.md §6.4.3). */
@Composable
private fun StoreDialog(
    selectedId: String?,
    stores: List<Option>,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.detail_store)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                val none = stringResource(R.string.detail_store_none)
                StoreChoice(none, selected = selectedId == null) { onSelect(null) }
                stores.forEach { store ->
                    StoreChoice(store.name, store.id == selectedId) { onSelect(store.id) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { CancelButton(onDismiss) },
    )
}

@Composable
private fun StoreChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label)
    }
}

@Composable
private fun CancelButton(onClick: () -> Unit) {
    TextButton(onClick = onClick) { Text(stringResource(R.string.cancel)) }
}

private const val MAX_NOTE_LENGTH = 2000
