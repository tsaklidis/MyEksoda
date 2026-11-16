@file:Suppress("MatchingDeclarationName")

package io.eksoda.core.designsystem.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.eksoda.core.designsystem.theme.ChartLabelStyle
import io.eksoda.core.designsystem.theme.EksodaTheme
import io.eksoda.core.designsystem.theme.tabular
import java.math.BigDecimal

/** One merchant of a [RangeDotPlot]: its lowest, average and highest price. */
@Immutable
data class RangeRow(
    val label: String,
    /** The average, formatted: "1,18 €". */
    val value: String,
    /** "11 buys". */
    val supporting: String?,
    val minimum: BigDecimal,
    val average: BigDecimal,
    val maximum: BigDecimal,
    /** The cheapest row: periwinkle; the others are neutral. */
    val highlighted: Boolean,
)

/**
 * A price comparison (docs/design/ui-redesign.md §3): one row per merchant on a shared axis, the
 * bar from the lowest to the highest price, the dot at the average. Rows are drawn in the given
 * order and numbered "01", "02"… [formatAxis] formats the axis ends; [legend] explains the marks
 * ("bar = range · dot = average").
 */
@Composable
fun RangeDotPlot(
    rows: List<RangeRow>,
    legend: String,
    contentDescription: String,
    formatAxis: (BigDecimal) -> String,
    modifier: Modifier = Modifier,
) {
    if (rows.isEmpty()) return
    val colors = EksodaTheme.colors
    val low = rows.minOf { it.minimum }
    val high = rows.maxOf { it.maximum }
    val reveal = rememberChartReveal()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics { this.contentDescription = contentDescription },
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        rows.forEachIndexed { index, row ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = (index + 1).toString().padStart(2, '0'),
                        style = MaterialTheme.typography.bodyMedium.tabular(),
                        color = if (row.highlighted) colors.compareText else colors.ink2,
                    )
                    Text(
                        text = row.label,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                    )
                    Text(text = row.value, style = MaterialTheme.typography.bodyMedium.tabular())
                    row.supporting?.let { supporting ->
                        Text(
                            text = " · $supporting",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.ink2,
                        )
                    }
                }
                Canvas(modifier = Modifier.fillMaxWidth().height(12.dp)) {
                    val y = size.height / 2
                    val dot = size.height / 2
                    val usable = size.width - 2 * dot
                    fun xOf(value: BigDecimal) = dot + usable * fractionBetween(value, low, high)
                    drawLine(
                        color = colors.line,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 2.dp.toPx(),
                    )
                    val start = xOf(row.minimum)
                    val end = start + (xOf(row.maximum) - start) * reveal
                    val bar = 4.dp.toPx()
                    drawRoundRect(
                        color = if (row.highlighted) colors.compareDeep else colors.bucket,
                        topLeft = Offset(start, y - bar / 2),
                        size = Size((end - start).coerceAtLeast(bar), bar),
                        cornerRadius = CornerRadius(bar / 2, bar / 2),
                    )
                    val center = Offset(xOf(row.average), y)
                    drawCircle(color = colors.panel, radius = dot, center = center)
                    drawCircle(
                        color = if (row.highlighted) colors.compareText else colors.ink,
                        radius = dot - 2.dp.toPx(),
                        center = center,
                    )
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(text = formatAxis(low), style = ChartLabelStyle, color = colors.ink2)
            Text(
                text = legend,
                style = MaterialTheme.typography.labelSmall,
                color = colors.ink2,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                textAlign = TextAlign.Center,
            )
            Text(text = formatAxis(high), style = ChartLabelStyle, color = colors.ink2)
        }
    }
}

@Preview
@Composable
private fun RangeDotPlotPreview() {
    EksodaTheme {
        RangeDotPlot(
            rows = listOf(
                RangeRow(
                    "Lidl",
                    "1,18 €",
                    "11 buys",
                    BigDecimal("1.15"),
                    BigDecimal("1.18"),
                    BigDecimal("1.22"),
                    true,
                ),
                RangeRow(
                    "AB Vasilopoulos",
                    "1,31 €",
                    "15 buys",
                    BigDecimal("1.24"),
                    BigDecimal("1.31"),
                    BigDecimal("1.45"),
                    false,
                ),
            ),
            legend = "bar = range · dot = average",
            contentDescription = "Lidl is cheapest at 1,18 € on average",
            formatAxis = { it.toPlainString() },
        )
    }
}
