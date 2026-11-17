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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Settings
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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.eksoda.core.designsystem.component.CategoryTile
import io.eksoda.core.designsystem.component.ChangeBadge
import io.eksoda.core.designsystem.component.ChangeDirection
import io.eksoda.core.designsystem.component.MerchantAvatar
import io.eksoda.core.designsystem.component.OverlineText
import io.eksoda.core.designsystem.component.SectionCard
import io.eksoda.core.designsystem.text.monoNumbers
import io.eksoda.core.designsystem.theme.EksodaTheme
import io.eksoda.core.model.CategorySpending
import io.eksoda.core.model.DocumentKind
import io.eksoda.core.model.Money
import io.eksoda.core.model.MonthPace
import io.eksoda.core.model.Receipt
import io.eksoda.core.model.SpendingBucketKind
import io.eksoda.core.ui.component.EmptyState
import io.eksoda.core.ui.component.HeroMoney
import io.eksoda.core.ui.component.MoneyText
import io.eksoda.core.ui.text.appLocale
import io.eksoda.core.ui.text.bucketName
import io.eksoda.core.ui.text.dateFormatter
import io.eksoda.core.ui.text.dayLabel
import io.eksoda.core.ui.text.formatMoney
import io.eksoda.core.ui.text.formatPercent
import java.math.RoundingMode
import java.time.LocalDate

/**
 * Home (docs/design/ui-redesign.md §5): this month at a glance, kept short on purpose. The
 * details (the day-by-day chart, receipts, averages, monthly trend, shares) are in Insights,
 * which every card here opens.
 */
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
            if (pace != null) HeroCard(pace, today, onInsightsClick)
            AttentionList(state.needsReview, state.importing, onReviewClick, onImportsClick)
            if (state.recent.isEmpty() && pace?.spentSoFar?.signum() == 0) {
                EmptyState(
                    title = stringResource(R.string.home_empty_title),
                    description = stringResource(R.string.home_empty_body),
                    icon = Icons.AutoMirrored.Outlined.ReceiptLong,
                )
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
                    Icons.Outlined.Settings,
                    contentDescription = stringResource(R.string.home_settings),
                    tint = colors.ink2,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/** "Spent in November so far" and one line against the same day last month. */
@Composable
private fun HeroCard(pace: MonthPace, today: LocalDate, onClick: () -> Unit) {
    val colors = EksodaTheme.colors
    val locale = appLocale()
    val lastMonth = pace.month.minusMonths(1)
    val sameDay = lastMonth.atDay(today.dayOfMonth.coerceAtMost(lastMonth.lengthOfMonth()))
    SectionCard(modifier = Modifier.clickable(onClick = onClick)) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(
                    R.string.home_spent_so_far,
                    pace.month.format(dateFormatter("MMMM", locale)),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.ink2,
            )
            HeroMoney(Money(pace.spentSoFar, pace.currency))
            PaceChange(pace, sameDay)
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

/** The top three buckets of this month: name and amount (shares are in Insights). */
@Composable
private fun WhereItWentCard(categories: CategorySpending, onAllClick: () -> Unit) {
    val colors = EksodaTheme.colors
    val top = categories.buckets.filter { it.total.signum() > 0 }.take(TOP_CATEGORIES)
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
                CategoryTile(
                    slug = bucket.category?.slug.takeIf {
                        bucket.kind == SpendingBucketKind.CATEGORY
                    },
                    size = 34.dp,
                )
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
