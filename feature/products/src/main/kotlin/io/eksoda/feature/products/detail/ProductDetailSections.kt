package io.eksoda.feature.products.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.eksoda.core.designsystem.component.ChangeBadge
import io.eksoda.core.designsystem.component.ChangeDirection
import io.eksoda.core.designsystem.component.MerchantAvatar
import io.eksoda.core.designsystem.component.StatTile
import io.eksoda.core.designsystem.theme.tabular
import io.eksoda.core.model.AvailableUnit
import io.eksoda.core.model.ComparisonReason
import io.eksoda.core.model.MerchantPrices
import io.eksoda.core.model.Money
import io.eksoda.core.model.PriceComparison
import io.eksoda.core.model.PriceEntry
import io.eksoda.core.model.PriceStats
import io.eksoda.core.ui.component.MoneyText
import io.eksoda.core.ui.text.appLocale
import io.eksoda.core.ui.text.dateFormatter
import io.eksoda.core.ui.text.formatMoney
import io.eksoda.core.ui.text.formatQuantity
import io.eksoda.feature.products.R
import io.eksoda.feature.products.common.unitLabel
import java.math.BigDecimal
import java.math.RoundingMode

/** "€1.29 / l". */
@Composable
internal fun pricePerUnit(price: BigDecimal, currency: String, unit: String?): String =
    stringResource(
        R.string.price_per_unit,
        formatMoney(Money(price, currency), appLocale()),
        unitLabel(unit),
    )

/** Last paid (with the change from the purchase before), lowest, highest and average. */
@Composable
internal fun StatsGrid(stats: PriceStats) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(
                label = stringResource(R.string.stats_latest),
                modifier = Modifier.weight(1f),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(pricePerUnit(stats.latest, stats.currency, stats.normalizedUnit))
                    stats.previous?.let { previous -> PriceChange(stats.latest, previous) }
                }
            }
            StatTile(
                label = stringResource(R.string.stats_average),
                modifier = Modifier.weight(1f),
                supporting = pluralStringResource(
                    R.plurals.stats_purchases,
                    stats.count,
                    stats.count,
                ),
            ) {
                Text(pricePerUnit(stats.average, stats.currency, stats.normalizedUnit))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(
                label = stringResource(R.string.stats_minimum),
                modifier = Modifier.weight(1f),
            ) { Text(pricePerUnit(stats.minimum, stats.currency, stats.normalizedUnit)) }
            StatTile(
                label = stringResource(R.string.stats_maximum),
                modifier = Modifier.weight(1f),
            ) { Text(pricePerUnit(stats.maximum, stats.currency, stats.normalizedUnit)) }
        }
    }
}

@Composable
private fun PriceChange(latest: BigDecimal, previous: BigDecimal) {
    val direction = when (latest.compareTo(previous)) {
        1 -> ChangeDirection.UP
        -1 -> ChangeDirection.DOWN
        else -> ChangeDirection.NONE
    }
    val percent = if (previous.signum() == 0) {
        BigDecimal.ZERO
    } else {
        (latest - previous).abs().multiply(HUNDRED).divide(previous, 0, RoundingMode.HALF_UP)
    }
    ChangeBadge(direction, stringResource(R.string.percent, percent.toPlainString()))
}

/** A switch between the units the product was bought in, when there are several. */
@Composable
internal fun UnitSwitch(units: List<AvailableUnit>, selected: String?, onSelect: (String) -> Unit) {
    val distinct = units.map { it.normalizedUnit }.distinct()
    if (distinct.size < 2) return
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        distinct.forEachIndexed { index, unit ->
            SegmentedButton(
                selected = unit == selected,
                onClick = { onSelect(unit) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = distinct.size),
            ) { Text(unitLabel(unit)) }
        }
    }
}

/** "Where it's cheapest": merchants ranked by average price, or why they are not. */
@Composable
internal fun ComparisonContent(comparison: PriceComparison, onEditClick: () -> Unit) {
    when {
        comparison.comparable -> comparison.merchants.forEach { MerchantPriceRow(it) }

        comparison.reason == ComparisonReason.ONE_MERCHANT -> Text(
            text = stringResource(
                R.string.comparison_one_merchant,
                comparison.merchants.firstOrNull()?.merchant?.name.orEmpty(),
            ),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        comparison.reason == ComparisonReason.UNKNOWN_UNIT -> Column(
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            Text(
                text = stringResource(R.string.comparison_unknown_unit),
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = onEditClick) { Text(stringResource(R.string.product_edit)) }
        }
    }
}

@Composable
private fun MerchantPriceRow(prices: MerchantPrices) {
    val stats = prices.stats
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = prices.rank?.toString().orEmpty(),
            style = MaterialTheme.typography.titleMedium.tabular(),
            fontWeight = if (prices.rank == 1) FontWeight.Bold else null,
            color = if (prices.rank == 1) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.width(20.dp),
        )
        MerchantAvatar(prices.merchant.id, prices.merchant.name, size = 32.dp)
        Text(
            text = prices.merchant.name,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = pricePerUnit(stats.average, stats.currency, stats.normalizedUnit),
            style = MaterialTheme.typography.bodyMedium.tabular(),
            fontWeight = if (prices.rank == 1) FontWeight.Bold else null,
        )
    }
}

/** One purchase: day, shop, quantity and amount. Tapping opens the receipt. */
@Composable
internal fun PurchaseRow(entry: PriceEntry, onClick: () -> Unit) {
    val locale = appLocale()
    val shop = listOfNotNull(entry.merchant.name, entry.store?.label?.ifEmpty { null })
        .joinToString(", ")
    val quantity = entry.quantity?.let { formatQuantity(it, locale) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = entry.issuedAt.format(dateFormatter("d MMM yy", locale)),
            style = MaterialTheme.typography.bodySmall.tabular(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(64.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(shop, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val price = entry.normalizedUnitPrice
            if (price != null) {
                Text(
                    text = listOfNotNull(
                        quantity?.let { stringResource(R.string.quantity_times, it) },
                        pricePerUnit(price, entry.grossAmount.currency, entry.normalizedUnit),
                    ).joinToString(" "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        MoneyText(entry.grossAmount, style = MaterialTheme.typography.bodyMedium)
    }
}

private val HUNDRED = BigDecimal(100)
