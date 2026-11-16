package io.eksoda.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.eksoda.core.designsystem.text.monoNumbers
import io.eksoda.core.designsystem.theme.BadgeShape
import io.eksoda.core.designsystem.theme.EksodaTheme

/**
 * A change in words with an arrow (docs/design/ui-redesign.md §3): "▼ 8% less" in periwinkle on
 * its tint, "▲ 4% more" in terracotta on its tint, and "— First month with receipts" neutral.
 * Color is never the only signal: the arrow and the words carry the meaning. The caller formats
 * [text] for the locale; its numbers are drawn in IBM Plex Mono.
 */
@Composable
fun ChangeBadge(direction: ChangeDirection, text: String, modifier: Modifier = Modifier) {
    val colors = EksodaTheme.colors
    val (content, container, border) = when (direction) {
        ChangeDirection.UP -> Triple(colors.spendText, colors.spendTint, colors.spendBorder)
        ChangeDirection.DOWN -> Triple(colors.compareText, colors.compareTint, colors.compareBorder)
        ChangeDirection.NONE -> Triple(colors.ink2, colors.inset, colors.line)
    }
    val arrow = when (direction) {
        ChangeDirection.UP -> "▲"
        ChangeDirection.DOWN -> "▼"
        ChangeDirection.NONE -> "—"
    }
    val label = remember(arrow, text) { monoNumbers(if (text.isBlank()) arrow else "$arrow $text") }
    Surface(
        modifier = modifier,
        shape = BadgeShape,
        color = container,
        contentColor = content,
        border = BorderStroke(1.dp, border),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Preview
@Composable
private fun ChangeBadgePreview() {
    EksodaTheme {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ChangeBadge(ChangeDirection.DOWN, "8% less than by 15 Oct (371,20 €)")
            ChangeBadge(ChangeDirection.UP, "0,10 € more than last time")
            ChangeBadge(ChangeDirection.NONE, "First month with receipts")
        }
    }
}
