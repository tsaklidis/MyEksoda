package io.eksoda.feature.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.WarningAmber
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.eksoda.core.designsystem.chart.BarChart
import io.eksoda.core.designsystem.chart.BarDatum
import io.eksoda.core.designsystem.chart.LineChart
import io.eksoda.core.designsystem.chart.ShareBar
import io.eksoda.core.designsystem.component.CategoryTile
import io.eksoda.core.designsystem.component.ChangeBadge
import io.eksoda.core.designsystem.component.ChangeDirection
import io.eksoda.core.designsystem.component.MerchantAvatar
import io.eksoda.core.designsystem.component.OverlineText
import io.eksoda.core.designsystem.component.SectionCard
import io.eksoda.core.designsystem.component.StatItem
import io.eksoda.core.designsystem.component.StatRow
import io.eksoda.core.designsystem.text.monoNumbers
import io.eksoda.core.designsystem.theme.EksodaTheme
import io.eksoda.core.designsystem.theme.tabular
import io.eksoda.core.model.CategorySpending
import io.eksoda.core.model.DocumentKind
import io.eksoda.core.model.Money
import io.eksoda.core.model.MonthPace
import io.eksoda.core.model.MonthlyTrend
import io.eksoda.core.model.Receipt
import io.eksoda.core.model.SpendingBucketKind
import io.eksoda.core.model.SpendingSummary
import io.eksoda.core.ui.component.EmptyState
import io.eksoda.core.ui.component.HeroMoney
import io.eksoda.core.ui.component.MoneyText
import io.eksoda.core.ui.text.appLocale
import io.eksoda.core.ui.text.bucketName
import io.eksoda.core.ui.text.dateFormatter
import io.eksoda.core.ui.text.dayLabel
import io.eksoda.core.ui.text.formatMoney
import io.eksoda.core.ui.text.formatPercent
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

/** Home (docs/design/ui-redesign.md §5): this month at a glance. */
@Composable
fun HomeRoute(
    onReceiptsClick: () -> Unit,
    onInsightsClick: () -> Unit,
    onReviewClick: () -> Unit,
    onImportsClick: () -> Unit,
    onReceiptClick: (String) -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // The screen has no Scaffold, so it sets the text color itself.
    CompositionLocalProvider(LocalContentColor provides EksodaTheme.colors.ink) {
        HomeScreen(
            state = state,
            onReceiptsClick = onReceiptsClick,
            onInsightsClick = onInsightsClick,
            onReviewClick = onReviewClick,
            onImportsClick = onImportsClick,
            onReceiptClick = onReceiptClick,
            onSettingsClick = onSettingsClick,
            onRetry = viewModel::retry,
        )
    }
}

@Composable
fun HomeScreen(
    state: HomeUiState,
    onReceiptsClick: () -> Unit,
    onInsightsClick: () -> Unit,
    onReviewClick: () -> Unit,
    onImportsClick: () -> Unit,
    onReceiptClick: (String) -> Unit,
    onSettingsClick: () -> Unit,
    onRetry: () -> Unit,
) {
    val today = state.today
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 20.dp),
    ) {
        Header(state.greeting, today, onSettingsClick)
        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (state.loading || today == null) return@Column
            val pace = state.pace
            if (pace != null) HeroCard(pace, state.summary, today, onInsightsClick)
            AttentionList(state.needsReview, state.importing, onReviewClick, onImportsClick)
            if (state.recent.isEmpty() && pace?.spentSoFar?.signum() == 0) {
                EmptyState(
                    title = stringResource(R.string.home_empty_title),
                    description = stringResource(R.string.home_empty_body),
                    icon = Icons.AutoMirrored.Outlined.ReceiptLong,
                )
            }
            state.trend?.let { trend ->
                if (trend.months.any { it.receipts > 0 }) MonthsCard(trend, onInsightsClick)
            }
            state.categories?.let { categories ->
                if (categories.buckets.any { it.total.signum() > 0 }) {
                    WhereItWentCard(categories, onInsightsClick)
                }
            }
            if (state.recent.isNotEmpty()) {
                RecentCard(state.recent, today, onReceiptClick, onReceiptsClick)
            }
            if (state.failed) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.home_failed),
                        style = MaterialTheme.typography.bodySmall,
                        color = EksodaTheme.colors.ink2,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.home_retry)) }
                }
            }
        }
    }
}

