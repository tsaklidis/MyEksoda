package io.eksoda.feature.products.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.eksoda.core.designsystem.chart.LineChart
import io.eksoda.core.designsystem.chart.MinMaxBar
import io.eksoda.core.designsystem.chart.RangeDotPlot
import io.eksoda.core.designsystem.chart.RangeRow
import io.eksoda.core.designsystem.component.ChangeBadge
import io.eksoda.core.designsystem.component.ChangeDirection
import io.eksoda.core.designsystem.component.HeroAmount
import io.eksoda.core.designsystem.component.OverlineText
import io.eksoda.core.designsystem.component.SectionCard
import io.eksoda.core.designsystem.component.SegmentedToggle
import io.eksoda.core.designsystem.component.StatItem
import io.eksoda.core.designsystem.component.StatRow
import io.eksoda.core.designsystem.text.monoNumbers
import io.eksoda.core.designsystem.theme.ButtonShape
import io.eksoda.core.designsystem.theme.EksodaTheme
import io.eksoda.core.designsystem.theme.PlexMono
import io.eksoda.core.designsystem.theme.tabular
import io.eksoda.core.model.AvailableUnit
import io.eksoda.core.model.ComparisonReason
import io.eksoda.core.model.Money
import io.eksoda.core.model.PriceComparison
import io.eksoda.core.model.PriceEntry
import io.eksoda.core.model.PriceStats
import io.eksoda.core.ui.component.MoneyText
import io.eksoda.core.ui.text.appLocale
import io.eksoda.core.ui.text.currencySymbol
import io.eksoda.core.ui.text.dateFormatter
import io.eksoda.core.ui.text.formatAmount
import io.eksoda.core.ui.text.formatMoney
import io.eksoda.core.ui.text.formatQuantity
import io.eksoda.feature.products.R
import io.eksoda.feature.products.common.unitLabel
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.time.YearMonth

/** "€1.29/l". */
@Composable
internal fun pricePerUnit(price: BigDecimal, currency: String, unit: String?): String =
    stringResource(
        R.string.price_per_unit,
        formatMoney(Money(price, currency), appLocale()),
        unitLabel(unit),
    )

/** "€ / l": the unit after a hero price. */
@Composable
private fun unitSuffix(currency: String, unit: String?): String =
    "${currencySymbol(currency, appLocale())} / ${unitLabel(unit)}"

/** "Feb · Lidl": when and where a purchase was made. */
@Composable
private fun monthAndShop(entry: PriceEntry): String {
    val locale = appLocale()
    val month = entry.issuedAt.format(dateFormatter("LLL", locale))
        .replaceFirstChar { it.titlecase(locale) }
    return "$month · ${entry.merchant.name}"
}

/**
 * The price card (docs/design/ui-redesign.md §5): the last price paid in periwinkle with where and
 * when, the change from the purchase before as a badge, the lowest–highest range with the average,
 * and Lowest · Average · Highest with their month and merchant.
 */
