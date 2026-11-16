package io.eksoda.core.designsystem.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.eksoda.core.designsystem.theme.EksodaTheme
import java.math.BigDecimal

/**
 * A horizontal bar for ranked lists (by shop, top categories, top products; ui-redesign.md §4):
 * [value] as a share of [max] on a line-colored track, 4–6 dp thick. Spend amounts use
 * terracotta, counts periwinkle. Decorative: the row's text carries the figure.
 */
@Composable
fun ShareBar(
    value: BigDecimal,
    max: BigDecimal,
    color: Color,
    modifier: Modifier = Modifier,
    thickness: Dp = 6.dp,
) {
    val track = EksodaTheme.colors.line
    val fraction = fractionOf(value, max)
    val reveal = rememberChartReveal()
    Canvas(modifier = modifier.fillMaxWidth().height(thickness)) {
        val radius = CornerRadius(size.height / 2, size.height / 2)
        drawRoundRect(color = track, cornerRadius = radius)
        val width = size.width * fraction * reveal
        if (width > 0f) {
            drawRoundRect(
                color = color,
                size = Size(width.coerceAtLeast(size.height), size.height),
                cornerRadius = radius,
            )
        }
    }
}

@Preview
@Composable
private fun ShareBarPreview() {
    EksodaTheme {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ShareBar(BigDecimal("120"), BigDecimal("120"), EksodaTheme.colors.spend)
            ShareBar(BigDecimal("45"), BigDecimal("120"), EksodaTheme.colors.spend)
            ShareBar(BigDecimal("3"), BigDecimal("12"), EksodaTheme.colors.compare, Modifier, 4.dp)
        }
    }
}
