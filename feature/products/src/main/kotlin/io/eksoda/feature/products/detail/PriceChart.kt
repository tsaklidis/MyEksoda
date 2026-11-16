package io.eksoda.feature.products.detail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.eksoda.core.designsystem.theme.tabular
import io.eksoda.core.model.Money
import io.eksoda.core.model.PriceEntry
import io.eksoda.core.ui.text.appLocale
import io.eksoda.core.ui.text.dateFormatter
import io.eksoda.core.ui.text.formatMoney
import io.eksoda.feature.products.R
import java.math.BigDecimal
import java.math.RoundingMode

/** Where a price sits between the first and last day, and the lowest and highest price (0..1). */
private data class ChartPoint(val x: Float, val y: Float)

/**
 * The price per [unit] over time (`android-app.md` §6.6.2), oldest on the left. Prices stay
 * BigDecimal; only the position on screen becomes a Float.
 */
@Composable
internal fun PriceChart(entries: List<PriceEntry>, unit: String, currency: String) {
    val series = remember(entries, unit, currency) {
        entries.filter {
            it.normalizedUnit == unit && it.grossAmount.currency == currency &&
                it.normalizedUnitPrice != null
        }.sortedBy { it.issuedAt.toInstant() }
    }
    if (series.size < 2) return
    val prices = series.map { checkNotNull(it.normalizedUnitPrice) }
    val low = prices.min()
    val high = prices.max()
    val points = remember(series) { chartPoints(series, low, high) }
    val locale = appLocale()
    val format = dateFormatter("d MMM yy", locale)
    val line = MaterialTheme.colorScheme.primary
    val chartDescription = stringResource(
        R.string.chart_description,
        formatMoney(Money(low, currency), locale),
        formatMoney(Money(high, currency), locale),
    )
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = formatMoney(Money(high, currency), locale),
            style = MaterialTheme.typography.labelSmall.tabular(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .padding(vertical = 8.dp)
                .semantics { contentDescription = chartDescription },
        ) {
            val offsets = points.map { Offset(it.x * size.width, (1f - it.y) * size.height) }
            val path = Path().apply {
                offsets.forEachIndexed { index, point ->
                    if (index == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
                }
            }
            val area = Path().apply {
                addPath(path)
                lineTo(offsets.last().x, size.height)
                lineTo(offsets.first().x, size.height)
                close()
            }
            drawPath(
                area,
                Brush.verticalGradient(listOf(line.copy(alpha = 0.25f), line.copy(alpha = 0f))),
            )
            drawPath(
                path,
                line,
                style = Stroke(
                    width = 2.5.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
            offsets.forEach { drawCircle(line, radius = 3.dp.toPx(), center = it) }
        }
        Text(
            text = formatMoney(Money(low, currency), locale),
            style = MaterialTheme.typography.labelSmall.tabular(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = series.first().issuedAt.format(format),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = series.last().issuedAt.format(format),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun chartPoints(
    series: List<PriceEntry>,
    low: BigDecimal,
    high: BigDecimal,
): List<ChartPoint> {
    val first = series.first().issuedAt.toEpochSecond()
    val span = (series.last().issuedAt.toEpochSecond() - first).coerceAtLeast(1)
    val range = high - low
    return series.map { entry ->
        val price = checkNotNull(entry.normalizedUnitPrice)
        val y = if (range.signum() == 0) {
            MIDDLE
        } else {
            // Prices stay BigDecimal: the fraction becomes a whole number of steps first.
            (price - low).multiply(STEPS).divide(range, 0, RoundingMode.HALF_UP).toInt() / STEPS_F
        }
        ChartPoint(x = (entry.issuedAt.toEpochSecond() - first).toFloat() / span, y = y)
    }
}

private const val MIDDLE = 0.5f
private const val STEPS_F = 10_000f
private val STEPS = BigDecimal(10_000)