@Composable
internal fun PriceCard(stats: PriceStats, priced: List<PriceEntry>) {
    val locale = appLocale()
    val colors = EksodaTheme.colors
    val latest = priced.firstOrNull()
    val extremes = remember(priced) { priceExtremes(priced) }
    val money = { value: BigDecimal -> formatMoney(Money(value, stats.currency), locale) }
    val purchases = pluralStringResource(R.plurals.stats_purchases, stats.count, stats.count)
    SectionCard {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (latest != null) {
                    Text(
                        text = stringResource(
                            R.string.last_paid,
                            latest.issuedAt.format(dateFormatter("d MMM", locale)),
                            latest.merchant.name,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.ink2,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                HeroAmount(
                    number = formatAmount(stats.latest, locale),
                    unit = unitSuffix(stats.currency, stats.normalizedUnit),
                    color = colors.compare,
                )
                PriceChangeBadge(stats)
            }
            MinMaxBar(
                minimum = stats.minimum,
                average = stats.average,
                maximum = stats.maximum,
                lowLabel = stringResource(R.string.stats_min_short),
                lowValue = formatAmount(stats.minimum, locale),
                middleText = stringResource(
                    R.string.stats_avg_purchases,
                    formatAmount(stats.average, locale),
                    purchases,
                ),
                highLabel = stringResource(R.string.stats_max_short),
                highValue = formatAmount(stats.maximum, locale),
                contentDescription = stringResource(
                    R.string.price_range_description,
                    money(stats.minimum),
                    money(stats.average),
                    money(stats.maximum),
                ),
            )
            StatRow(
                listOf(
                    StatItem(
                        label = stringResource(R.string.stats_minimum),
                        value = money(stats.minimum),
                        supporting = extremes?.let { monthAndShop(it.lowest) },
                        labelColor = colors.compareText,
                    ),
                    StatItem(
                        label = stringResource(R.string.stats_average),
                        value = money(stats.average),
                        supporting = purchases,
                    ),
                    StatItem(
                        label = stringResource(R.string.stats_maximum),
                        value = money(stats.maximum),
                        supporting = extremes?.let { monthAndShop(it.highest) },
                        labelColor = colors.spendText,
                    ),
                ),
            )
        }
    }
}

/** "▲ 0,10 € more than last time"; "— First purchase" when there is no purchase before. */
@Composable
private fun PriceChangeBadge(stats: PriceStats) {
    val previous = stats.previous
    if (previous == null) {
        ChangeBadge(ChangeDirection.NONE, stringResource(R.string.price_change_first))
        return
    }
    val difference = stats.latest - previous
    val amount = formatMoney(Money(difference.abs(), stats.currency), appLocale())
    when (difference.signum()) {
        1 -> ChangeBadge(ChangeDirection.UP, stringResource(R.string.price_change_more, amount))
        -1 -> ChangeBadge(ChangeDirection.DOWN, stringResource(R.string.price_change_less, amount))
        else -> ChangeBadge(ChangeDirection.NONE, stringResource(R.string.price_change_same))
    }
}

/**
 * The price per unit at each change of price (§5, [pricePoints]): the prices in periwinkle,
 * the average dashed, the lowest and highest ringed, the first, middle and last purchase dates
 * below. Hidden with fewer than two points.
 */
@Composable
internal fun PriceTrendCard(stats: PriceStats, priced: List<PriceEntry>) {
    val points = remember(priced) { pricePoints(priced) }
    if (points.size < 2) return
    val distinct = remember(priced) { distinctPrices(priced) }
    val locale = appLocale()
    val colors = EksodaTheme.colors
    val dateFormat = dateFormatter("MMM yy", locale)
    val present = points.map { it.price }
    val description = stringResource(
        R.string.chart_description,
        formatMoney(Money(present.min(), stats.currency), locale),
        formatMoney(Money(present.max(), stats.currency), locale),
    )
    SectionCard(
        title = stringResource(chartTitle(stats.normalizedUnit)),
        action = {
            Text(
                text = pluralStringResource(R.plurals.chart_points, distinct, distinct),
                style = MaterialTheme.typography.bodySmall,
                color = colors.ink2,
            )
        },
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LineChart(
                values = present,
                contentDescription = description,
                formatValue = { formatAmount(it, locale) },
                color = colors.compare,
                average = stats.average,
                markExtremes = true,
                axisLabels = axisIndexes(points).map { index ->
                    points[index].entry.issuedAt.format(dateFormat)
                        .replaceFirstChar { it.titlecase(locale) }
                },
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LegendDash(colors.guide)
                Text(
                    text = monoNumbers(
                        stringResource(
                            R.string.chart_legend_average,
                            formatMoney(Money(stats.average, stats.currency), locale),
                        ),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.ink2,
                )
                LegendRing(colors.compareText)
                Text(
                    text = stringResource(R.string.stats_minimum),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.ink2,
                )
                LegendRing(colors.spend)
                Text(
                    text = stringResource(R.string.stats_maximum),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.ink2,
                )
            }
        }
    }
}

private fun chartTitle(unit: String): Int = when (unit) {
    "kg" -> R.string.chart_title_kg
    "l" -> R.string.chart_title_l
    "piece" -> R.string.chart_title_piece
    else -> R.string.chart_title
}

@Composable
private fun LegendDash(color: Color) {
    Canvas(modifier = Modifier.width(14.dp).heightIn(min = 2.dp)) {
        drawLine(
            color = color,
            start = Offset(0f, size.height / 2),
            end = Offset(size.width, size.height / 2),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx())),
        )
    }
}

@Composable
private fun LegendRing(color: Color) {
    Box(modifier = Modifier.size(10.dp).border(2.dp, color, CircleShape))
}

/** A switch between the units the product was bought in, when there are several. */
@Composable
internal fun UnitSwitch(units: List<AvailableUnit>, selected: String?, onSelect: (String) -> Unit) {
    val distinct = units.map { it.normalizedUnit }.distinct()
    if (distinct.size < 2) return
    SegmentedToggle(
        options = distinct.map { unitLabel(it) },
        selectedIndex = distinct.indexOf(selected).coerceAtLeast(0),
        onSelect = { onSelect(distinct[it]) },
    )
}

