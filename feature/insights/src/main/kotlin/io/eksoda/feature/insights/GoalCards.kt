package io.eksoda.feature.insights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.eksoda.core.designsystem.chart.ShareBar
import io.eksoda.core.designsystem.text.monoNumbers
import io.eksoda.core.designsystem.theme.EksodaTheme
import io.eksoda.core.model.BudgetLevel
import io.eksoda.core.model.BudgetProgress
import io.eksoda.core.model.Category
import io.eksoda.core.model.EPaymentProgress
import io.eksoda.core.model.Money
import io.eksoda.core.model.MonthBudgets
import io.eksoda.core.ui.component.HeroMoney
import io.eksoda.core.ui.component.IncomeDialog
import io.eksoda.core.ui.text.appLocale
import io.eksoda.core.ui.text.categoryName
import io.eksoda.core.ui.text.formatMoney
import io.eksoda.core.ui.text.formatPercent
import java.math.BigDecimal
import java.time.YearMonth

/**
 * This month's budgets: the whole month's first, then the categories, most used first. Each row
 * says in words how much is left or by how much it is over, so color is never the only signal.
 */
@Composable
internal fun BudgetCard(budgets: MonthBudgets, onEdit: () -> Unit) {
    val colors = EksodaTheme.colors
    val locale = appLocale()
    FoldableCard(
        card = InsightsCard.BUDGETS,
        title = stringResource(R.string.insights_budgets),
        action = {
            val label = if (budgets.isEmpty) {
                R.string.insights_budgets_set
            } else {
                R.string.insights_budgets_edit
            }
            TextButton(onClick = onEdit) { Text(stringResource(label)) }
        },
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (budgets.isEmpty) {
                Text(
                    stringResource(R.string.insights_budgets_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.ink2,
                )
                return@Column
            }
            Text(
                stringResource(
                    R.string.insights_budget_month_gone,
                    formatPercent(budgets.elapsedPercent, locale, decimals = 0),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = colors.ink2,
            )
            val rows = listOfNotNull(budgets.total) + budgets.categories
            rows.forEachIndexed { index, progress ->
                if (index > 0) HorizontalDivider(color = colors.line)
                BudgetRow(progress, budgets.currency)
            }
        }
    }
}

@Composable
private fun BudgetRow(progress: BudgetProgress, currency: String) {
    val colors = EksodaTheme.colors
    val locale = appLocale()
    val attention = progress.level != BudgetLevel.UNDER
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                progress.category?.let { categoryName(it) }
                    ?: stringResource(R.string.insights_budget_total),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f).padding(end = 8.dp),
            )
            Text(
                monoNumbers(
                    stringResource(
                        R.string.insights_budget_of,
                        formatMoney(Money(progress.spent, currency), locale),
                        formatMoney(Money(progress.budget, currency), locale),
                    ),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = colors.ink2,
            )
        }
        ShareBar(
            value = progress.spent.max(BigDecimal.ZERO).min(progress.budget),
            max = progress.budget,
            color = if (attention) colors.spend else colors.compare,
        )
        val left = formatMoney(Money(progress.left.abs(), currency), locale)
        val used = progress.usedPercent?.let { formatPercent(it, locale, decimals = 0) }
        Text(
            monoNumbers(
                when {
                    progress.level == BudgetLevel.OVER ->
                        stringResource(R.string.insights_budget_over, left)

                    used != null -> stringResource(R.string.insights_budget_left_used, left, used)

                    else -> stringResource(R.string.insights_budget_left, left)
                },
            ),
            style = MaterialTheme.typography.bodySmall,
            color = if (attention) colors.spendText else colors.ink2,
        )
    }
}

/**
 * This year's card and bank payments against the e-payment target, with the income that sets
 * the target. Without the income it shows what was paid and asks for it.
 */
