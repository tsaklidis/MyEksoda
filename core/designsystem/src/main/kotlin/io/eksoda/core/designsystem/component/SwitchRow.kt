package io.eksoda.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.eksoda.core.designsystem.theme.EksodaTheme

/**
 * A setting with a switch: a label, an optional [supporting] hint under it, and an optional
 * leading [icon] (decorative: the label says what it is). The whole row toggles it, so the
 * [Switch] itself takes no clicks. While [enabled] is false (the value is still loading) the row
 * cannot be toggled. The default [contentPadding] and [minHeight] fit a [SectionCard] (a list
 * row's 56 dp); a denser container that pads its sides itself (a bottom sheet) passes its own,
 * no lower than the 48 dp touch target.
 */
@Composable
fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    contentPadding: PaddingValues = SwitchRowPadding,
    minHeight: Dp = 56.dp,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (icon != null) RowIcon(icon)
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (supporting != null) {
                Text(
                    supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = EksodaTheme.colors.ink2,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/** The leading icon of a settings row: decorative, the row's text says what it is. */
@Composable
fun RowIcon(icon: ImageVector) {
    Icon(icon, contentDescription = null, tint = EksodaTheme.colors.ink2)
}

private val SwitchRowPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)

@Preview
@Composable
private fun SwitchRowPreview() {
    EksodaTheme {
        SectionCard {
            SwitchRow(
                label = "Import notifications",
                supporting = "When a receipt finishes importing while the app is closed.",
                checked = true,
                onCheckedChange = {},
            )
        }
    }
}
