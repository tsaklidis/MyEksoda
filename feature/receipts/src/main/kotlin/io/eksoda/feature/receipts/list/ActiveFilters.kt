package io.eksoda.feature.receipts.list

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.eksoda.core.model.DocumentKind
import io.eksoda.core.model.ReceiptFilter
import io.eksoda.core.ui.text.appLocale
import io.eksoda.core.ui.text.categoryName
import io.eksoda.core.ui.text.dateFormatter
import io.eksoda.core.ui.text.formatDecimalInput
import io.eksoda.feature.receipts.R
import java.time.LocalDate

/** One active filter as a removable chip (android-app.md §6.4.2, §4.3). */
private data class ActiveFilter(val label: String, val remove: (ReceiptFilter) -> ReceiptFilter)

/** The active filters as removable chips, and "Clear all". Nothing when no filter is set. */
@Composable
internal fun ActiveFilterChips(
    filter: ReceiptFilter,
    today: LocalDate,
    onChange: (ReceiptFilter) -> Unit,
    onClear: () -> Unit,
) {
    if (!filter.hasFilters) return
    val chips = activeFilters(filter, today)
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        chips.forEach { chip ->
            InputChip(
                selected = true,
                onClick = { onChange(chip.remove(filter)) },
                label = { Text(chip.label) },
                trailingIcon = {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.filter_remove, chip.label),
                    )
                },
            )
        }
        AssistChip(onClick = onClear, label = { Text(stringResource(R.string.filter_clear_all)) })
    }
}

@Composable
private fun activeFilters(filter: ReceiptFilter, today: LocalDate): List<ActiveFilter> = buildList {
    periodLabel(filter, today)?.let { label ->
        add(ActiveFilter(label) { it.copy(issuedFrom = null, issuedTo = null) })
    }
    filter.merchant?.let { add(ActiveFilter(it.name) { f -> f.copy(merchant = null) }) }
    filter.store?.let { add(ActiveFilter(it.name) { f -> f.copy(store = null) }) }
    filter.category?.let {
        val label = categoryName(it.name)
        add(ActiveFilter(label) { f -> f.copy(category = null) })
    }
    filter.product?.let { add(ActiveFilter(it.name) { f -> f.copy(product = null) }) }
    amountLabel(filter)?.let { label ->
        add(ActiveFilter(label) { it.copy(minTotal = null, maxTotal = null) })
    }
    addAll(flagFilters(filter))
}

@Composable
private fun flagFilters(filter: ReceiptFilter): List<ActiveFilter> = buildList {
    if (filter.needsReview) {
        val label = stringResource(R.string.filter_needs_review)
        add(ActiveFilter(label) { it.copy(needsReview = false) })
    }
    if (filter.hasWarnings) {
        val label = stringResource(R.string.filter_has_warnings)
        add(ActiveFilter(label) { it.copy(hasWarnings = false) })
    }
    filter.documentKind?.let { kind ->
        val refunds = kind == DocumentKind.CREDIT_NOTE
        val label = stringResource(if (refunds) R.string.type_refunds else R.string.type_purchases)
        add(ActiveFilter(label) { it.copy(documentKind = null) })
    }
    if (filter.hideCancelled) {
        val label = stringResource(R.string.filter_hide_cancelled)
        add(ActiveFilter(label) { it.copy(hideCancelled = false) })
    }
}

/** A preset's name ("This month"), else the dates ("1 Nov – 15 Nov"). */
@Composable
internal fun periodLabel(filter: ReceiptFilter, today: LocalDate): String? {
    val from = filter.issuedFrom
    val to = filter.issuedTo
    val preset = PeriodPreset.of(from, to, today)
    val format = dateFormatter("d MMM yyyy", appLocale())
    return when {
        from == null && to == null -> null

        preset != null -> stringResource(preset.label)

        from != null && to != null ->
            stringResource(R.string.range, from.format(format), to.format(format))

        from != null -> stringResource(R.string.range_from, from.format(format))

        else -> stringResource(R.string.range_to, requireNotNull(to).format(format))
    }
}

@Composable
private fun amountLabel(filter: ReceiptFilter): String? {
    val locale = appLocale()
    val min = filter.minTotal?.let { formatDecimalInput(it, locale) }
    val max = filter.maxTotal?.let { formatDecimalInput(it, locale) }
    return when {
        min != null && max != null -> stringResource(R.string.amount_range, min, max)
        min != null -> stringResource(R.string.amount_min, min)
        max != null -> stringResource(R.string.amount_max, max)
        else -> null
    }
}
