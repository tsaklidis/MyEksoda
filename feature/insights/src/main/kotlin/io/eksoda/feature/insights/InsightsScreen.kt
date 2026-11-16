package io.eksoda.feature.insights

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.eksoda.core.designsystem.chart.BarChart
import io.eksoda.core.designsystem.chart.BarDatum
import io.eksoda.core.designsystem.chart.DonutChart
import io.eksoda.core.designsystem.chart.DonutItem
import io.eksoda.core.designsystem.chart.RankedItem
import io.eksoda.core.designsystem.chart.ShareBar
import io.eksoda.core.designsystem.chart.rankByValue
import io.eksoda.core.designsystem.component.ChangeBadge
import io.eksoda.core.designsystem.component.ChangeDirection
import io.eksoda.core.designsystem.component.OverlineText
import io.eksoda.core.designsystem.component.PeriodSelector
import io.eksoda.core.designsystem.component.SectionCard
import io.eksoda.core.designsystem.component.SegmentedToggle
import io.eksoda.core.designsystem.component.StatItem
import io.eksoda.core.designsystem.component.StatRow
import io.eksoda.core.designsystem.text.monoNumbers
import io.eksoda.core.designsystem.theme.EksodaTheme
import io.eksoda.core.designsystem.theme.tabular
import io.eksoda.core.model.CategorySpending
import io.eksoda.core.model.MerchantSpending
import io.eksoda.core.model.Money
import io.eksoda.core.model.MonthlyTrend
import io.eksoda.core.model.Period
import io.eksoda.core.model.SpendingBucket
import io.eksoda.core.model.SpendingBucketKind
import io.eksoda.core.model.SpendingSummary
import io.eksoda.core.model.TopProduct
import io.eksoda.core.model.percentOf
import io.eksoda.core.ui.component.DelayedProgressIndicator
import io.eksoda.core.ui.component.HeroMoney
import io.eksoda.core.ui.component.MoneyText
import io.eksoda.core.ui.text.appLocale
import io.eksoda.core.ui.text.bucketName
import io.eksoda.core.ui.text.dateFormatter
import io.eksoda.core.ui.text.formatMoney
import io.eksoda.core.ui.text.formatPercent
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

/** Insights (docs/design/ui-redesign.md §5): a period, its summary and its breakdowns. */
@Composable
fun InsightsRoute(
    onProductClick: (String) -> Unit,
    onProductsClick: () -> Unit,
    onMerchantsClick: () -> Unit,
    viewModel: InsightsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // The screen has no Scaffold, so it sets the text color itself.
    CompositionLocalProvider(LocalContentColor provides EksodaTheme.colors.ink) {
        InsightsScreen(
            state = state,
            onEvent = viewModel::onEvent,
            onProductClick = onProductClick,
            onAssignCategories = onProductsClick,
            onMerchantsClick = onMerchantsClick,
        )
    }
}

@Composable
fun InsightsScreen(
    state: InsightsUiState,
    onEvent: (InsightsEvent) -> Unit,
    onProductClick: (String) -> Unit,
    onAssignCategories: () -> Unit,
    onMerchantsClick: () -> Unit,
) {
    var showHelp by rememberSaveable { mutableStateOf(false) }
    val currency = state.currency()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 20.dp),
    ) {
        Header(state.range, state.summary, onHelp = { showHelp = true })
        PeriodSelector(
            labels = PERIODS.map { stringResource(it.second) },
            selectedIndex = PERIODS.indexOfFirst { it.first == state.period },
            onSelect = { onEvent(InsightsEvent.PeriodSelected(PERIODS[it].first)) },
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 16.dp),
        )
        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val retry = { onEvent(InsightsEvent.Retry) }
            Card(state.summary, retry) { SummaryCard(it) }
            Card(state.categories, retry) {
                if (it.buckets.isNotEmpty()) CategoryCard(it, onAssignCategories)
            }
            Card(state.merchants, retry) {
                if (it.isNotEmpty()) MerchantCard(it, currency, onMerchantsClick)
            }
            val today = state.today
            Card(state.trend, retry) {
                if (today != null && it.months.any { month -> month.receipts > 0 }) {
                    TrendCard(it, today)
                }
            }
            TopProductsCard(state.topBySpend, state.topByCount, currency, onProductClick, retry)
        }
    }
    if (showHelp) {
        AlertDialog(
            onDismissRequest = { showHelp = false },
            title = { Text(stringResource(R.string.insights_help_title)) },
            text = { Text(stringResource(R.string.insights_help_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showHelp = false
                }) { Text(stringResource(R.string.insights_ok)) }
            },
        )
    }
}

