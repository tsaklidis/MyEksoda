package io.eksoda.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * The caution color (android-app.md §7.2) as the scheme's tertiary. It stays fixed when dynamic
 * color is on, so review badges and warnings keep their meaning whatever the wallpaper. Error
 * is the scheme's own M3 error, which is readable in both light and dark.
 */
private fun ColorScheme.withSemanticColors(dark: Boolean): ColorScheme = if (dark) {
    copy(
        tertiary = CautionDark,
        onTertiary = OnCautionDark,
        tertiaryContainer = CautionContainerDark,
        onTertiaryContainer = OnCautionContainerDark,
    )
} else {
    copy(
        tertiary = CautionLight,
        onTertiary = OnCautionLight,
        tertiaryContainer = CautionContainerLight,
        onTertiaryContainer = OnCautionContainerLight,
    )
}

/**
 * The brand scheme (or the wallpaper's on Android 12+), Inter and rounder shapes. Material 3
 * Expressive (MaterialExpressiveTheme, MotionScheme) is internal in the Compose version in use;
 * switch to it when it is public.
 */
@Composable
fun EksodaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+: primary and secondary follow the wallpaper.
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = remember(darkTheme, dynamicColor, context) {
        val base = when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

            darkTheme -> DarkScheme

            else -> LightScheme
        }
        base.withSemanticColors(darkTheme)
    }
    // The activity draws edge to edge (enableEdgeToEdge), which also picks the system bar icon
    // colors. No status bar color is set: Android 15+ ignores it.
    MaterialTheme(
        colorScheme = colorScheme,
        shapes = Shapes,
        typography = Typography,
        content = content,
    )
}
