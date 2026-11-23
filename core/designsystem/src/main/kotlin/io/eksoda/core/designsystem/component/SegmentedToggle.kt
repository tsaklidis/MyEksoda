package io.eksoda.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp
import io.eksoda.core.designsystem.theme.ButtonShape
import io.eksoda.core.designsystem.theme.EksodaTheme

/**
 * Two or three options in a dark well, the selected one on a line-colored segment
 * (docs/design/ui-redesign.md §3): "Most spent · Most bought", "€ / l · € / piece".
 *
 * A label is never cut (§2). Side by side, a label that does not fit wraps to a second line, and
 * the segments keep one height, so the selected one stays a whole pill when only its neighbor
 * wraps. When a label would need a third line, or would break a word in the middle, the options
 * stack instead, one per row at the full width (three units on a narrow phone, large text).
 *
 * The choice is made while measuring, with the exact width each label gets side by side, so it
 * agrees with how the labels are then laid out.
 */
@Composable
fun SegmentedToggle(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = EksodaTheme.colors
    val style = MaterialTheme.typography.labelLarge
    val measurer = rememberTextMeasurer()
    Layout(
        content = {
            options.forEachIndexed { index, option ->
                Segment(
                    label = option,
                    selected = index == selectedIndex,
                    onClick = { onSelect(index) },
                    style = style,
                )
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .clip(ButtonShape)
            .background(MaterialTheme.colorScheme.background)
            .border(1.dp, colors.line, ButtonShape)
            .padding(WellPadding)
            .selectableGroup(),
    ) { measurables, constraints ->
        if (measurables.isEmpty()) {
            return@Layout layout(constraints.minWidth, constraints.minHeight) {}
        }
        if (!constraints.hasBoundedWidth) {
            // No width to share (a horizontally scrolling parent): each segment takes its own.
            val placeables = measurables.map { it.measure(Constraints()) }
            val height = placeables.maxOf { it.height }
            return@Layout layout(placeables.sumOf { it.width }, height) {
                var x = 0
                placeables.forEach { placeable ->
                    placeable.placeRelative(x, (height - placeable.height) / 2)
                    x += placeable.width
                }
            }
        }
        val width = constraints.maxWidth
        val widths = shareWidth(width, measurables.size)
        // The label's own padding, rounded per side as Modifier.padding rounds it.
        val labelPadding = LabelPadding.roundToPx() * 2
        val sideBySide = options.indices.all { index ->
            val labelWidth = (widths[index] - labelPadding).coerceAtLeast(0)
            fitsSideBySide(measurer, options[index], style, labelWidth)
        }
        if (sideBySide) {
            // One height for all, so the selected pill is whole when only a neighbor wraps.
            val height = measurables.indices
                .maxOf { measurables[it].maxIntrinsicHeight(widths[it]) }
                .let { constraints.constrainHeight(it) }
            val placeables = measurables.mapIndexed { index, measurable ->
                measurable.measure(Constraints.fixed(widths[index], height))
            }
            layout(width, height) {
                var x = 0
                placeables.forEach { placeable ->
                    placeable.placeRelative(x, 0)
                    x += placeable.width
                }
            }
        } else {
            val placeables = measurables.map { it.measure(Constraints.fixedWidth(width)) }
            layout(width, constraints.constrainHeight(placeables.sumOf { it.height })) {
                var y = 0
                placeables.forEach { placeable ->
                    placeable.placeRelative(0, y)
                    y += placeable.height
                }
            }
        }
    }
}

@Composable
private fun Segment(label: String, selected: Boolean, onClick: () -> Unit, style: TextStyle) {
    val colors = EksodaTheme.colors
    Box(
        modifier = Modifier
            .heightIn(min = 40.dp)
            .clip(SegmentShape)
            .background(if (selected) colors.line else Color.Transparent)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = style,
            color = if (selected) colors.ink else colors.ink2,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = LabelPadding, vertical = 6.dp),
        )
    }
}

/** [total] px over [count] segments as a Row with equal weights shares it: whole pixels. */
private fun shareWidth(total: Int, count: Int): List<Int> =
    List(count) { index -> total / count + if (index < total % count) 1 else 0 }

/** Whether [label], laid out [width] px wide, fits a side-by-side segment. */
private fun fitsSideBySide(
    measurer: TextMeasurer,
    label: String,
    style: TextStyle,
    width: Int,
): Boolean {
    val layout = measurer.measure(label, style, constraints = Constraints(maxWidth = width))
    return layout.lineCount <= MAX_LABEL_LINES && !breaksAWord(layout, label)
}

/**
 * Whether a line of [layout] ends inside a word: a letter or digit on both sides of the break. A
 * break at a space, a hyphen or a slash is where a reader expects one.
 */
internal fun breaksAWord(layout: TextLayoutResult, text: String): Boolean =
    (0 until layout.lineCount - 1).any { line ->
        val end = layout.getLineEnd(line)
        end in 1 until text.length && text[end - 1].isLetterOrDigit() && text[end].isLetterOrDigit()
    }

private val SegmentShape = RoundedCornerShape(8.dp)
private val WellPadding = 3.dp
private val LabelPadding = 8.dp

private const val MAX_LABEL_LINES = 2

@Preview
@Composable
private fun SegmentedTogglePreview() {
    EksodaTheme {
        SegmentedToggle(listOf("Most spent", "Most bought"), 0, {})
    }
}

/** Greek labels at a narrow width: they wrap to two lines instead of ending in "…". */
@Preview(widthDp = 300)
@Composable
private fun SegmentedToggleWrapPreview() {
    EksodaTheme {
        SegmentedToggle(listOf("Περισσότερα χρήματα", "Συχνότερες αγορές"), 0, {})
    }
}

/** Three long labels that cannot sit side by side: they stack, each whole. */
@Preview(widthDp = 220)
@Composable
private fun SegmentedToggleStackedPreview() {
    EksodaTheme {
        SegmentedToggle(listOf("Ανά κιλό", "Ανά λίτρο", "Ανά τεμάχιο"), 1, {})
    }
}
