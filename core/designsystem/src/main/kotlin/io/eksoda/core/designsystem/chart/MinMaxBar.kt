package io.eksoda.core.designsystem.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.eksoda.core.designsystem.theme.ChartLabelStyle
import io.eksoda.core.designsystem.theme.EksodaTheme
import io.eksoda.core.designsystem.theme.OverlineStyle
import java.math.BigDecimal

/**
 * A price range (docs/design/ui-redesign.md §3, after the station page of logs.tsaklidis.gr): a
 * 2 dp track from periwinkle (lowest) to terracotta (highest), an ink dot at the average, and
 * "Min 1,15" and "Max 1,45" at its ends, and "avg 1,27 · 34 buys" on its own line below, so
 * the three never crowd one line. The caller formats the texts for the locale.
 */
@Composable
fun MinMaxBar(
    minimum: BigDecimal,
    average: BigDecimal,
    maximum: BigDecimal,
    lowLabel: String,
    lowValue: String,
    middleText: String,
    highLabel: String,
    highValue: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val colors = EksodaTheme.colors
    val position = fractionBetween(average, minimum, maximum)
    val reveal = rememberChartReveal()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics { this.contentDescription = contentDescription },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(10.dp)) {
            val y = size.height / 2
            val track = 2.dp.toPx()
            drawLine(
                brush = Brush.horizontalGradient(listOf(colors.compare, colors.spend)),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = track,
            )
            val radius = size.height / 2
            val x = radius + (size.width - 2 * radius) * position * reveal
            drawCircle(color = colors.ink, radius = radius, center = Offset(x, y))
        }
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = buildAnnotatedString {
                    withStyle(OverlineStyle.toSpanStyle()) { append(lowLabel) }
                    append(" ")
                    withStyle(SpanStyle(color = colors.compareText)) { append(lowValue) }
                },
                style = ChartLabelStyle,
                color = colors.ink2,
                modifier = Modifier.weight(1f),
            )
            Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
                Text(
                    text = buildAnnotatedString {
                        withStyle(OverlineStyle.toSpanStyle()) { append(highLabel) }
                        append(" ")
                        withStyle(SpanStyle(color = colors.spendText)) { append(highValue) }
                    },
                    style = ChartLabelStyle,
                    color = colors.ink2,
                )
            }
        }
        Text(
            text = middleText,
            style = ChartLabelStyle,
            color = colors.ink2,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Preview
@Composable
private fun MinMaxBarPreview() {
    EksodaTheme {
        MinMaxBar(
            minimum = BigDecimal("1.15"),
            average = BigDecimal("1.27"),
            maximum = BigDecimal("1.45"),
            lowLabel = "Min",
            lowValue = "1,15",
            middleText = "avg 1,27 · 34 buys",
            highLabel = "Max",
            highValue = "1,45",
            contentDescription = "Lowest 1,15 €, average 1,27 €, highest 1,45 €",
        )
    }
}