/** The household's currency, from whichever card has loaded (all amounts are in it). */
private fun InsightsUiState.currency(): String = (summary as? CardState.Ready)?.value?.currency
    ?: (categories as? CardState.Ready)?.value?.currency
    ?: (trend as? CardState.Ready)?.value?.currency
    ?: DEFAULT_CURRENCY

private const val DEFAULT_CURRENCY = "EUR"

private val PERIODS = listOf(
    InsightsPeriod.THIS_MONTH to R.string.insights_period_this_month,
    InsightsPeriod.LAST_MONTH to R.string.insights_period_last_month,
    InsightsPeriod.THREE_MONTHS to R.string.insights_period_3_months,
    InsightsPeriod.YEAR to R.string.insights_period_year,
)

@Composable
private fun <T> Card(card: CardState<T>, onRetry: () -> Unit, content: @Composable (T) -> Unit) {
    when (card) {
        CardState.Loading -> Box(Modifier.fillMaxWidth().heightIn(min = 120.dp), Alignment.Center) {
            DelayedProgressIndicator()
        }

        CardState.Failed -> SectionCard {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.insights_card_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = EksodaTheme.colors.ink2,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onRetry) { Text(stringResource(R.string.insights_retry)) }
            }
        }

        is CardState.Ready -> content(card.value)
    }
}