@Composable
private fun Header(greeting: Greeting, today: LocalDate?, onSettingsClick: () -> Unit) {
    val colors = EksodaTheme.colors
    val locale = appLocale()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            OverlineText(
                stringResource(
                    when (greeting) {
                        Greeting.MORNING -> R.string.home_greeting_morning
                        Greeting.DAY -> R.string.home_greeting_day
                        Greeting.EVENING -> R.string.home_greeting_evening
                    },
                ),
            )
            if (today != null) {
                Text(
                    text = today.format(dateFormatter("LLLL", locale))
                        .replaceFirstChar { it.titlecase(locale) },
                    style = MaterialTheme.typography.headlineMedium,
                    color = colors.ink,
                    modifier = Modifier.semantics { heading() },
                )
            }
        }
        Surface(
            onClick = onSettingsClick,
            shape = RoundedCornerShape(12.dp),
            color = colors.panel,
            border = BorderStroke(1.dp, colors.line),
            modifier = Modifier.size(44.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Outlined.Home,
                    contentDescription = stringResource(R.string.home_settings),
                    tint = colors.ink2,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/** "Spent in November so far", the change against the same day last month, the pace chart. */
@Composable
private fun HeroCard(
    pace: MonthPace,
    summary: SpendingSummary?,
    today: LocalDate,
    onClick: () -> Unit,
) {
    val colors = EksodaTheme.colors
    val locale = appLocale()
    val currency = pace.currency
    val lastMonth = pace.month.minusMonths(1)
    val sameDay = lastMonth.atDay(today.dayOfMonth.coerceAtMost(lastMonth.lengthOfMonth()))
    SectionCard(modifier = Modifier.clickable(onClick = onClick)) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(
                        R.string.home_spent_so_far,
                        pace.month.format(dateFormatter("MMMM", locale)),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.ink2,
                )
                HeroMoney(Money(pace.spentSoFar, currency))
                PaceChange(pace, sameDay)
            }
            val dayLabelFormat = dateFormatter("d MMM", locale)
            LineChart(
                values = pace.current,
                comparison = pace.previous,
                contentDescription = stringResource(R.string.home_pace_description),
                formatValue = { formatMoney(Money(it, currency), locale) },
                gridLines = 4,
                gridLabels = false,
                markLast = true,
                height = 104.dp,
                axisLabels = listOf(
                    pace.month.atDay(1).format(dayLabelFormat),
                    stringResource(R.string.home_pace_today),
                    pace.month.atEndOfMonth().format(dayLabelFormat),
                ),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LegendLine(monthName(pace.month, locale), colors.spend, dashed = false)
                LegendLine(
                    stringResource(
                        R.string.home_pace_last_month,
                        monthName(lastMonth, locale),
                        formatMoney(Money(pace.previousTotal, currency), locale),
                    ),
                    colors.compare,
                    dashed = true,
                )
            }
            val current = summary?.current
            val perDay = pace.spentSoFar
                .divide(BigDecimal(today.dayOfMonth), 2, RoundingMode.HALF_UP)
            StatRow(
                items = listOf(
                    StatItem(
                        label = stringResource(R.string.home_stat_receipts),
                        value = (current?.receiptCount ?: 0).toString(),
                    ),
                    StatItem(
                        label = stringResource(R.string.home_stat_average),
                        value = current?.averageSale
                            ?.let { formatMoney(Money(it, currency), locale) } ?: "—",
                    ),
                    StatItem(
                        label = stringResource(R.string.home_stat_per_day),
                        value = formatMoney(Money(perDay, currency), locale),
                    ),
                ),
            )
        }
    }
}

