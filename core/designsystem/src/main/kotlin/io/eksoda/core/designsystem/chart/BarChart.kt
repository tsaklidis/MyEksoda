@file:Suppress("MatchingDeclarationName")

package io.eksoda.core.designsystem.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.eksoda.core.designsystem.theme.ChartLabelStyle
import io.eksoda.core.designsystem.theme.EksodaColors
import io.eksoda.core.designsystem.theme.EksodaTheme
import java.math.BigDecimal

/** One period of a [BarChart]. A null [value] keeps the slot empty (a month with no receipts). */
@Immutable
data class BarDatum(
    /** The axis label: "Jun", "15". */
    val label: String,
    val value: BigDecimal?,
    /** The current, unfinished period: striped terracotta. */
    val partial: Boolean = false,
)

/**
 * Bars per period (docs/design/ui-redesign.md §4): past periods neutral, the selected one
 * periwinkle, the current partial period striped terracotta, empty periods keep their slot, and
 * [average] as a dashed neutral line. Tapping a bar calls [onSelect]; the caller shows the
 * callout (month, amount, "▲ n% above average"). [contentDescription] reads the chart aloud.
 */
@Composable
fun BarChart(
    bars: List<BarDatum>,
    contentDescription: String,
    modifier: Modifier = Modifier,
    selectedIndex: Int? = null,
    onSelect: ((Int) -> Unit)? = null,
    average: BigDecimal? = null,
    height: Dp = 140.dp,
) {
    if (bars.isEmpty()) return
    val colors = EksodaTheme.colors
    val top = (bars.mapNotNull { it.value } + listOfNotNull(average)).maxOrNull() ?: BigDecimal.ONE
    val reveal = rememberChartReveal()
    val select by rememberUpdatedState(onSelect)
    val tap = if (onSelect == null) {
        Modifier
    } else {
        Modifier.pointerInput(bars.size) {
            detectTapGestures { offset ->
                val index = (offset.x / (size.width.toFloat() / bars.size)).toInt()
                select?.invoke(index.coerceIn(0, bars.size - 1))
            }
        }
    }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .then(tap)
                .semantics { this.contentDescription = contentDescription },
        ) {
            val slot = size.width / bars.size
            val barWidth = slot * BAR_SHARE
            drawLine(
                color = colors.line,
                start = Offset(0f, size.height),
                end = Offset(size.width, size.height),
                strokeWidth = 1.dp.toPx(),
            )
            bars.forEachIndexed { index, bar ->
                val value = bar.value ?: return@forEachIndexed
                val barHeight = size.height * fractionOf(value, top) * reveal
                if (barHeight <= 0f) return@forEachIndexed
                val left = index * slot + (slot - barWidth) / 2
                drawBar(
                    colors = colors,
                    left = left,
                    width = barWidth,
                    height = barHeight,
                    fill = when {
                        bar.partial -> BarFill.PARTIAL
                        index == selectedIndex -> BarFill.SELECTED
                        else -> BarFill.NEUTRAL
                    },
                )
            }
            if (average != null) {
                val y = size.height * (1f - fractionOf(average, top))
                drawLine(
                    color = colors.guide,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(
                        floatArrayOf(4.dp.toPx(), 4.dp.toPx()),
                    ),
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            bars.forEachIndexed { index, bar ->
                Text(
                    text = bar.label,
                    style = ChartLabelStyle,
                    color = when {
                        bar.partial -> colors.spend
                        index == selectedIndex -> colors.compareText
                        else -> colors.ink2
                    },
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** A bar with 3 dp top corners; striped terracotta when partial. */
private fun DrawScope.drawBar(
    colors: EksodaColors,
    left: Float,
    width: Float,
    height: Float,
    fill: BarFill,
) {
    val corner = CornerRadius(TOP_CORNER.toPx(), TOP_CORNER.toPx())
    val foot = CornerRadius(1.dp.toPx(), 1.dp.toPx())
    val path = Path().apply {
        addRoundRect(
            RoundRect(
                left = left,
                top = size.height - height,
                right = left + width,
                bottom = size.height,
                topLeftCornerRadius = corner,
                topRightCornerRadius = corner,
                bottomRightCornerRadius = foot,
                bottomLeftCornerRadius = foot,
            ),
        )
    }
    when (fill) {
        BarFill.PARTIAL -> clipPath(path) {
            drawRect(color = colors.spend)
            val step = STRIPE_STEP.toPx()
            val stripe = STRIPE_WIDTH.toPx()
            var x = left - height
            while (x < left + width) {
                drawLine(
                    color = colors.spendStripe,
                    start = Offset(x, size.height),
                    end = Offset(x + height, size.height - height),
                    strokeWidth = stripe,
                )
                x += step
            }
        }

        BarFill.SELECTED -> drawPath(path, colors.compare)

        BarFill.NEUTRAL -> drawPath(path, colors.chartNeutral)
    }
}

private enum class BarFill { NEUTRAL, SELECTED, PARTIAL }

/** The bar's share of its slot; the rest is the gap. */
private const val BAR_SHARE = 0.62f
private val TOP_CORNER = 3.dp
private val STRIPE_STEP = 8.dp
private val STRIPE_WIDTH = 4.dp

@Preview
@Composable
private fun BarChartPreview() {
    EksodaTheme {
        BarChart(
            bars = listOf(
                BarDatum("Jun", BigDecimal("410")),
                BarDatum("Jul", BigDecimal("520")),
                BarDatum("Aug", null),
                BarDatum("Sep", BigDecimal("470")),
                BarDatum("Oct", BigDecimal("495")),
                BarDatum("Nov", BigDecimal("240"), partial = true),
            ),
            contentDescription = "Spending per month",
            selectedIndex = 4,
            onSelect = {},
            average = BigDecimal("474"),
        )
    }
}