@Composable
private fun Header(range: Period?, summary: CardState<SpendingSummary>, onHelp: () -> Unit) {
    val colors = EksodaTheme.colors
    val locale = appLocale()
    val currency = (summary as? CardState.Ready)?.value?.currency
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val overline = listOfNotNull(
                range?.let {
                    rangeLabel(it, locale, withYear = true)
                },
                currency,
            )
                .joinToString(" · ")
            OverlineText(overline)
            Text(
                stringResource(R.string.insights_title),
                style = MaterialTheme.typography.headlineMedium,
                color = colors.ink,
                modifier = Modifier.semantics { heading() },
            )
        }
        Surface(
            onClick = onHelp,
            shape = RoundedCornerShape(12.dp),
            color = colors.panel,
            border = BorderStroke(1.dp, colors.line),
            modifier = Modifier.size(44.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Outlined.Info,
                    contentDescription = stringResource(R.string.insights_help_title),
                    tint = colors.ink2,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/** "1–15 Νοεμβρίου 2026", "17–31 Οκτ", "1 Σεπ – 15 Νοε". */
private fun rangeLabel(period: Period, locale: Locale, withYear: Boolean): String {
    val start = period.start
    val end = period.end
    val year = if (withYear) " yyyy" else ""
    return if (start.year == end.year && start.month == end.month) {
        val monthPattern = if (withYear) "MMMM" else "MMM"
        "${start.dayOfMonth}–" + end.format(dateFormatter("d $monthPattern$year", locale))
    } else {
        start.format(dateFormatter("d MMM", locale)) + " – " +
            end.format(dateFormatter("d MMM$year", locale))
    }
}

@Composable
private fun SummaryCard(summary: SpendingSummary) {
    val colors = EksodaTheme.colors
    val locale = appLocale()
    val currency = summary.currency
    val previous = summary.previous
    SectionCard {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.insights_spending),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.ink2,
                )
                HeroMoney(Money(summary.current.spending, currency))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    val percent = summary.changePercent
                    val previousRange =
                        rangeLabel(Period.Range(previous.from, previous.to), locale, false)
                    val previousAmount = formatMoney(Money(previous.spending, currency), locale)
                    if (percent == null) {
                        ChangeBadge(ChangeDirection.NONE, "")
                        Text(
                            monoNumbers(
                                stringResource(R.string.insights_no_previous, previousRange),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.ink2,
                        )
                    } else {
                        val shown = percent.setScale(1, RoundingMode.HALF_UP)
                        val amount = formatPercent(shown, locale)
                        val (direction, words) = when (shown.signum()) {
                            1 ->
                                ChangeDirection.UP to
                                    stringResource(R.string.insights_change_more, amount)

                            -1 ->
                                ChangeDirection.DOWN to
                                    stringResource(R.string.insights_change_less, amount)

                            else ->
                                ChangeDirection.NONE to
                                    stringResource(R.string.insights_change_same)
                        }
                        ChangeBadge(direction, words)
                        Text(
                            monoNumbers(
                                stringResource(
                                    R.string.insights_change_than,
                                    previousRange,
                                    previousAmount,
                                ),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.ink2,
                        )
                    }
                }
            }
            val refunds = summary.current.creditNotesTotal
            StatRow(
                items = listOf(
                    StatItem(
                        stringResource(R.string.insights_receipts),
                        summary.current.receiptCount.toString(),
                    ),
                    StatItem(
                        stringResource(R.string.insights_average),
                        summary.current.averageSale?.let {
                            formatMoney(Money(it, currency), locale)
                        }
                            ?: "—",
                    ),
                    StatItem(
                        stringResource(R.string.insights_refunds),
                        formatMoney(
                            Money(refunds, currency),
                            locale,
                            negative =
                            refunds.signum() != 0,
                        ),
                    ),
                ),
            )
            val other = summary.current.otherCurrencyReceipts
            Text(
                buildString {
                    append(stringResource(R.string.insights_note))
                    if (other > 0) {
                        append(' ')
                        append(
                            pluralStringResource(R.plurals.insights_other_currency, other, other),
                        )
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = colors.ink2,
            )
        }
    }
}

/** A stable key for a bucket, to find it again after ranking. */
private fun SpendingBucket.key(): String = kind.code + ":" + category?.id.orEmpty()

@Composable
private fun CategoryCard(spending: CategorySpending, onAssignCategories: () -> Unit) {
    val colors = EksodaTheme.colors
    val locale = appLocale()
    val byKey = spending.buckets.associateBy { it.key() }
    val ranked = rankByValue(
        spending.buckets.map {
            DonutItem(it.key(), it.total, isBucket = it.kind != SpendingBucketKind.CATEGORY)
        },
        colors,
    )
    // Negative buckets (discounts) are not in the chart, but the list shows them.
    val negative = spending.buckets.filter { it.total.signum() < 0 }
    val names = spending.buckets.associate { it.key() to bucketName(it) }
    SectionCard(title = stringResource(R.string.insights_by_category)) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (ranked.isNotEmpty()) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val top = ranked.take(3).joinToString(", ") { item ->
                        val share = byKey[item.key]?.share?.let {
                            formatPercent(it, locale)
                        }.orEmpty()
                        "${names[item.key]} $share"
                    }
                    DonutChart(
                        items = ranked,
                        diameter = 190.dp,
                        contentDescription = stringResource(
                            R.string.insights_donut_description,
                            top,
                        ),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            MoneyText(
                                Money(spending.total, spending.currency),
                                style = MaterialTheme.typography.titleLarge,
                            )
                            OverlineText(
                                pluralStringResource(
                                    R.plurals.insights_groups,
                                    spending.buckets.size,
                                    spending.buckets.size,
                                ),
                            )
                        }
                    }
                }
            }
            Column {
                var groupedHeaderShown = false
                ranked.forEach { item ->
                    val bucket = byKey[item.key] ?: return@forEach
                    if (item.grouped && !groupedHeaderShown) {
                        groupedHeaderShown = true
                        OverlineText(
                            stringResource(R.string.insights_grouped),
                            modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 2.dp),
                        )
                    }
                    CategoryRow(
                        bucket = bucket,
                        name = names[item.key].orEmpty(),
                        swatch = item,
                        currency = spending.currency,
                        onAssignCategories = onAssignCategories,
                    )
                }
                negative.forEach { bucket ->
                    CategoryRow(
                        bucket = bucket,
                        name = names[bucket.key()].orEmpty(),
                        swatch = null,
                        currency = spending.currency,
                        onAssignCategories = onAssignCategories,
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryRow(
    bucket: SpendingBucket,
    name: String,
    swatch: RankedItem?,
    currency: String,
    onAssignCategories: () -> Unit,
) {
    val colors = EksodaTheme.colors
    val locale = appLocale()
    val assign = bucket.kind == SpendingBucketKind.UNCATEGORIZED
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = if (assign) 52.dp else 44.dp)
            .then(if (assign) Modifier.clickable(onClick = onAssignCategories) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val isBucket = bucket.kind != SpendingBucketKind.CATEGORY
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(swatch?.color ?: Color.Transparent)
                .then(
                    if (isBucket || swatch == null) {
                        Modifier.border(1.dp, colors.guide, RoundedCornerShape(2.dp))
                    } else {
                        Modifier
                    },
                ),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (assign) {
                Text(
                    stringResource(R.string.insights_assign_categories),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.compare,
                )
            }
        }
        Text(
            bucket.share?.let { formatPercent(it, locale) }.orEmpty(),
            style = MaterialTheme.typography.bodySmall.tabular(),
            color = colors.ink2,
            modifier = Modifier.width(52.dp),
            maxLines = 1,
        )
        MoneyText(
            Money(bucket.total, currency),
            negative = bucket.total.signum() < 0,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    HorizontalDivider(color = colors.line)
}

@Composable
private fun MerchantCard(
    merchants: List<MerchantSpending>,
    currency: String,
    onShowAll: () -> Unit,
) {
    val colors = EksodaTheme.colors
    val locale = appLocale()
    val top = merchants.take(TOP_MERCHANTS)
    val max = top.maxOfOrNull { it.total } ?: BigDecimal.ONE
    SectionCard(
        title = stringResource(R.string.insights_by_shop),
        action = if (merchants.size > TOP_MERCHANTS) {
            {
                Text(
                    stringResource(R.string.insights_show_all, merchants.size),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.compare,
                    modifier = Modifier.clickable(onClick = onShowAll).padding(vertical = 6.dp),
                )
            }
        } else {
            null
        },
    ) {
        top.forEach { merchant ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        merchant.name,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        merchant.share?.let { formatPercent(it, locale) }.orEmpty(),
                        style = MaterialTheme.typography.bodySmall.tabular(),
                        color = colors.ink2,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    MoneyText(
                        Money(merchant.total, currency),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                ShareBar(value = merchant.total, max = max, color = colors.spend, thickness = 6.dp)
            }
        }
    }
}

@Composable
private fun TrendCard(trend: MonthlyTrend, today: LocalDate) {
    val colors = EksodaTheme.colors
    val locale = appLocale()
    val months = trend.months
    val thisMonth = YearMonth.from(today)
    val full = months.filter { it.month != thisMonth && it.receipts > 0 }
    val average = if (full.isEmpty()) {
        null
    } else {
        full.fold(BigDecimal.ZERO) { sum, month -> sum + month.total }
            .divide(BigDecimal(full.size), 2, RoundingMode.HALF_UP)
    }
    var selected by rememberSaveable(months.size) { mutableIntStateOf(months.lastIndex) }
    SectionCard(
        title = stringResource(R.string.insights_monthly_trend),
        action = average?.let {
            {
                Text(
                    monoNumbers(
                        stringResource(
                            R.string.insights_average_short,
                            formatMoney(Money(it, trend.currency), locale),
                        ),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.ink2,
                )
            }
        },
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val month = months.getOrNull(selected)
            if (month != null) {
                TrendCallout(month.month, month.total, trend.currency, average, today)
            }
            BarChart(
                // Short month names on every other bar (counted back from this month) fit
                // the width; the callout names the selected month in full.
                bars = months.mapIndexed { index, month ->
                    BarDatum(
                        label = if ((months.lastIndex - index) % 2 == 0) {
                            month.month.format(dateFormatter("LLL", locale))
                                .replaceFirstChar { char -> char.titlecase(locale) }
                        } else {
                            ""
                        },
                        value = month.total,
                        partial = month.month == thisMonth,
                    )
                },
                contentDescription = stringResource(R.string.insights_trend_description),
                selectedIndex = selected,
                onSelect = { selected = it },
                average = average,
                height = 150.dp,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Swatch(stringResource(R.string.insights_legend_selected), colors.compare)
                Swatch(
                    stringResource(
                        R.string.insights_legend_so_far,
                        thisMonth.format(dateFormatter("LLLL", locale)).replaceFirstChar {
                            it.titlecase(locale)
                        },
                    ),
                    colors.spend,
                )
                if (average !=
                    null
                ) {
                    Swatch(stringResource(R.string.insights_legend_average), colors.guide)
                }
            }
        }
    }
}

@Composable
private fun TrendCallout(
    month: YearMonth,
    total: BigDecimal,
    currency: String,
    average: BigDecimal?,
    today: LocalDate,
) {
    val colors = EksodaTheme.colors
    val locale = appLocale()
    val current = month == YearMonth.from(today)
    val (note, color) = when {
        current -> stringResource(
            R.string.insights_so_far_days,
            today.dayOfMonth,
            month.lengthOfMonth(),
        ) to colors.ink2

        average == null -> "" to colors.ink2

        else -> {
            val percent = percentOf(total - average, average)?.setScale(0, RoundingMode.HALF_UP)
            when (percent?.signum()) {
                1 ->
                    "▲ " +
                        stringResource(
                            R.string.insights_above_average,
                            formatPercent(percent, locale, 0),
                        ) to
                        colors.spendText

                -1 ->
                    "▼ " +
                        stringResource(
                            R.string.insights_below_average,
                            formatPercent(percent, locale, 0),
                        ) to
                        colors.compareText

                else -> stringResource(R.string.insights_about_average) to colors.ink2
            }
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.inset)
            .border(1.dp, colors.line, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .heightIn(min = 44.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        OverlineText(
            month.format(dateFormatter("LLLL yyyy", locale))
                .replaceFirstChar { it.titlecase(locale) },
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            itemVerticalAlignment = Alignment.Bottom,
        ) {
            MoneyText(Money(total, currency), style = MaterialTheme.typography.titleLarge)
            if (note.isNotEmpty()) {
                Text(monoNumbers(note), style = MaterialTheme.typography.bodySmall, color = color)
            }
        }
    }
}

@Composable
private fun Swatch(text: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Text(text, style = MaterialTheme.typography.bodySmall, color = EksodaTheme.colors.ink2)
    }
}

@Composable
private fun TopProductsCard(
    bySpend: CardState<List<TopProduct>>,
    byCount: CardState<List<TopProduct>>,
    currency: String,
    onProductClick: (String) -> Unit,
    onRetry: () -> Unit,
) {
    var mode by rememberSaveable { mutableIntStateOf(0) }
    val card = if (mode == 0) bySpend else byCount
    if (card is CardState.Ready && card.value.isEmpty() && mode == 0) return
    SectionCard(title = stringResource(R.string.insights_top_products)) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SegmentedToggle(
                options = listOf(
                    stringResource(R.string.insights_most_spent),
                    stringResource(R.string.insights_most_bought),
                ),
                selectedIndex = mode,
                onSelect = { mode = it },
            )
            Card(card, onRetry) { products ->
                ProductList(products, byCount = mode == 1, currency, onProductClick)
            }
        }
    }
}

@Composable
private fun ProductList(
    products: List<TopProduct>,
    byCount: Boolean,
    currency: String,
    onProductClick: (String) -> Unit,
) {
    val colors = EksodaTheme.colors
    val locale = appLocale()
    val max = if (byCount) {
        BigDecimal(products.maxOfOrNull { it.receipts } ?: 1)
    } else {
        products.maxOfOrNull { it.spend } ?: BigDecimal.ONE
    }
    products.forEachIndexed { index, product ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable { onProductClick(product.productId) }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "%02d".format(locale, index + 1),
                style = MaterialTheme.typography.bodySmall.tabular(),
                color = colors.ink2,
                modifier = Modifier.width(24.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        product.name,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                    )
                    if (byCount) {
                        Text(
                            "${product.receipts}×",
                            style = MaterialTheme.typography.bodyMedium.tabular(),
                        )
                    } else {
                        MoneyText(
                            Money(product.spend, currency),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ShareBar(
                        value = if (byCount) BigDecimal(product.receipts) else product.spend,
                        max = max,
                        color = if (byCount) colors.compare else colors.spend,
                        thickness = 4.dp,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        if (byCount) {
                            formatMoney(Money(product.spend, currency), locale)
                        } else {
                            pluralStringResource(
                                R.plurals.insights_buys,
                                product.receipts,
                                product.receipts,
                            )
                        },
                        style = MaterialTheme.typography.labelSmall.tabular(),
                        color = colors.ink2,
                    )
                }
            }
        }
    }
}

private const val TOP_MERCHANTS = 5
