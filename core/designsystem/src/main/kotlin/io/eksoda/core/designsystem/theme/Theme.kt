package io.eksoda.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

/**
 * The app's theme (docs/design/ui-redesign.md): the dark scheme in both system modes until a
 * light theme is designed, Inter for words and IBM Plex Mono for numbers, and the redesign's
 * shapes. Dynamic color is off on purpose: the two accents carry meanings (§1), which wallpaper
 * colors would break. Material 3 Expressive (MaterialExpressiveTheme, MotionScheme) is internal
 * in the Compose version in use; switch to it when it is public.
 */
@Composable
fun EksodaTheme(content: @Composable () -> Unit) {
    // The activity draws edge to edge (enableEdgeToEdge), which also picks the system bar icon
    // colors. No status bar color is set: Android 15+ ignores it.
    CompositionLocalProvider(LocalEksodaColors provides DarkEksodaColors) {
        MaterialTheme(
            colorScheme = DarkScheme,
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