@Composable
internal fun EPaymentCard(progress: EPaymentProgress, onIncomeChange: (BigDecimal?) -> Unit) {
    val colors = EksodaTheme.colors
    val locale = appLocale()
    var editing by rememberSaveable { mutableStateOf(false) }
    val money = { amount: BigDecimal -> formatMoney(Money(amount, progress.currency), locale) }
    FoldableCard(
        card = InsightsCard.E_PAYMENTS,
        title = stringResource(R.string.insights_epayments_title, progress.year),
        action = if (progress.income != null) {
            {
                TextButton(onClick = { editing = true }) {
                    Text(stringResource(R.string.insights_epayments_income))
                }
            }
        } else {
            null
        },
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                stringResource(R.string.insights_epayments_paid),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.ink2,
            )
            HeroMoney(Money(progress.paid, progress.currency))
            val target = progress.target
            if (target == null) {
                Text(
                    monoNumbers(
                        stringResource(
                            R.string.insights_epayments_no_income,
                            formatPercent(progress.sharePercent, locale, decimals = 0),
                            money(progress.cap),
                        ),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(onClick = { editing = true }) {
                    Text(stringResource(R.string.insights_epayments_add_income))
                }
            } else {
                ShareBar(
                    value = progress.paid.max(BigDecimal.ZERO).min(target),
                    max = target,
                    color = colors.compare,
                )
                val remaining = progress.remaining ?: BigDecimal.ZERO
                Text(
                    monoNumbers(
                        if (remaining.signum() == 0) {
                            stringResource(R.string.insights_epayments_reached, money(target))
                        } else {
                            stringResource(
                                R.string.insights_epayments_left,
                                money(remaining),
                                money(target),
                                money(progress.perMonth ?: BigDecimal.ZERO),
                            )
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (remaining.signum() != 0) {
                    Text(
                        monoNumbers(
                            stringResource(
                                R.string.insights_epayments_projected,
                                money(progress.projected),
                            ),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (progress.projected < target) colors.spendText else colors.ink2,
                    )
                }
            }
            Text(
                stringResource(R.string.insights_epayments_note),
                style = MaterialTheme.typography.bodySmall,
                color = colors.ink2,
            )
        }
    }
    if (editing) {
        IncomeDialog(
            income = progress.income,
            currency = progress.currency,
            onChange = {
                editing = false
                onIncomeChange(it)
            },
            onDismiss = { editing = false },
        )
    }
}

@Preview
@Composable
private fun BudgetCardPreview() {
    EksodaTheme {
        BudgetCard(
            MonthBudgets(
                currency = "EUR",
                month = YearMonth.of(2026, 11),
                current = true,
                changed = false,
                elapsedPercent = BigDecimal("60"),
                total = BudgetProgress(
                    category = null,
                    budget = BigDecimal("800"),
                    spent = BigDecimal("512.40"),
                    left = BigDecimal("287.60"),
                    usedPercent = BigDecimal("64.05"),
                    level = BudgetLevel.UNDER,
                ),
                categories = listOf(
                    BudgetProgress(
                        category = Category("c1", "Eating out", "eating-out", false),
                        budget = BigDecimal("100"),
                        spent = BigDecimal("112.30"),
                        left = BigDecimal("-12.30"),
                        usedPercent = BigDecimal("112.30"),
                        level = BudgetLevel.OVER,
                    ),
                ),
            ),
            onEdit = {},
        )
    }
}

@Preview
@Composable
private fun EPaymentCardPreview() {
    EksodaTheme {
        EPaymentCard(
            EPaymentProgress(
                currency = "EUR",
                year = 2026,
                income = BigDecimal("18000"),
                target = BigDecimal("5400"),
                paid = BigDecimal("4120.55"),
                remaining = BigDecimal("1279.45"),
                perMonth = BigDecimal("639.73"),
                projected = BigDecimal("4590.00"),
                paidPercent = BigDecimal("76.31"),
                sharePercent = BigDecimal("30"),
                cap = BigDecimal("20000"),
            ),
            onIncomeChange = {},
        )
    }
}
