package io.eksoda.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.eksoda.core.designsystem.theme.ButtonShape
import io.eksoda.core.designsystem.theme.EksodaTheme

/**
 * Two or three options in a dark well, the selected one on a line-colored segment
 * (docs/design/ui-redesign.md §3): "Most spent · Most bought", "€ / l · € / piece".
 */
@Composable
fun SegmentedToggle(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = EksodaTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(ButtonShape)
            .background(MaterialTheme.colorScheme.background)
            .border(1.dp, colors.line, ButtonShape)
            .padding(3.dp)
            .selectableGroup(),
    ) {
        options.forEachIndexed { index, option ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp)
                    .clip(SegmentShape)
                    .background(if (selected) colors.line else Color.Transparent)
                    .selectable(
                        selected = selected,
                        onClick = { onSelect(index) },
                        role = Role.RadioButton,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = option,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) colors.ink else colors.ink2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        }
    }
}

private val SegmentShape = RoundedCornerShape(8.dp)

@Preview
@Composable
private fun SegmentedTogglePreview() {
    EksodaTheme {
        SegmentedToggle(listOf("Most spent", "Most bought"), 0, {})
    }
}
