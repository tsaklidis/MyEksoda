package io.eksoda.feature.receipts.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.eksoda.core.designsystem.text.monoNumbers
import io.eksoda.core.model.LineType
import io.eksoda.core.model.MatchStatus
import io.eksoda.core.model.ReceiptItem
import io.eksoda.core.model.Store
import io.eksoda.core.ui.component.MoneyText
import io.eksoda.core.ui.text.appLocale
import io.eksoda.core.ui.text.categoryName
import io.eksoda.core.ui.text.formatMoney
import io.eksoda.core.ui.text.formatQuantity
import io.eksoda.feature.receipts.R
import io.eksoda.feature.receipts.list.BadgeTone
import io.eksoda.feature.receipts.list.StatusTag
import java.math.BigDecimal

/**
 * A receipt line (android-app.md §6.4.3): description as printed, quantity × unit price, the
 * amount, and a second line with the normalized price and the product. Discounts are muted.
 */
@Composable
internal fun LineRow(item: ReceiptItem, onClick: (() -> Unit)? = null) {
    val muted = item.lineType == LineType.DISCOUNT
    // A line with a product opens it (price history and comparison).
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                text = item.description,
                style = MaterialTheme.typography.bodyLarge,
                color = if (muted) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.weight(1f).padding(end = 8.dp),
            )
            quantityText(item)?.let {
                Text(
                    text = monoNumbers(it),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 12.dp),
                )
            }
            MoneyText(
                money = item.totalPrice,
                negative = muted || item.totalPrice.amount.signum() < 0,
                fontWeight = if (muted) FontWeight.Normal else null,
            )
        }
        LineDetails(item)
    }
}

/** "2 × 1,29" or "0,812 kg × 1,99"; nothing for a single piece. */
@Composable
private fun quantityText(item: ReceiptItem): String? {
    val quantity = item.quantity ?: return null
    val locale = appLocale()
    val weighed = item.unit == UNIT_KG || item.unit == UNIT_L
    val amount = if (weighed) {
        val number = formatQuantity(quantity, locale)
        stringResource(R.string.quantity_with_unit, number, unitLabel(item.unit))
    } else {
        formatQuantity(quantity, locale)
    }
    val price = item.unitPrice?.let { formatMoney(it, locale) }
    return when {
        !weighed && quantity.compareTo(BigDecimal.ONE) == 0 -> null
        price == null -> amount
        else -> stringResource(R.string.quantity_times_price, amount, price)
    }
}

@Composable
private fun LineDetails(item: ReceiptItem) {
    val locale = appLocale()
    val parts = buildList {
        val price = item.normalizedUnitPrice
        val unit = item.normalizedUnit
        if (price != null && unit != null) {
            val formatted = formatMoney(price, locale)
            add(stringResource(R.string.price_per_unit, formatted, unitLabel(unit)))
        }
        item.productName?.takeIf { it != item.description }?.let { add(it) }
        item.categorySlug?.let { add(categoryName(it)) }
    }
    val review = item.matchStatus == MatchStatus.NEEDS_REVIEW
    if (parts.isEmpty() && !review) return
    Row(
        modifier = Modifier.padding(top = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (parts.isNotEmpty()) {
            Text(
                text = monoNumbers(parts.joinToString(" · ")),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        if (review) StatusTag(stringResource(R.string.badge_review), BadgeTone.ATTENTION)
    }
}

@Composable
private fun unitLabel(unit: String?): String = stringResource(
    when (unit) {
        UNIT_KG -> R.string.unit_kg
        UNIT_L -> R.string.unit_l
        else -> R.string.unit_piece
    },
)

private const val UNIT_KG = "kg"
private const val UNIT_L = "l"

/** Note, store and "Include in stats" (android-app.md §6.4.3). */
@Composable
internal fun EditSection(
    note: String,
    store: Store?,
    canPickStore: Boolean,
    included: Boolean,
    onEditNote: () -> Unit,
    onPickStore: () -> Unit,
    onIncludedChange: (Boolean) -> Unit,
) {
    ListItem(
        modifier = Modifier.clickable(onClick = onEditNote),
        headlineContent = { Text(stringResource(R.string.detail_note)) },
        supportingContent = { Text(note.ifEmpty { stringResource(R.string.detail_note_add) }) },
        trailingContent = { Icon(Icons.Default.Edit, stringResource(R.string.detail_note_edit)) },
    )
    if (canPickStore) {
        ListItem(
            modifier = Modifier.clickable(onClick = onPickStore),
            headlineContent = { Text(stringResource(R.string.detail_store)) },
            supportingContent = {
                val label = store?.label?.takeIf { it.isNotEmpty() }
                Text(label ?: stringResource(R.string.detail_store_none))
            },
            trailingContent = {
                Icon(Icons.Default.Edit, stringResource(R.string.detail_store_edit))
            },
        )
    }
    ListItem(
        modifier = Modifier.clickable { onIncludedChange(!included) },
        headlineContent = { Text(stringResource(R.string.detail_include_in_stats)) },
        trailingContent = { Switch(checked = included, onCheckedChange = onIncludedChange) },
    )
}
