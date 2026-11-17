package io.eksoda.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.eksoda.core.designsystem.theme.EksodaTheme

/**
 * The merchant's initials on a neutral tile with a line border. The
 * redesign allows three colors only (docs/design/ui-redesign.md §1), each with a meaning, so
 * merchants are told apart by name and initials, not by color. [id] is kept for callers and
 * future use.
 */
@Suppress("UnusedParameter")
@Composable
fun MerchantAvatar(id: String, name: String, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val colors = EksodaTheme.colors
    val initials = remember(name) {
        name.split(' ', '.', '-')
            .filter { word -> word.firstOrNull()?.isLetterOrDigit() == true }
            .take(2)
            .joinToString("") { it.first().uppercase() }
    }
    Box(
        modifier = modifier
            .size(size)
            .background(colors.tile, CircleShape)
            .border(1.dp, colors.line, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials,
            color = colors.ink,
            style = if (size >= 56.dp) {
                MaterialTheme.typography.titleLarge
            } else {
                MaterialTheme.typography.titleSmall
            },
        )
    }
}

@Preview
@Composable
private fun MerchantAvatarPreview() {
    EksodaTheme { MerchantAvatar(id = "1", name = "AB Vasilopoulos") }
}
