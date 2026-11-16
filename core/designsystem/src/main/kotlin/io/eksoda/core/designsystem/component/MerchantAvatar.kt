package io.eksoda.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Avatar backgrounds. Each keeps white initials at 4.5:1 contrast or more (WCAG AA for text),
 * in light and dark themes alike.
 */
private val AVATAR_COLORS = listOf(
    Color(0xFF3949AB),
    Color(0xFF00796B),
    Color(0xFFBF360C),
    Color(0xFF6D4C41),
    Color(0xFF8E24AA),
    Color(0xFF1565C0),
    Color(0xFF2E7D32),
    Color(0xFFC2185B),
)

/** Initials on a color derived from the merchant id (android-app.md §6.4.1, §7.4). */
@Composable
fun MerchantAvatar(id: String, name: String, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val color = remember(id) { AVATAR_COLORS[Math.floorMod(id.hashCode(), AVATAR_COLORS.size)] }
    val initials = remember(name) {
        name.split(' ', '.', '-')
            .filter { word -> word.firstOrNull()?.isLetterOrDigit() == true }
            .take(2)
            .joinToString("") { it.first().uppercase() }
    }
    Box(
        modifier = modifier.size(size).background(color, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials,
            color = Color.White,
            style = if (size >= 56.dp) {
                MaterialTheme.typography.titleLarge
            } else {
                MaterialTheme.typography.titleSmall
            },
        )
    }
}
