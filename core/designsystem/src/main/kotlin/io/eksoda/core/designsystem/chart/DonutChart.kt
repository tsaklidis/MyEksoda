package io.eksoda.core.designsystem.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.eksoda.core.designsystem.theme.EksodaColors
import io.eksoda.core.designsystem.theme.EksodaTheme
import java.math.BigDecimal

/** A category's (or a bucket's) total, before ranking. */
@Immutable
data class DonutItem(
    val key: String,
    val value: BigDecimal,
    /** Not a category: uncategorized and the like, drawn in the dark bucket gray. */
    val isBucket: Boolean = false,
)

/** An item with its color by rank. [grouped] items share one gray slice ("Smaller"). */
@Immutable
data class RankedItem(
    val key: String,
    val value: BigDecimal,
    val color: Color,
    val grouped: Boolean,
)

/**
 * Colors by rank, not by category (docs/design/ui-redesign.md §4): the [named] largest
 * categories get terracotta, periwinkle, light terracotta, deep periwinkle and deep terracotta;
 * the rest are grouped in one gray slice; buckets come last in the dark bucket gray. The list
 * under the chart uses the same colors, in the same order.
 */
fun rankByValue(items: List<DonutItem>, colors: EksodaColors, named: Int = 5): List<RankedItem> {
    val categories = items.filter { !it.isBucket && it.value.signum() > 0 }
        .sortedByDescending { it.value }
    val shown = minOf(named, colors.rank.size)
    val ranked = categories.mapIndexed { index, item ->
        if (index < shown) {
            RankedItem(item.key, item.value, colors.rank[index], grouped = false)
        } else {
            RankedItem(item.key, item.value, colors.guide, grouped = true)
        }
    }
    val buckets = items.filter { it.isBucket && it.value.signum() > 0 }
        .map { RankedItem(it.key, it.value, colors.bucket, grouped = false) }
    return ranked + buckets
}

/**
 * A donut by rank (docs/design/ui-redesign.md §4): a 20 dp ring with small gaps, the grouped
 * items drawn as one slice. [center] sits in the hole (the total, for example).
 */
@Composable
fun DonutChart(
    items: List<RankedItem>,
    contentDescription: String,
    modifier: Modifier = Modifier,
    diameter: Dp = 168.dp,
    center: @Composable BoxScope.() -> Unit = {},
) {
    val slices = slicesOf(items)
    val total = slices.fold(BigDecimal.ZERO) { sum, slice -> sum + slice.second }
    val reveal = rememberChartReveal()
    Box(
        modifier = modifier
            .size(diameter)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(diameter)) {
            val stroke = RING.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val gap = if (slices.size > 1) GAP_DEGREES else 0f
            var start = START_ANGLE
            slices.forEach { (color, value) ->
                val sweep = FULL_CIRCLE * fractionOf(value, total) * reveal
                if (sweep > gap) {
                    drawArc(
                        color = color,
                        startAngle = start + gap / 2,
                        sweepAngle = sweep - gap,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = Stroke(width = stroke),
                    )
                }
                start += sweep
            }
        }
        center()
    }
}

/** The items in order, with consecutive grouped items summed into one slice. */
private fun slicesOf(items: List<RankedItem>): List<Pair<Color, BigDecimal>> {
    val slices = mutableListOf<Pair<Color, BigDecimal>>()
    var groupIndex = -1
    items.forEach { item ->
        if (item.grouped && groupIndex >= 0) {
            val (color, sum) = slices[groupIndex]
            slices[groupIndex] = color to sum + item.value
        } else {
            if (item.grouped) groupIndex = slices.size
            slices += item.color to item.value
        }
    }
    return slices
}

private val RING = 20.dp
private const val GAP_DEGREES = 1.5f
private const val START_ANGLE = -90f
private const val FULL_CIRCLE = 360f

@Preview
@Composable
private fun DonutChartPreview() {
    EksodaTheme {
        val items = rankByValue(
            listOf(
                DonutItem("groceries", BigDecimal("142.30")),
                DonutItem("restaurants", BigDecimal("86.10")),
                DonutItem("fuel", BigDecimal("60")),
                DonutItem("pharmacy", BigDecimal("24.50")),
                DonutItem("bakery", BigDecimal("18.20")),
                DonutItem("pets", BigDecimal("9.90")),
                DonutItem("services", BigDecimal("5")),
                DonutItem("uncategorized", BigDecimal("12"), isBucket = true),
            ),
            EksodaTheme.colors,
        )
        DonutChart(items, contentDescription = "Spending by category") {
            Text("358 €")
        }
    }
}