@Composable
private fun PaceChange(pace: MonthPace, sameDay: LocalDate) {
    val colors = EksodaTheme.colors
    val locale = appLocale()
    val percent = pace.changePercent
    val dayText = sameDay.format(dateFormatter("d MMM", locale))
    val previous = formatMoney(Money(pace.previousBySameDay, pace.currency), locale)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        if (percent == null) {
            ChangeBadge(ChangeDirection.NONE, "")
            Text(
                monoNumbers(
                    if (pace.previousTotal.signum() == 0) {
                        stringResource(R.string.home_change_first)
                    } else {
                        stringResource(R.string.home_change_nothing_before, dayText)
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.ink2,
            )
        } else {
            val rounded = percent.setScale(0, RoundingMode.HALF_UP)
            val amount = formatPercent(rounded, locale, decimals = 0)
            val (direction, words) = when (rounded.signum()) {
                1 -> ChangeDirection.UP to stringResource(R.string.home_change_more, amount)
                -1 -> ChangeDirection.DOWN to stringResource(R.string.home_change_less, amount)
                else -> ChangeDirection.NONE to stringResource(R.string.home_change_same)
            }
            ChangeBadge(direction, words)
            Text(
                monoNumbers(stringResource(R.string.home_change_than, dayText, previous)),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.ink2,
            )
        }
    }
}

@Composable
private fun LegendLine(text: String, color: Color, dashed: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (dashed) {
            Dashes(color, width = 4.dp, height = 2.dp)
        } else {
            Box(Modifier.size(width = 16.dp, height = 2.dp).background(color))
        }
        Text(
            monoNumbers(text),
            style = MaterialTheme.typography.bodySmall,
            color = EksodaTheme.colors.ink2,
        )
    }
}

/** A short dashed line for a legend. */
@Composable
private fun Dashes(color: Color, width: Dp, height: Dp) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(3) { Box(Modifier.size(width = width, height = height).background(color)) }
    }
}

@Composable
private fun LegendSwatch(text: String, swatch: @Composable () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        swatch()
        Text(text, style = MaterialTheme.typography.bodySmall, color = EksodaTheme.colors.ink2)
    }
}

/** "4 items to match" and "2 receipts importing…", each hidden when 0. */
@Composable
private fun AttentionList(
    needsReview: Int,
    importing: Int,
    onReviewClick: () -> Unit,
    onImportsClick: () -> Unit,
) {
    if (needsReview == 0 && importing == 0) return
    val colors = EksodaTheme.colors
    SectionCard {
        if (needsReview > 0) {
            AttentionRow(
                icon = Icons.Outlined.WarningAmber,
                tint = colors.spend,
                tile = colors.spendTint,
                title = pluralStringResource(R.plurals.home_to_match, needsReview, needsReview),
                body = stringResource(R.string.home_to_match_body),
                onClick = onReviewClick,
            )
        }
        if (needsReview > 0 && importing > 0) {
            HorizontalDivider(color = colors.line, modifier = Modifier.padding(start = 62.dp))
        }
        if (importing > 0) {
            AttentionRow(
                icon = Icons.Outlined.Sync,
                tint = colors.compare,
                tile = colors.compareTint,
                title = pluralStringResource(R.plurals.home_importing, importing, importing),
                body = stringResource(R.string.home_importing_body),
                onClick = onImportsClick,
            )
        }
    }
}

@Composable
private fun AttentionRow(
    icon: ImageVector,
    tint: Color,
    tile: Color,
    title: String,
    body: String,
    onClick: () -> Unit,
) {
    val colors = EksodaTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)).background(tile),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(monoNumbers(title), style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodySmall, color = colors.ink2)
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = colors.ink2,
        )
    }
}

