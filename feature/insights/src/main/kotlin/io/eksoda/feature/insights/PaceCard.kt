package io.eksoda.feature.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.eksoda.core.designsystem.chart.LineChart
import io.eksoda.core.designsystem.component.SectionCard
import io.eksoda.core.designsystem.text.monoNumbers
import io.eksoda.core.designsystem.theme.EksodaTheme
import io.eksoda.core.model.Money
import io.eksoda.core.model.MonthPace
import io.eksoda.core.ui.text.appLocale
import io.eksoda.core.ui.text.dateFormatter
import io.eksoda.core.ui.text.formatMoney
import java.time.YearMonth
import java.util.Locale

/**
 * "This month" day by day: the running total against last month's (dashed), so the pace shows
 * at a glance. Moved here from Home, which keeps only the total.
 */
@Composable
internal fun PaceCard(pace: MonthPace) {
    val colors = EksodaTheme.colors
    val locale = appLocale()
    val currency = pace.currency
    val lastMonth = pace.month.minusMonths(1)
    SectionCard(title = stringResource(R.string.insights_pace_title)) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val dayFormat = dateFormatter("d MMM", locale)
            LineChart(
                values = pace.current,
                comparison = pace.previous,
                contentDescription = stringResource(R.string.insights_pace_description),
                formatValue = { formatMoney(Money(it, currency), locale) },
                gridLines = 4,
                gridLabels = false,
                markLast = true,
                height = 120.dp,
                axisLabels = listOf(
                    pace.month.atDay(1).format(dayFormat),
                    stringResource(R.string.insights_pace_today),
                    pace.month.atEndOfMonth().format(dayFormat),
                ),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LegendLine(monthName(pace.month, locale), colors.spend, dashed = false)
                LegendLine(
                    stringResource(
                        R.string.insights_pace_last_month,
                        monthName(lastMonth, locale),
                        formatMoney(Money(pace.previousTotal, currency), locale),
                    ),
                    colors.compare,
                    dashed = true,
                )
            }
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

/** "Νοέμβριος", "November": the month's name on its own, capitalized. */
private fun monthName(month: YearMonth, locale: Locale): String =
    month.format(dateFormatter("LLLL", locale)).replaceFirstChar { it.titlecase(locale) }
