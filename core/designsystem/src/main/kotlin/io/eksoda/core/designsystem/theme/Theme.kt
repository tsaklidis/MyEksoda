package io.eksoda.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = EksodaPrimaryDark,
    secondary = EksodaSecondaryDark,
    tertiary = EksodaCautionDark,
    error = EksodaError,
)

private val LightColorScheme = lightColorScheme(
    primary = EksodaPrimaryLight,
    secondary = EksodaSecondaryLight,
    tertiary = EksodaCautionLight,
    background = EksodaBackgroundLight,
    surface = EksodaSurfaceLight,
    error = EksodaError,
)

@Composable
fun EksodaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme

        else -> LightColorScheme
    }
    // The activity draws edge to edge (enableEdgeToEdge), which also picks the system bar icon
    // colors. No status bar color is set: Android 15+ ignores it.
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
