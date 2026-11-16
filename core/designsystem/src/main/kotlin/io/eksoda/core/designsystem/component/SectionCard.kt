package io.eksoda.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
 * A titled group on a panel (docs/design/ui-redesign.md §3): 16 dp corners, a 1 dp line border
 * and no elevation. The title row and the content pad themselves 16 dp from the sides.
 */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    action: (@Composable () -> Unit)? = null,
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
        Column(
            modifier = Modifier.padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (title != null || action != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = title.orEmpty(),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f).semantics { heading() },
                    )
                    action?.invoke()
                }
            }
            content()
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
