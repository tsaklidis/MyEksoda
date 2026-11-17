package io.eksoda.feature.receipts.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.eksoda.core.designsystem.component.CategoryIcon
import io.eksoda.core.model.DocumentKind
import io.eksoda.core.model.Option
import io.eksoda.core.model.PeriodPreset
import io.eksoda.core.model.ReceiptFilter
import io.eksoda.core.ui.component.DateRangeDialog
import io.eksoda.core.ui.text.appLocale
import io.eksoda.core.ui.text.categoryName
import io.eksoda.core.ui.text.formatDecimalInput
import io.eksoda.core.ui.text.parseDecimalInput
import io.eksoda.core.ui.text.searchKey
import io.eksoda.feature.receipts.R
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Locale

private const val MAX_SUGGESTIONS = 8

/**
 * The filter sheet. It edits a draft; nothing changes until Apply. Sheet actions never navigate
 * away.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReceiptFilterSheet(
    filter: ReceiptFilter,
    options: FilterOptions,
    today: LocalDate,
    onApply: (ReceiptFilter) -> Unit,
    onDismiss: () -> Unit,
    onMerchantPicked: (String?) -> Unit,
    onProductSearch: (String) -> Unit,
) {
    val locale = appLocale()
    var draft by remember { mutableStateOf(filter) }
    var minText by remember { mutableStateOf(amountText(filter.minTotal, locale)) }
    var maxText by remember { mutableStateOf(amountText(filter.maxTotal, locale)) }
    LaunchedEffect(draft.merchant) { onMerchantPicked(draft.merchant?.id) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.filter_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            PeriodSection(draft, today) { from, to ->
                draft = draft.copy(issuedFrom = from, issuedTo = to)
            }
            MerchantSection(draft.merchant, options.merchants) {
                draft = draft.copy(merchant = it, store = null)
            }
            if (draft.merchant != null && options.stores.isNotEmpty()) {
                StoreSection(draft.store, options.stores) { draft = draft.copy(store = it) }
            }
            CategorySection(draft.category, options.categories) {
                draft = draft.copy(category = it)
            }
            ProductSection(draft.product, options.products, onProductSearch) {
                draft = draft.copy(product = it)
            }
            AmountSection(minText, maxText, onMin = { minText = it }, onMax = { maxText = it })
            TypeSection(draft.documentKind) { draft = draft.copy(documentKind = it) }
            ToggleRow(R.string.filter_needs_review, draft.needsReview) {
                draft = draft.copy(needsReview = it)
            }
            ToggleRow(R.string.filter_has_warnings, draft.hasWarnings) {
                draft = draft.copy(hasWarnings = it)
            }
            ToggleRow(R.string.filter_hide_cancelled, draft.hideCancelled) {
                draft = draft.copy(hideCancelled = it)
            }
            SheetButtons(
                onClear = {
                    draft = ReceiptFilter(query = filter.query, ordering = filter.ordering)
                    minText = ""
                    maxText = ""
                },
                onApply = {
                    val applied = draft.copy(
                        minTotal = parseDecimalInput(minText),
                        maxTotal = parseDecimalInput(maxText),
                    )
                    // Slide the sheet away first, then apply and remove it.
                    scope.launch { sheetState.hide() }.invokeOnCompletion { onApply(applied) }
                },
            )
        }
    }
}

@Composable
private fun SectionTitle(text: Int) {
    Text(
        text = stringResource(text),
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.semantics { heading() },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PeriodSection(
    filter: ReceiptFilter,
    today: LocalDate,
    onChange: (LocalDate?, LocalDate?) -> Unit,
) {
    var customOpen by remember { mutableStateOf(false) }
    val preset = PeriodPreset.of(filter.issuedFrom, filter.issuedTo, today)
    val custom = preset == null && (filter.issuedFrom != null || filter.issuedTo != null)
    SectionTitle(R.string.filter_period)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PeriodPreset.entries.forEach { entry ->
            FilterChip(
                selected = entry == preset,
                onClick = {
                    if (entry == preset) {
                        onChange(null, null)
                    } else {
                        val range = entry.range(today)
                        onChange(range.start, range.endInclusive)
                    }
                },
                label = { Text(stringResource(entry.label)) },
            )
        }
        FilterChip(
            selected = custom,
            onClick = { customOpen = true },
            label = {
                val label = periodLabel(filter, today).takeIf { custom }
                Text(label ?: stringResource(R.string.period_custom))
            },
        )
    }
    if (customOpen) {
        DateRangeDialog(
            from = filter.issuedFrom,
            to = filter.issuedTo,
            onConfirm = { from, to ->
                customOpen = false
                onChange(from, to)
            },
            onDismiss = { customOpen = false },
        )
    }
}

private fun amountText(value: BigDecimal?, locale: Locale): String =
    value?.let { formatDecimalInput(it, locale) }.orEmpty()

@Composable
private fun SelectedOption(option: Option, label: String = option.name, onClear: () -> Unit) {
    InputChip(
        selected = true,
        onClick = onClear,
        label = { Text(label) },
        trailingIcon = {
            Icon(
                Icons.Default.Close,
                contentDescription = stringResource(R.string.filter_remove, label),
            )
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MerchantSection(
    selected: Option?,
    merchants: List<Option>,
    onSelect: (Option?) -> Unit,
) {
    SectionTitle(R.string.filter_merchant)
    if (selected != null) {
        SelectedOption(selected) { onSelect(null) }
        return
    }
    var query by remember { mutableStateOf("") }
    // Accents and case are ignored, as in the receipt search: "μασουτης" finds "Μασούτης".
    val keyed = remember(merchants) { merchants.map { it to searchKey(it.name) } }
    val matches = remember(query, keyed) {
        val key = searchKey(query)
        keyed.filter { (_, name) -> name.contains(key) }.map { it.first }.take(MAX_SUGGESTIONS)
    }
    SearchField(query, R.string.filter_merchant_search) { query = it }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        matches.forEach { option ->
            SuggestionChip(option) { onSelect(option) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StoreSection(selected: Option?, stores: List<Option>, onSelect: (Option?) -> Unit) {
    SectionTitle(R.string.filter_store)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        stores.forEach { option ->
            FilterChip(
                selected = option.id == selected?.id,
                onClick = { onSelect(if (option.id == selected?.id) null else option) },
                label = { Text(option.name) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategorySection(
    selected: Option?,
    categories: List<Option>,
    onSelect: (Option?) -> Unit,
) {
    SectionTitle(R.string.filter_category)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        categories.forEach { option ->
            FilterChip(
                selected = option.id == selected?.id,
                onClick = { onSelect(if (option.id == selected?.id) null else option) },
                label = { Text(categoryName(option.name)) },
                leadingIcon = { CategoryIcon(option.name, modifier = Modifier.size(18.dp)) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProductSection(
    selected: Option?,
    results: List<Option>,
    onSearch: (String) -> Unit,
    onSelect: (Option?) -> Unit,
) {
    SectionTitle(R.string.filter_product)
    if (selected != null) {
        SelectedOption(selected) { onSelect(null) }
        return
    }
    var query by remember { mutableStateOf("") }
    SearchField(query, R.string.filter_product_search) {
        query = it
        onSearch(it)
    }
    if (query.isNotBlank()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            results.take(MAX_SUGGESTIONS).forEach { option ->
                SuggestionChip(option) { onSelect(option) }
            }
        }
    }
}

@Composable
private fun SuggestionChip(option: Option, onClick: () -> Unit) {
    FilterChip(selected = false, onClick = onClick, label = { Text(option.name) })
}

@Composable
private fun SearchField(value: String, placeholder: Int, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        placeholder = { Text(stringResource(placeholder)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun AmountSection(
    minText: String,
    maxText: String,
    onMin: (String) -> Unit,
    onMax: (String) -> Unit,
) {
    SectionTitle(R.string.filter_amount)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        AmountField(minText, R.string.filter_amount_min, onMin, Modifier.weight(1f))
        AmountField(maxText, R.string.filter_amount_max, onMax, Modifier.weight(1f))
    }
}

@Composable
private fun AmountField(value: String, label: Int, onChange: (String) -> Unit, modifier: Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(label)) },
        isError = value.isNotBlank() && parseDecimalInput(value) == null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}

/** All · Purchases · Refunds. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TypeSection(selected: DocumentKind?, onSelect: (DocumentKind?) -> Unit) {
    SectionTitle(R.string.filter_type)
    val choices = listOf(
        null to R.string.type_all,
        DocumentKind.SALE to R.string.type_purchases,
        DocumentKind.CREDIT_NOTE to R.string.type_refunds,
    )
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        choices.forEachIndexed { index, (kind, label) ->
            SegmentedButton(
                selected = kind == selected,
                onClick = { onSelect(kind) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = choices.size),
            ) { Text(stringResource(label)) }
        }
    }
}

@Composable
private fun ToggleRow(label: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(label), modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SheetButtons(onClear: () -> Unit, onApply: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        TextButton(onClick = onClear) { Text(stringResource(R.string.filter_clear_all)) }
        Button(onClick = onApply, modifier = Modifier.padding(start = 8.dp)) {
            Text(stringResource(R.string.filter_apply))
        }
    }
}
