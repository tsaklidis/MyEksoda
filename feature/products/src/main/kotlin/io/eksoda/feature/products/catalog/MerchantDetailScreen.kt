package io.eksoda.feature.products.catalog

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.eksoda.core.designsystem.component.MerchantAvatar
import io.eksoda.core.designsystem.component.SectionCard
import io.eksoda.core.designsystem.theme.EksodaTheme
import io.eksoda.core.model.Category
import io.eksoda.core.model.MerchantSummary
import io.eksoda.core.model.Store
import io.eksoda.core.ui.component.DelayedProgressIndicator
import io.eksoda.core.ui.component.EmptyState
import io.eksoda.core.ui.text.appLocale
import io.eksoda.core.ui.text.categoryName
import io.eksoda.core.ui.text.dateFormatter
import io.eksoda.feature.products.R
import io.eksoda.feature.products.common.CategoryBadge
import io.eksoda.feature.products.common.catalogErrorRes
import io.eksoda.feature.products.common.categoryLabel

@Composable
fun MerchantDetailRoute(onBack: () -> Unit, viewModel: MerchantDetailViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var renaming by rememberSaveable { mutableStateOf(false) }
    var choosingCategory by rememberSaveable { mutableStateOf(false) }
    val resources = LocalResources.current
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is MerchantDetailEffect.Failed -> {
                    val message = resources.getString(catalogErrorRes(effect.error))
                    snackbarHostState.showSnackbar(message)
                }
            }
        }
    }
    MerchantDetailScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onRenameClick = { renaming = true },
        onCategoryClick = { choosingCategory = true },
    )
    val loaded = state as? MerchantDetailUiState.Loaded ?: return
    if (renaming) {
        RenameDialog(
            initial = if (loaded.merchant.nameSetByUser) loaded.merchant.name else "",
            derived = loaded.merchant.name,
            onSave = {
                renaming = false
                viewModel.onEvent(MerchantDetailEvent.Rename(it))
            },
            onDismiss = { renaming = false },
        )
    }
    if (choosingCategory) {
        CategoryDialog(
            categories = loaded.categories,
            selectedId = loaded.merchant.defaultCategory?.id,
            onSelect = {
                choosingCategory = false
                viewModel.onEvent(MerchantDetailEvent.DefaultCategoryChanged(it))
            },
            onDismiss = { choosingCategory = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MerchantDetailScreen(
    state: MerchantDetailUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onRenameClick: () -> Unit,
    onCategoryClick: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    if (state is MerchantDetailUiState.Loaded) {
                        IconButton(onClick = onRenameClick) {
                            Icon(Icons.Default.Edit, stringResource(R.string.merchant_rename))
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (state) {
                MerchantDetailUiState.Loading ->
                    DelayedProgressIndicator(modifier = Modifier.align(Alignment.Center))

                MerchantDetailUiState.Missing -> EmptyState(
                    title = stringResource(R.string.merchant_missing),
                    description = null,
                    icon = Icons.Outlined.Storefront,
                )

                is MerchantDetailUiState.Loaded ->
                    MerchantDetailContent(state.merchant, state.stores, onCategoryClick)
            }
        }
    }
}

@Composable
private fun MerchantDetailContent(
    merchant: MerchantSummary,
    stores: List<Store>,
    onCategoryClick: () -> Unit,
) {
    val context = LocalContext.current
    val locale = appLocale()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            MerchantAvatar(merchant.id, merchant.name, size = 56.dp)
            Column {
                Text(merchant.name, style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = merchant.legalName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        SectionCard(title = stringResource(R.string.merchant_details)) {
            Detail(R.string.merchant_vat, merchant.vatNumber)
            Detail(
                R.string.merchant_receipts,
                pluralStringResource(
                    R.plurals.merchant_receipts,
                    merchant.receiptCount,
                    merchant.receiptCount,
                ),
            )
            merchant.lastReceiptAt?.let {
                val day = it.format(dateFormatter("d MMM yyyy", locale))
                Detail(R.string.merchant_last_receipt, day)
            }
            merchant.address?.let { address ->
                Detail(R.string.merchant_address, address)
                TextButton(
                    onClick = { openInMaps(context, address) },
                    modifier = Modifier.padding(horizontal = 4.dp),
                ) {
                    Icon(Icons.Outlined.Map, contentDescription = null)
                    Text(
                        stringResource(R.string.merchant_open_maps),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
        SectionCard(title = stringResource(R.string.merchant_default_category)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onCategoryClick)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CategoryBadge(merchant.defaultCategory?.slug, size = 32.dp)
                Text(categoryLabel(merchant.defaultCategory), modifier = Modifier.weight(1f))
                Text(
                    stringResource(R.string.change),
                    color = EksodaTheme.colors.compare,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Text(
                text = stringResource(R.string.merchant_default_category_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        if (stores.isNotEmpty()) {
            SectionCard(title = stringResource(R.string.merchant_stores)) {
                stores.forEach { store ->
                    Text(
                        text = store.label.ifEmpty {
                            stringResource(R.string.merchant_store_unnamed)
                        },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Detail(label: Int, value: String) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text(
            text = stringResource(label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun RenameDialog(
    initial: String,
    derived: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initial.ifEmpty { derived }) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.merchant_rename)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.merchant_name)) },
                supportingText = { Text(stringResource(R.string.merchant_rename_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(name) }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun CategoryDialog(
    categories: List<Category>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val named = categories.map { it to categoryName(it) }
    val sorted = remember(named) { named.sortedBy { it.second.lowercase() } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.merchant_default_category)) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
            ) {
                ChoiceRow(stringResource(R.string.category_none), selectedId == null) {
                    onSelect(null)
                }
                sorted.forEach { (category, label) ->
                    ChoiceRow(label, category.id == selectedId) { onSelect(category.id) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, modifier = Modifier.padding(start = 12.dp))
    }
}

/** The address in the user's map app; nothing happens when there is none. */
private fun openInMaps(context: Context, address: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(address)))
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // No map app installed.
    }
}
