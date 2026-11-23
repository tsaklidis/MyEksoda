package io.eksoda.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.eksoda.core.designsystem.chart.ShareBar
import io.eksoda.core.designsystem.component.CategoryTile
import io.eksoda.core.designsystem.component.OverlineText
import io.eksoda.core.designsystem.component.SectionCard
import io.eksoda.core.designsystem.text.monoNumbers
import io.eksoda.core.designsystem.theme.EksodaTheme
import io.eksoda.core.designsystem.theme.HeroNumberStyle
import io.eksoda.core.model.BillsMonth
import io.eksoda.core.model.BudgetLevel
import io.eksoda.core.model.BudgetProgress
import io.eksoda.core.model.Money
import io.eksoda.core.model.Period
import io.eksoda.core.model.SpendingBucket
import io.eksoda.core.model.SpendingBucketKind
import io.eksoda.core.ui.component.EmptyState
import io.eksoda.core.ui.component.MoneyText
import io.eksoda.core.ui.text.appLocale
import io.eksoda.core.ui.text.bucketName
import io.eksoda.core.ui.text.formatMoney
import io.eksoda.core.ui.text.monthName
import io.eksoda.core.ui.text.shortMonthName
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth

/**
 * The Overview (docs/design/ui-redesign.md §5): a month's everyday spending against its budget,
 * its bills, and its categories, each of which opens its own screen. Kept short on purpose: the
 * details are in Spending.
 */
@Composable
fun HomeRoute(actions: HomeActions, viewModel: HomeViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // The screen has no Scaffold, so it sets the text color itself.
    CompositionLocalProvider(LocalContentColor provides EksodaTheme.colors.ink) {
        HomeScreen(state = state, onEvent = viewModel::onEvent, actions = actions)
    }
}

@Composable
fun HomeScreen(state: HomeUiState, onEvent: (HomeEvent) -> Unit, actions: HomeActions) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 20.dp),
    ) {
        Header(state.greeting, actions.onBudgetClick)
        val today = state.today
        val month = state.selected
        if (state.loading || today == null || month == null) return@Column
        MonthTabs(state.months, month, state.overMonths) { onEvent(HomeEvent.MonthPicked(it)) }
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            EverydayCard(state, today, month, actions.onBudgetClick)
            state.bills?.let { BillsRow(it, actions.onBillsClick) }
            AttentionList(
                needsReview = state.needsReview,
                importing = state.importing,
                onReviewClick = actions.onReviewClick,
                onImportsClick = actions.onImportsClick,
            )
            val spending = state.spending
            val buckets = spending?.buckets.orEmpty().filter { it.total.signum() > 0 }
            if (buckets.isEmpty() && state.isCurrent && state.budgets?.isEmpty != false) {
                EmptyState(
                    title = stringResource(R.string.home_empty_title),
                    description = stringResource(R.string.home_empty_body),
                    icon = Icons.AutoMirrored.Outlined.ReceiptLong,
                )
            }
            if (buckets.isNotEmpty()) {
                val end = if (state.isCurrent) today else month.atEndOfMonth()
                val period = Period.Range(month.atDay(1), end)
                CategoriesCard(
                    buckets = buckets,
                    limits = state.budgets?.categories.orEmpty(),
                    currency = spending?.currency.orEmpty(),
                    onCategoryClick = { actions.onCategoryClick(it, period) },
                    onAllClick = actions.onSpendingClick,
                )
            }
            if (state.failed) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.home_failed),
                        style = MaterialTheme.typography.bodySmall,
                        color = EksodaTheme.colors.ink2,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { onEvent(HomeEvent.Retry) }) {
                        Text(stringResource(R.string.home_retry))
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(greeting: Greeting, onBudgetClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 12.dp, top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            OverlineText(
                stringResource(
                    when (greeting) {
                        Greeting.MORNING -> R.string.home_greeting_morning
                        Greeting.DAY -> R.string.home_greeting_day
                        Greeting.EVENING -> R.string.home_greeting_evening
                    },
                ),
            )
            Text(
                stringResource(R.string.home_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
        }
        IconButton(onClick = onBudgetClick, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Outlined.Tune, contentDescription = stringResource(R.string.home_budget))
        }
    }
}

/**
 * The last months as text tabs: the picked one bold with an underline, red when that month went
 * over its budget; the others gray, with a red dot when they went over. No boxes (§1).
 */