/**
 * "Where it's cheapest" (§5): a sentence ("Lidl is 0,13 €/l cheaper than AB Vasilopoulos on
 * average"), then each merchant's range and average on a shared axis; or why there is no ranking.
 */
@Composable
internal fun ComparisonContent(comparison: PriceComparison, onEditClick: () -> Unit) {
    when {
        comparison.comparable -> RankedMerchants(comparison)

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
private fun RankedMerchants(comparison: PriceComparison) {
    val locale = appLocale()
    val ranked = comparison.merchants.sortedBy { it.rank ?: Int.MAX_VALUE }
    val gap = remember(comparison) { cheapestGap(comparison.merchants) }
    val rows = ranked.map { prices ->
        val stats = prices.stats
        RangeRow(
            label = prices.merchant.name,
            value = formatMoney(Money(stats.average, stats.currency), locale),
            supporting = pluralStringResource(R.plurals.stats_purchases, stats.count, stats.count),
            minimum = stats.minimum,
            average = stats.average,
            maximum = stats.maximum,
            highlighted = prices.rank == 1,
        )
    }
    // "0,13 €/l" and the sentence around it.
    val amount = gap?.let {
        pricePerUnit(it.difference, it.cheapest.stats.currency, it.cheapest.stats.normalizedUnit)
    }
    val sentence = if (gap != null && amount != null) {
        stringResource(
            R.string.cheapest_sentence,
            gap.cheapest.merchant.name,
            amount,
            gap.next.merchant.name,
        )
    } else {
        null
    }
    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (gap != null && amount != null && sentence != null) {
            CheapestCallout(sentence, shop = gap.cheapest.merchant.name, amount = amount)
        }
        RangeDotPlot(
            rows = rows,
            legend = stringResource(R.string.range_legend),
            contentDescription = sentence ?: stringResource(R.string.comparison_title),
            formatAxis = { formatAmount(it, locale) },
        )
    }
}

/** The sentence on the periwinkle tint, the shop in bold and the amount in Plex Mono. */
@Composable
private fun CheapestCallout(sentence: String, shop: String, amount: String) {
    val colors = EksodaTheme.colors
    val text = buildAnnotatedString {
        append(sentence)
        sentence.indexOf(shop).takeIf { it >= 0 }?.let { start ->
            addStyle(SpanStyle(fontWeight = FontWeight.SemiBold), start, start + shop.length)
        }
        sentence.indexOf(amount).takeIf { it >= 0 }?.let { start ->
            addStyle(
                SpanStyle(
                    fontFamily = PlexMono,
                    fontWeight = FontWeight.Medium,
                    color = colors.compareText,
                ),
                start,
                start + amount.length,
            )
        }
    }
    Surface(
        shape = ButtonShape,
        color = colors.compareTint,
        contentColor = colors.ink,
        border = BorderStroke(1.dp, colors.compareBorder),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.LocalOffer,
                contentDescription = null,
                tint = colors.compareText,
                modifier = Modifier.size(18.dp),
            )
            Text(text = text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/**
 * One purchase (§5): the day over the month, the shop with quantity × unit price, and the
 * amount. Tapping opens the receipt.
 */
@Composable
internal fun PurchaseRow(
    entry: PriceEntry,
    thisMonth: YearMonth,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = appLocale()
    val colors = EksodaTheme.colors
    val shop = listOfNotNull(entry.merchant.name, entry.store?.label?.ifEmpty { null })
        .joinToString(", ")
    val quantity = entry.quantity?.let { formatQuantity(it, locale) }
    val price = entry.normalizedUnitPrice
    val detail = listOfNotNull(
        quantity?.let { stringResource(R.string.quantity_times, it) },
        price?.let { pricePerUnit(it, entry.grossAmount.currency, entry.normalizedUnit) },
    ).joinToString(" ")
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            modifier = Modifier.width(44.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = entry.issuedAt.format(dateFormatter("dd", locale)),
                style = MaterialTheme.typography.titleMedium.tabular(),
                color = colors.ink,
            )
            OverlineText(monthLabel(entry.issuedAt, thisMonth))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = shop,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (detail.isNotEmpty()) {
                Text(
                    text = monoNumbers(detail),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.ink2,
                    maxLines = 1,
                )
            }
        }
        MoneyText(entry.grossAmount, style = MaterialTheme.typography.bodyMedium)
    }
}

/** "Nov"; "Nov 25" for another year. */
@Composable
private fun monthLabel(issuedAt: OffsetDateTime, thisMonth: YearMonth): String {
    val pattern = if (issuedAt.year == thisMonth.year) "MMM" else "MMM yy"
    return issuedAt.format(dateFormatter(pattern, appLocale()))
}
