package io.eksoda.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.eksoda.core.designsystem.theme.ButtonShape
import io.eksoda.core.designsystem.theme.EksodaTheme

/**
 * Period chips: "This month", "Last month", "3 months", "Year" (docs/design/ui-redesign.md §3).
 * The selected chip is ink with dark text: neutral, not an accent, because the accents mean
 * "this period" and "the previous one".
 */
@Composable
fun PeriodSelector(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = EksodaTheme.colors
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        labels.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Surface(
                shape = ButtonShape,
                color = if (selected) colors.ink else colors.panel,
                contentColor = if (selected) MaterialTheme.colorScheme.background else colors.ink2,
                border = if (selected) null else BorderStroke(1.dp, colors.line),
                modifier = Modifier
                    .heightIn(min = 36.dp)
                    .selectable(
                        selected = selected,
                        onClick = { onSelect(index) },
                        role = Role.Tab,
                    ),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = label, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Preview
@Composable
private fun PeriodSelectorPreview() {
    EksodaTheme {
        PeriodSelector(listOf("This month", "Last month", "3 months", "Year"), 0, {})
    }
}