@Composable
internal fun MonthTabs(
    months: List<YearMonth>,
    selected: YearMonth,
    over: Set<YearMonth>,
    onPick: (YearMonth) -> Unit,
) {
    val colors = EksodaTheme.colors
    val locale = appLocale()
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).selectableGroup()) {
        months.forEach { month ->
            val on = month == selected
            val wentOver = month in over
            val name = shortMonthName(month, locale)
            val description = if (wentOver) {
                stringResource(R.string.home_month_over, monthName(month, locale))
            } else {
                monthName(month, locale)
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .selectable(selected = on, role = Role.Tab, onClick = { onPick(month) })
                    .semantics { this.contentDescription = description },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterVertically),
            ) {
                Text(
                    name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                    color = if (on) colors.ink else colors.ink2,
                    maxLines = 1,
                )
                val mark = when {
                    on && wentOver -> colors.over
                    on -> colors.ink
                    wentOver -> colors.over
                    else -> Color.Transparent
                }
                Box(
                    modifier = Modifier
                        .size(width = if (on) 18.dp else 5.dp, height = if (on) 3.dp else 5.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(mark),
                )
            }
        }
    }
}

/**
 * The month's everyday spending: what is left (or how much over) in big, a bar against the
 * budget, the budget's kind ("Default", "Changed for this month") and, for the current month,
 * what can still be spent each day. Without a budget: what was spent, and an invitation to set
 * one.
 */
