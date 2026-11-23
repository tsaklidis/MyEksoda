package io.eksoda.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import io.eksoda.core.designsystem.theme.ButtonShape
import io.eksoda.core.designsystem.theme.EksodaTheme
import io.eksoda.core.designsystem.theme.PillShape

/**
 * Two or three options in a pill-shaped well, the selected one on a raised segment
 * (docs/design/ui-redesign.md §3): "Most spent · Most bought", "€ / l · € / piece".
 *
 * A label is never cut (§2). Side by side, a label that does not fit wraps to a second line, and
 * the segments keep one height, so the selected one stays a whole pill when only its neighbor
 * wraps. When a label would need more lines, or has a word wider than its segment (it would be
 * broken mid-word), the options stack instead, one per row at the full width (three units on a
 * narrow phone, a large font scale).
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
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val labelWidth = with(LocalDensity.current) {
            ((maxWidth - WellPadding * 2) / options.size.coerceAtLeast(1) - LabelPadding * 2)
                .roundToPx()
        }
        val stacked = remember(options, labelWidth, style, measurer) {
            constraints.hasBoundedWidth &&
                options.any { !fitsInSegment(measurer, it, style, labelWidth) }
        }
        val well = Modifier
            .fillMaxWidth()
            .clip(ButtonShape)
            .background(MaterialTheme.colorScheme.background)
            .border(1.dp, colors.line, ButtonShape)
            .padding(WellPadding)
            .selectableGroup()
        val segment: @Composable (Int, String, Modifier) -> Unit = { index, option, segmentModifier ->
            Segment(
                label = option,
                selected = index == selectedIndex,
                onClick = { onSelect(index) },
                style = style,
                modifier = segmentModifier,
            )
        }
        if (stacked) {
            Column(modifier = well) {
                options.forEachIndexed { index, option ->
                    segment(index, option, Modifier.fillMaxWidth())
                }
            }
        } else {
            Row(modifier = well.height(IntrinsicSize.Min)) {
                options.forEachIndexed { index, option ->
                    segment(index, option, Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
    }
}

@Composable
private fun Segment(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    style: TextStyle,
    modifier: Modifier,
) {
    val colors = EksodaTheme.colors
    Box(
        modifier = modifier
            .heightIn(min = 40.dp)
            .clip(SegmentShape)
            .background(
                when {
                    !selected -> Color.Transparent
                    colors.isDark -> colors.line
                    else -> colors.panel
                },
            )
            .then(
                if (selected && !colors.isDark) Modifier.border(1.dp, colors.line, SegmentShape) else Modifier,
            )
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

/**
 * Whether [label] fits a side-by-side segment [width] px wide: in at most [MAX_LABEL_LINES]
 * lines, with no word wider than the segment (it would be broken in the middle).
 */
private fun fitsInSegment(
    measurer: TextMeasurer,
    label: String,
    style: TextStyle,
    width: Int,
): Boolean {
    if (width <= 0) return false
    val wrapped = measurer.measure(label, style, constraints = Constraints(maxWidth = width))
    if (wrapped.lineCount > MAX_LABEL_LINES) return false
    return label.split(' ').filter { it.isNotEmpty() }.all { word ->
        measurer.measure(word, style, softWrap = false, maxLines = 1).size.width <= width
    }
}

private val SegmentShape = PillShape
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
