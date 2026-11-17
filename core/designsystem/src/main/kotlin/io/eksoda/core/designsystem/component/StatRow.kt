@file:Suppress("MatchingDeclarationName")

package io.eksoda.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.eksoda.core.designsystem.theme.EksodaTheme
import io.eksoda.core.designsystem.theme.OverlineStyle
import io.eksoda.core.designsystem.theme.tabular

/** One figure of a [StatRow]: "Lowest" over "1,15 €" over "Feb · Lidl". */
@Immutable
data class StatItem(
    val label: String,
    val value: String,
    val supporting: String? = null,
    /** The label's color: periwinkle for a lowest, terracotta for a highest; else ink-2. */
    val labelColor: Color = Color.Unspecified,
)

/**
 * Two or three figures (docs/design/ui-redesign.md §3), under a 1 dp line when [divider] is true.
 * Side by side, split by 1 dp vertical lines, when every label, value and supporting text fits its
 * column on one line; otherwise one figure per line (label and supporting text on the left, the
 * value on the right), so nothing wraps mid-word or is cut. Values are in IBM Plex Mono.
 */
@Composable
fun StatRow(items: List<StatItem>, modifier: Modifier = Modifier, divider: Boolean = true) {
    val colors = EksodaTheme.colors
    val valueStyle = MaterialTheme.typography.titleMedium.tabular()
    val supportingStyle = MaterialTheme.typography.bodySmall
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    Column(modifier = modifier.fillMaxWidth()) {
        if (divider) HorizontalDivider(color = colors.line)
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = if (divider) 14.dp else 0.dp),
        ) {
            val count = items.size.coerceAtLeast(1)
            // As SideBySide lays them out: equal weights after the dividers, then each column's
            // own padding (the first has no start padding).
            val (slot, start, end) = with(density) {
                Triple(
                    (constraints.maxWidth - DIVIDER.toPx() * (count - 1)) / count,
                    COLUMN_START.toPx(),
                    COLUMN_END.toPx(),
                )
            }
            val fits = remember(items, slot, valueStyle, supportingStyle) {
                fun width(text: String, style: TextStyle) =
                    measurer.measure(text, style, maxLines = 1).size.width
                items.withIndex().all { (index, item) ->
                    val column = slot - end - if (index > 0) start else 0f
                    width(item.label, OverlineStyle) <= column &&
                        width(item.value, valueStyle) <= column &&
                        (
                            item.supporting == null ||
                                width(item.supporting, supportingStyle) <= column
                            )
                }
            }
            if (fits) {
                SideBySide(items, valueStyle, supportingStyle)
            } else {
                Stacked(items, valueStyle, supportingStyle)
            }
        }
    }
}

@Composable
private fun SideBySide(items: List<StatItem>, valueStyle: TextStyle, supportingStyle: TextStyle) {
    val colors = EksodaTheme.colors
    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        items.forEachIndexed { index, item ->
            if (index > 0) {
                VerticalDivider(modifier = Modifier.fillMaxHeight(), color = colors.line)
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = if (index > 0) COLUMN_START else 0.dp, end = COLUMN_END),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                OverlineText(item.label, color = item.labelColor, maxLines = 1)
                Text(
                    text = item.value,
                    style = valueStyle,
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                item.supporting?.let { supporting ->
                    Text(
                        text = supporting,
                        style = supportingStyle,
                        color = colors.ink2,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun Stacked(items: List<StatItem>, valueStyle: TextStyle, supportingStyle: TextStyle) {
    val colors = EksodaTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.forEachIndexed { index, item ->
            if (index > 0) HorizontalDivider(color = colors.line)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(
                    modifier = Modifier.weight(1f).padding(end = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    OverlineText(item.label, color = item.labelColor)
                    item.supporting?.let { supporting ->
                        Text(text = supporting, style = supportingStyle, color = colors.ink2)
                    }
                }
                Text(text = item.value, style = valueStyle, color = colors.ink, maxLines = 1)
            }
        }
    }
}

private val COLUMN_START = 12.dp
private val COLUMN_END = 4.dp
private val DIVIDER = 1.dp

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

@Preview
@Composable
private fun StatRowStackedPreview() {
    EksodaTheme {
        val colors = EksodaTheme.colors
        StatRow(
            listOf(
                StatItem("Χαμηλότερη", "12,15 €", "Φεβ · Σκλαβενίτης", colors.compareText),
                StatItem("Μέση τιμή", "13,27 €", "14 αγορές"),
                StatItem("Υψηλότερη", "14,45 €", "Αύγ · ΑΒ Βασιλόπουλος", colors.spendText),
            ),
        )
    }
}
