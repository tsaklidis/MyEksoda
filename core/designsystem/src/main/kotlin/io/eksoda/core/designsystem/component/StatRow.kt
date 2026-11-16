@file:Suppress("MatchingDeclarationName")

package io.eksoda.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.eksoda.core.designsystem.theme.EksodaTheme
import io.eksoda.core.designsystem.theme.tabular

/** One figure of a [StatRow]: "LOWEST" over "1,15 €" over "Feb · Lidl". */
@Immutable
data class StatItem(
    val label: String,
    val value: String,
    val supporting: String? = null,
    /** The label's color: periwinkle for a lowest, terracotta for a highest; else ink-2. */
    val labelColor: Color = Color.Unspecified,
)

/**
 * Two or three figures side by side, split by 1 dp vertical lines (docs/design/ui-redesign.md
 * §3), under a 1 dp line when [divider] is true. The labels are in capitals, the values in IBM
 * Plex Mono. Replaces the filled StatTile boxes.
 */
@Composable
fun StatRow(items: List<StatItem>, modifier: Modifier = Modifier, divider: Boolean = true) {
    val colors = EksodaTheme.colors
    Column(modifier = modifier.fillMaxWidth()) {
        if (divider) HorizontalDivider(color = colors.line)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .padding(top = if (divider) 14.dp else 0.dp),
        ) {
            items.forEachIndexed { index, item ->
                if (index > 0) {
                    VerticalDivider(modifier = Modifier.fillMaxHeight(), color = colors.line)
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = if (index > 0) 12.dp else 0.dp, end = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    OverlineText(item.label, color = item.labelColor)
                    Text(
                        text = item.value,
                        style = MaterialTheme.typography.titleMedium.tabular(),
                        color = colors.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    item.supporting?.let { supporting ->
                        Text(
                            text = supporting,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.ink2,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun StatRowPreview() {
    EksodaTheme {
        val colors = EksodaTheme.colors
        StatRow(
            listOf(
                StatItem("Lowest", "1,15 €", "Feb · Lidl", colors.compareText),
                StatItem("Average", "1,27 €", "12 months"),
                StatItem("Highest", "1,45 €", "Aug · AB", colors.spendText),
            ),
        )
    }
}
