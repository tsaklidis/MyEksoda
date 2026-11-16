package io.eksoda.core.designsystem.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.eksoda.core.designsystem.theme.ChartLabelStyle
import io.eksoda.core.designsystem.theme.EksodaColors
import io.eksoda.core.designsystem.theme.EksodaTheme
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * A line over evenly spaced slots (docs/design/ui-redesign.md §4): [values] solid in [color]
 * (2.2 dp) with a fill fading from 28% to 0, [comparison] dashed periwinkle, [average] as a
 * dashed neutral line. With [markExtremes] the lowest point gets a periwinkle ring and the
 * highest a terracotta ring, each with its value. Null values are gaps: the line joins the points
 * around them, and stops where the values stop (a month still in progress). [gridLines]
 * horizontal lines carry their values on the left unless [gridLabels] is false; [axisLabels] are
 * spread under the chart. With [markLast] the last point gets a ring and a faint vertical line
 * ("today" on the Home pace chart) and the comparison's last point a small dot.
 */
@Composable
fun LineChart(
    values: List<BigDecimal?>,
    contentDescription: String,
    formatValue: (BigDecimal) -> String,
    modifier: Modifier = Modifier,
    color: Color = EksodaTheme.colors.spend,
    comparison: List<BigDecimal?> = emptyList(),
    average: BigDecimal? = null,
    markExtremes: Boolean = false,
    axisLabels: List<String> = emptyList(),
    gridLines: Int = 4,
    gridLabels: Boolean = true,
    markLast: Boolean = false,
    height: Dp = 170.dp,
) {
    val slots = maxOf(values.size, comparison.size)
    if (slots == 0) return
    val colors = EksodaTheme.colors
    val domain = remember(values, comparison, average) { domainOf(values, comparison, average) }
    val gridValues = remember(domain, gridLines) { gridValues(domain, gridLines) }
    val labels = if (gridLabels) gridValues.map(formatValue) else emptyList()
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val gutter = remember(labels, measurer, density) {
        if (labels.isEmpty()) {
            0f
        } else {
            labels.maxOf { measurer.measure(it, ChartLabelStyle).size.width } +
                with(density) { GUTTER_GAP.toPx() }
        }
    }
    val reveal = rememberChartReveal()
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .semantics { this.contentDescription = contentDescription },
        ) {
            val plot = Plot(
                left = gutter,
                right = size.width - EDGE.toPx(),
                top = EDGE.toPx() + LABEL_ROOM.toPx(),
                bottom = size.height - EDGE.toPx() - LABEL_ROOM.toPx(),
                slots = slots,
                domain = domain,
            )
            drawGrid(plot, gridValues, labels, measurer, colors)
            if (average != null) {
                val y = plot.y(average)
                drawLine(
                    color = colors.guide,
                    start = Offset(plot.left, y),
                    end = Offset(plot.right, y),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = dashes(),
                )
            }
            clipRect(right = plot.left + (plot.right - plot.left) * reveal + EDGE.toPx()) {
                val compared = plot.points(comparison)
                if (compared.size > 1) {
                    drawPath(
                        path = lineThrough(compared),
                        color = colors.compare,
                        style = Stroke(width = COMPARE_WIDTH.toPx(), pathEffect = dashes()),
                    )
                }
                val points = plot.points(values)
                drawSeries(points, plot, color, ring = markLast, ringFill = colors.panel)
                if (markLast && compared.isNotEmpty()) {
                    drawCircle(
                        color = colors.compare,
                        radius = END_DOT.toPx(),
                        center = compared.last(),
                    )
                }
                if (markExtremes) drawExtremes(values, plot, measurer, formatValue, colors)
            }
        }
        if (axisLabels.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = with(density) { gutter.toDp() }),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                axisLabels.forEach { label ->
                    Text(text = label, style = ChartLabelStyle, color = colors.ink2)
                }
            }
        }
    }
}

/** The plot area and the mapping from a slot and a value to a position on screen. */
private class Plot(
    val left: Float,
    val right: Float,
    val top: Float,
    val bottom: Float,
    val slots: Int,
    val domain: Pair<BigDecimal, BigDecimal>,
) {
    fun x(index: Int): Float = if (slots < 2) {
        (left + right) / 2
    } else {
        left + (right - left) * index / (slots - 1)
    }

    fun y(value: BigDecimal): Float =
        bottom - (bottom - top) * fractionBetween(value, domain.first, domain.second)

    /** The non-null values as points, in order. */
    fun points(series: List<BigDecimal?>): List<Offset> =
        series.mapIndexedNotNull { index, value -> value?.let { Offset(x(index), y(it)) } }
}

private fun DrawScope.drawGrid(
    plot: Plot,
    values: List<BigDecimal>,
    labels: List<String>,
    measurer: TextMeasurer,
    colors: EksodaColors,
) {
    values.forEachIndexed { index, value ->
        val y = plot.y(value)
        drawLine(
            color = colors.line,
            start = Offset(plot.left, y),
            end = Offset(size.width, y),
            strokeWidth = 1.dp.toPx(),
        )
        val label = labels.getOrNull(index) ?: return@forEachIndexed
        val layout = measurer.measure(label, ChartLabelStyle)
        drawText(
            textLayoutResult = layout,
            color = colors.ink2,
            topLeft = Offset(0f, y - layout.size.height / 2f),
        )
    }
}