@Composable
private fun EverydayCard(
    state: HomeUiState,
    today: LocalDate,
    month: YearMonth,
    onBudgetClick: () -> Unit,
) {
    val colors = EksodaTheme.colors
    val locale = appLocale()
    val budgets = state.budgets
    val total = budgets?.total
    val currency = state.spending?.currency ?: budgets?.currency.orEmpty()
    val spent = state.spending?.total ?: BigDecimal.ZERO
    val over = total?.level == BudgetLevel.OVER
    SectionCard {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(
                        if (state.isCurrent) {
                            R.string.home_everyday_so_far
                        } else {
                            R.string.home_everyday
                        },
                        monthName(month, locale),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.ink2,
                    modifier = Modifier.weight(1f),
                )
                if (total != null) {
                    Text(
                        stringResource(
                            when {
                                over -> R.string.home_badge_over
                                state.isCurrent -> R.string.home_badge_on_track
                                else -> R.string.home_badge_within
                            },
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (over) colors.overText else colors.ink,
                    )
                }
            }
            if (total == null) {
                Figure(spent, currency, stringResource(R.string.home_spent_word), colors.ink)
                Text(
                    stringResource(R.string.home_set_budget),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.compareText,
                    modifier = Modifier.clickable(onClick = onBudgetClick).padding(vertical = 4.dp),
                )
                return@Column
            }
            Figure(
                total.left.abs(),
                currency,
                stringResource(if (over) R.string.home_over_word else R.string.home_left_word),
                if (over) colors.overText else colors.ink,
            )
            Bar(total, height = 12.dp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    monoNumbers(
                        stringResource(
                            R.string.home_of,
                            formatMoney(Money(total.spent, currency), locale),
                            formatMoney(Money(total.budget, currency), locale),
                        ),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.ink2,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    stringResource(
                        if (budgets.changed) {
                            R.string.home_budget_changed
                        } else {
                            R.string.home_budget_default
                        },
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.compareText,
                    modifier = Modifier.clickable(onClick = onBudgetClick).padding(vertical = 4.dp),
                )
            }
            if (state.isCurrent) {
                perDay(total.left, today)?.let { daily ->
                    HorizontalDivider(color = colors.line)
                    Text(
                        monoNumbers(
                            stringResource(
                                R.string.home_per_day,
                                formatMoney(Money(daily, currency), locale),
                            ),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

/** "413,60 € left": the big figure and its word. */
@Composable
private fun Figure(amount: BigDecimal, currency: String, word: String, color: Color) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MoneyText(
            Money(amount, currency),
            style = HeroNumberStyle,
            color = color,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            word,
            style = MaterialTheme.typography.titleMedium,
            color = EksodaTheme.colors.ink2,
            modifier = Modifier.padding(bottom = 6.dp),
        )
    }
}

/** How much of a budget is used: blue, red once over (§1). */
@Composable
private fun Bar(progress: BudgetProgress, height: Dp) {
    val colors = EksodaTheme.colors
    ShareBar(
        value = progress.spent.max(BigDecimal.ZERO),
        max = progress.budget,
        color = if (progress.level == BudgetLevel.OVER) colors.over else colors.compare,
        thickness = height,
    )
}

/** The month's bills on one line, apart from everyday spending; it opens Bills. */
@Composable
private fun BillsRow(bills: BillsMonth, onClick: () -> Unit) {
    val colors = EksodaTheme.colors
    SectionCard(modifier = Modifier.clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Tile(Icons.Outlined.Home)
            val waiting = bills.toEnter > 0 && bills.current
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.home_bills),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    when {
                        bills.lines.isEmpty() -> stringResource(R.string.home_bills_none)

                        waiting -> pluralStringResource(
                            R.plurals.home_bills_to_enter,
                            bills.toEnter,
                            bills.toEnter,
                        )

                        else -> pluralStringResource(
                            R.plurals.home_bills_count,
                            bills.lines.size,
                            bills.lines.size,
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (waiting) colors.overText else colors.ink2,
                )
            }
            if (bills.lines.isNotEmpty()) {
                MoneyText(
                    Money(bills.total, bills.currency),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = colors.ink2,
            )
        }
    }
}

/**
 * The month's categories, most spent first, each opening its own screen. One with a monthly
 * limit shows a thin bar and what is left of it.
 */
@Composable
private fun CategoriesCard(
    buckets: List<SpendingBucket>,
    limits: List<BudgetProgress>,
    currency: String,
    onCategoryClick: (String?) -> Unit,
    onAllClick: () -> Unit,
) {
    val colors = EksodaTheme.colors
    val byId = limits.associateBy { it.category?.id }
    SectionCard(
        title = stringResource(R.string.home_categories),
        action = {
            Text(
                stringResource(R.string.home_all_categories),
                style = MaterialTheme.typography.labelLarge,
                color = colors.compareText,
                modifier = Modifier.clickable(onClick = onAllClick).padding(vertical = 6.dp),
            )
        },
    ) {
        buckets.take(TOP_CATEGORIES).forEachIndexed { index, bucket ->
            if (index > 0) {
                HorizontalDivider(color = colors.line, modifier = Modifier.padding(start = 68.dp))
            }
            val opens = bucket.kind == SpendingBucketKind.CATEGORY ||
                bucket.kind == SpendingBucketKind.UNCATEGORIZED
            CategoryRow(
                bucket = bucket,
                limit = bucket.category?.id?.let(byId::get),
                currency = currency,
                onClick = if (opens) ({ onCategoryClick(bucket.category?.id) }) else null,
            )
        }
    }
}

@Composable
private fun CategoryRow(
    bucket: SpendingBucket,
    limit: BudgetProgress?,
    currency: String,
    onClick: (() -> Unit)?,
) {
    val colors = EksodaTheme.colors
    val locale = appLocale()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CategoryTile(slug = bucket.category?.slug)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    bucketName(bucket),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                MoneyText(
                    Money(bucket.total, currency),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (limit != null) {
                val over = limit.level == BudgetLevel.OVER
                Bar(limit, height = 5.dp)
                Text(
                    monoNumbers(
                        stringResource(
                            if (over) R.string.home_limit_over else R.string.home_limit_left,
                            formatMoney(Money(limit.budget, currency), locale),
                            formatMoney(Money(limit.left.abs(), currency), locale),
                        ),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (over) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (over) colors.overText else colors.ink2,
                )
            }
        }
        if (onClick != null) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = colors.ink2,
            )
        }
    }
}

/** "4 items to match" and "2 receipts importing…", each hidden when 0, the card when both are. */
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
                title = pluralStringResource(R.plurals.home_to_match, needsReview, needsReview),
                body = stringResource(R.string.home_to_match_body),
                onClick = onReviewClick,
            )
        }
        if (needsReview > 0 && importing > 0) {
            HorizontalDivider(color = colors.line, modifier = Modifier.padding(start = 68.dp))
        }
        if (importing > 0) {
            AttentionRow(
                icon = Icons.Outlined.Sync,
                title = pluralStringResource(R.plurals.home_importing, importing, importing),
                body = stringResource(R.string.home_importing_body),
                onClick = onImportsClick,
            )
        }
    }
}

@Composable
private fun AttentionRow(icon: ImageVector, title: String, body: String, onClick: () -> Unit) {
    val colors = EksodaTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Tile(icon)
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

/** A 40 dp round neutral tile with an ink icon, like a category's. */
@Composable
internal fun Tile(icon: ImageVector) {
    val colors = EksodaTheme.colors
    Box(
        modifier = Modifier.size(40.dp).clip(CircleShape).background(colors.tile),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = colors.ink, modifier = Modifier.size(20.dp))
    }
}

private const val TOP_CATEGORIES = 6