/** Six months of spending, the average of the full months as a dashed line. */
@Composable
private fun MonthsCard(trend: MonthlyTrend, onClick: () -> Unit) {
    val colors = EksodaTheme.colors
    val locale = appLocale()
    val months = trend.months
    val full = months.dropLast(1).filter { it.receipts > 0 }
    val average = if (full.isEmpty()) {
        null
    } else {
        full.fold(BigDecimal.ZERO) { sum, month -> sum + month.total }
            .divide(BigDecimal(full.size), 2, RoundingMode.HALF_UP)
    }
    SectionCard(
        modifier = Modifier.clickable(onClick = onClick),
        title = stringResource(R.string.home_last_6_months),
        action = average?.let {
            {
                Text(
                    monoNumbers(
                        stringResource(
                            R.string.home_average_per_month,
                            wholeMoney(it, trend.currency, locale),
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
            BarChart(
                bars = months.mapIndexed { index, month ->
                    BarDatum(
                        label = month.month.format(dateFormatter("LLL", locale))
                            .replaceFirstChar { it.titlecase(locale) },
                        value = month.total,
                        partial = index == months.lastIndex,
                    )
                },
                contentDescription = stringResource(R.string.home_months_description),
                selectedIndex = months.lastIndex - 1,
                average = average,
                height = 74.dp,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                LegendSwatch(stringResource(R.string.home_legend_so_far)) { Square(colors.spend) }
                LegendSwatch(stringResource(R.string.home_legend_last_month)) {
                    Square(colors.compare)
                }
                if (average != null) {
                    LegendSwatch(stringResource(R.string.home_legend_average)) {
                        Dashes(colors.guide, width = 3.dp, height = 1.dp)
                    }
                }
            }
        }
    }
}

/** The top three buckets of this month with their share. */
@Composable
private fun WhereItWentCard(categories: CategorySpending, onAllClick: () -> Unit) {
    val colors = EksodaTheme.colors
    val locale = appLocale()
    val top = categories.buckets.filter { it.total.signum() > 0 }.take(TOP_CATEGORIES)
    val max = top.firstOrNull()?.total ?: BigDecimal.ONE
    SectionCard(
        title = stringResource(R.string.home_where_it_went),
        action = {
            Text(
                stringResource(R.string.home_all_categories),
                style = MaterialTheme.typography.labelLarge,
                color = colors.compare,
                modifier = Modifier.clickable(onClick = onAllClick).padding(vertical = 6.dp),
            )
        },
    ) {
        top.forEach { bucket ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .clickable(onClick = onAllClick)
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val slug = bucket.category?.slug
                CategoryTile(
                    slug = slug.takeIf { bucket.kind == SpendingBucketKind.CATEGORY },
                    size = 38.dp,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            bucketName(bucket),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        MoneyText(
                            Money(bucket.total, categories.currency),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ShareBar(
                            value = bucket.total,
                            max = max,
                            color = colors.spend,
                            thickness = 4.dp,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            bucket.share?.let { formatPercent(it, locale) }.orEmpty(),
                            style = MaterialTheme.typography.labelSmall.tabular(),
                            color = colors.ink2,
                            modifier = Modifier.width(44.dp),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentCard(
    receipts: List<Receipt>,
    today: LocalDate,
    onReceiptClick: (String) -> Unit,
    onSeeAll: () -> Unit,
) {
    val colors = EksodaTheme.colors
    val locale = appLocale()
    SectionCard(
        title = stringResource(R.string.home_recent),
        action = {
            Text(
                stringResource(R.string.home_see_all),
                style = MaterialTheme.typography.labelLarge,
                color = colors.compare,
                modifier = Modifier.clickable(onClick = onSeeAll).padding(vertical = 6.dp),
            )
        },
    ) {
        receipts.forEachIndexed { index, receipt ->
            if (index > 0) {
                HorizontalDivider(
                    color = colors.line,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            val day = dayLabel(receipt.issuedDate, today)
            val withTime = if (receipt.issuedDate >= today.minusDays(1)) {
                "$day, " + receipt.issuedAt.format(dateFormatter("HH:mm", locale))
            } else {
                day
            }
            val subtitle = listOfNotNull(withTime, receipt.store?.label?.takeIf { it.isNotEmpty() })
                .joinToString(" · ")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .clickable { onReceiptClick(receipt.id) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MerchantAvatar(receipt.merchant.id, receipt.merchant.name, size = 38.dp)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        receipt.merchant.name,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        monoNumbers(subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.ink2,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                MoneyText(
                    receipt.totalAmount,
                    negative = receipt.documentKind == DocumentKind.CREDIT_NOTE,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

private const val TOP_CATEGORIES = 3

/** "Νοέμβριος", "November": the month's name on its own, capitalized. */
private fun monthName(month: YearMonth, locale: Locale): String =
    month.format(dateFormatter("LLLL", locale)).replaceFirstChar { it.titlecase(locale) }

/** "667 €": an amount rounded to whole units, for a short label. */
private fun wholeMoney(amount: BigDecimal, currency: String, locale: Locale): String {
    val format = java.text.NumberFormat.getCurrencyInstance(locale).apply {
        runCatching { this.currency = java.util.Currency.getInstance(currency) }
        maximumFractionDigits = 0
        minimumFractionDigits = 0
        roundingMode = RoundingMode.HALF_UP
    }
    return format.format(amount)
}

/** A legend square. */
@Composable
private fun Square(color: Color) {
    Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(color))
}