private fun DrawScope.drawSeries(
    points: List<Offset>,
    plot: Plot,
    color: Color,
    ring: Boolean,
    ringFill: Color,
) {
    if (points.isEmpty()) return
    if (points.size > 1) {
        val line = lineThrough(points)
        val area = Path().apply {
            addPath(line)
            lineTo(points.last().x, plot.bottom)
            lineTo(points.first().x, plot.bottom)
            close()
        }
        drawPath(
            path = area,
            brush = Brush.verticalGradient(
                colors = listOf(color.copy(alpha = FILL_ALPHA), color.copy(alpha = 0f)),
                startY = plot.top,
                endY = plot.bottom,
            ),
        )
        drawPath(
            path = line,
            color = color,
            style = Stroke(
                width = LINE_WIDTH.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
    }
    val last = points.last()
    if (ring) {
        drawLine(
            color = color.copy(alpha = TODAY_LINE_ALPHA),
            start = Offset(last.x, plot.top),
            end = Offset(last.x, plot.bottom),
            strokeWidth = 1.dp.toPx(),
        )
        drawCircle(color = ringFill, radius = RING.toPx(), center = last)
        drawCircle(
            color = color,
            radius = RING.toPx(),
            center = last,
            style = Stroke(width = RING_STROKE.toPx()),
        )
    } else {
        drawCircle(color = color, radius = DOT.toPx(), center = last)
    }
}

/** Rings on the lowest (periwinkle) and highest (terracotta) values, with the values. */
private fun DrawScope.drawExtremes(
    values: List<BigDecimal?>,
    plot: Plot,
    measurer: TextMeasurer,
    formatValue: (BigDecimal) -> String,
    colors: EksodaColors,
) {
    val present = values.withIndex().filter { it.value != null }
    val lowest = present.minByOrNull { checkNotNull(it.value) } ?: return
    val highest = present.maxByOrNull { checkNotNull(it.value) } ?: return
    val low = checkNotNull(lowest.value)
    val high = checkNotNull(highest.value)
    if (low.compareTo(high) == 0) return
    val radius = RING.toPx()
    listOf(
        Triple(lowest.index, low, colors.compareText) to false,
        Triple(highest.index, high, colors.spend) to true,
    ).forEach { (mark, above) ->
        val (index, value, ring) = mark
        val center = Offset(plot.x(index), plot.y(value))
        drawCircle(color = colors.panel, radius = radius, center = center)
        drawCircle(
            color = ring,
            radius = radius,
            center = center,
            style = Stroke(width = RING_STROKE.toPx()),
        )
        val layout = measurer.measure(formatValue(value), ChartLabelStyle)
        val gap = radius + LABEL_GAP.toPx()
        val y = if (above) center.y - gap - layout.size.height else center.y + gap
        val x = (center.x - layout.size.width / 2f)
            .coerceIn(0f, (size.width - layout.size.width).coerceAtLeast(0f))
        drawText(
            textLayoutResult = layout,
            color = if (above) colors.spendText else colors.compareText,
            topLeft = Offset(x, y),
        )
    }
}

private fun lineThrough(points: List<Offset>): Path = Path().apply {
    points.forEachIndexed { index, point ->
        if (index == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
    }
}

private fun DrawScope.dashes(): PathEffect =
    PathEffect.dashPathEffect(floatArrayOf(DASH.toPx(), DASH.toPx()))

/** The lowest and highest value to draw, with room around the line. Starts at 0 for totals. */
private fun domainOf(
    values: List<BigDecimal?>,
    comparison: List<BigDecimal?>,
    average: BigDecimal?,
): Pair<BigDecimal, BigDecimal> {
    val all = values.filterNotNull() + comparison.filterNotNull() + listOfNotNull(average)
    val low = all.minOrNull() ?: BigDecimal.ZERO
    val high = all.maxOrNull() ?: BigDecimal.ONE
    val range = high - low
    val pad = when {
        range.signum() > 0 -> range.multiply(PADDING)
        high.signum() != 0 -> high.abs().multiply(PADDING)
        else -> BigDecimal.ONE
    }
    val bottom = if (low.signum() >= 0 && low <= pad) BigDecimal.ZERO else low - pad
    return bottom to high + pad
}

/** [count] evenly spaced values from the bottom to the top of [domain]. */
private fun gridValues(domain: Pair<BigDecimal, BigDecimal>, count: Int): List<BigDecimal> {
    if (count < 2) return emptyList()
    val (low, high) = domain
    val step = (high - low).divide(BigDecimal(count - 1), MathContext.DECIMAL64)
    return (0 until count).map { low + step.multiply(BigDecimal(it)) }
}

private val GUTTER_GAP = 8.dp
private val LINE_WIDTH = 2.2.dp
private val COMPARE_WIDTH = 1.6.dp
private val DOT = 4.dp
private val RING = 5.dp
private val RING_STROKE = 2.5.dp
private val LABEL_GAP = 3.dp
private val DASH = 4.dp
private val EDGE = 6.dp
private val LABEL_ROOM = 12.dp
private val PADDING = BigDecimal("0.12")
private const val FILL_ALPHA = 0.28f
private const val TODAY_LINE_ALPHA = 0.35f
private val END_DOT = 3.dp

@Preview
@Composable
private fun LineChartPreview() {
    EksodaTheme {
        LineChart(
            values = listOf(
                "1.19", "1.21", "1.15", "1.22", "1.25", "1.24",
                "1.29", "1.32", "1.45", "1.28", "1.25", "1.29",
            ).map { BigDecimal(it) },
            contentDescription = "Price per liter by month",
            formatValue = { it.setScale(2, RoundingMode.HALF_UP).toPlainString() },
            color = EksodaTheme.colors.compare,
            average = BigDecimal("1.27"),
            markExtremes = true,
            axisLabels = listOf("Dec", "Mar", "Jun", "Sep", "Nov"),
        )
    }
}
