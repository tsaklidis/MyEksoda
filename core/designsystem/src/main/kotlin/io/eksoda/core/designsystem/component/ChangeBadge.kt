package io.eksoda.core.designsystem.component

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.eksoda.core.designsystem.theme.PositiveDark
import io.eksoda.core.designsystem.theme.PositiveLight
import io.eksoda.core.designsystem.theme.tabular

/**
 * ▲ or ▼ with a change ("▲ 4%"), or "—" (android-app.md §7.4 ChangeBadge). The caller formats
 * [text] for the locale.
 */
@Composable
fun ChangeBadge(direction: ChangeDirection, text: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val positive = if (isSystemInDarkTheme()) PositiveDark else PositiveLight
    val content = when (direction) {
        ChangeDirection.UP -> colors.error
        ChangeDirection.DOWN -> positive
        ChangeDirection.NONE -> colors.onSurfaceVariant
    }
    val label = when (direction) {
        ChangeDirection.UP -> "▲ $text"
        ChangeDirection.DOWN -> "▼ $text"
        ChangeDirection.NONE -> "—"
    }
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraSmall,
        color = content.copy(alpha = 0.12f),
        contentColor = content,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.tabular(),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}
