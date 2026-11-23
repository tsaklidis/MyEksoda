package io.eksoda.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

/**
 * The app's theme (docs/design/ui-redesign.md): a light or a dark palette ([darkTheme], the
 * system's by default; Settings can fix one), Inter for words, Fira Sans Extra Condensed for the
 * big figures and IBM Plex Mono for numbers in lists, and the redesign's shapes. Dynamic color
 * is off on purpose: the two accents carry meanings (§1), which wallpaper colors would break.
 */
@Composable
fun EksodaTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    // The activity draws edge to edge and picks the system bar icon colors from the same
    // choice (MainActivity). No status bar color is set: Android 15+ ignores it.
    val colors = if (darkTheme) DarkEksodaColors else LightEksodaColors
    CompositionLocalProvider(LocalEksodaColors provides colors) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            shapes = Shapes,
            typography = Typography,
            content = content,
        )
    }
}

/** The theme's own values, next to `MaterialTheme`'s. */
object EksodaTheme {
    /** The semantic colors: spend and compare families, lines, inks. */
    val colors: EksodaColors
        @Composable
        @ReadOnlyComposable
        get() = LocalEksodaColors.current
}
