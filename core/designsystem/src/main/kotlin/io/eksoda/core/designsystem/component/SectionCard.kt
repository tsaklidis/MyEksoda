package io.eksoda.core.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.eksoda.core.designsystem.theme.CardShape
import io.eksoda.core.designsystem.theme.EksodaTheme

/**
 * A titled group on a panel (docs/design/ui-redesign.md §3): 20 dp corners, a 1 dp line border
 * and no elevation. The title row and the content pad themselves 16 dp from the sides.
 *
 * With [onToggleExpanded], the card folds: tapping the title row (or its chevron) shows or hides
 * the content and the [action]; [toggleLabel] names that tap for screen readers.
 */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    action: (@Composable () -> Unit)? = null,
    expanded: Boolean = true,
    onToggleExpanded: (() -> Unit)? = null,
    toggleLabel: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = EksodaTheme.colors
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = CardShape,
        color = colors.panel,
        contentColor = colors.ink,
        border = BorderStroke(1.dp, colors.line),
    ) {
        Column(modifier = Modifier.padding(vertical = 18.dp)) {
            val hasTitleRow = title != null || action != null
            if (hasTitleRow) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (onToggleExpanded != null) {
                                Modifier.clickable(
                                    onClickLabel = toggleLabel,
                                    onClick = onToggleExpanded,
                                )
                            } else {
                                Modifier
                            },
                        )
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = title.orEmpty(),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f).semantics { heading() },
                    )
                    if (expanded) action?.invoke()
                    if (onToggleExpanded != null) {
                        // Decorative: the row carries the click and its label.
                        Icon(
                            if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = null,
                            tint = colors.ink2,
                            modifier = Modifier.padding(start = 4.dp).size(24.dp),
                        )
                    }
                }
            }
            // The gap under the title folds with the content, so a folded card is just its title.
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(top = if (hasTitleRow) 8.dp else 0.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    content()
                }
            }
        }
    }
}

@Preview
@Composable
private fun SectionCardPreview() {
    EksodaTheme {
        SectionCard(title = "Where it's cheapest", action = { Text("Show all 4") }) {
            Text("Lidl is 0,13 €/l cheaper", modifier = Modifier.padding(horizontal = 16.dp))
        }
    }
}
