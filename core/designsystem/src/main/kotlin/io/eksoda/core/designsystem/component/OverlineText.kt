package io.eksoda.core.designsystem.component

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import io.eksoda.core.designsystem.theme.EksodaTheme
import io.eksoda.core.designsystem.theme.OverlineStyle

/**
 * A small label in ink-2 unless [color] is given: "Lowest" over a value, "Dairy and eggs · Delta"
 * over a product name (docs/design/ui-redesign.md §2). Shown as written, never in capitals; a
 * long label wraps to [maxLines] lines instead of being cut.
 */
@Composable
fun OverlineText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    maxLines: Int = 2,
) {
    Text(
        text = text,
        modifier = modifier,
        style = OverlineStyle,
        color = if (color == Color.Unspecified) EksodaTheme.colors.ink2 else color,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}
