package io.eksoda.core.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// The brand scheme (android-app.md §7.2): Material's tonal-spot scheme generated from the
// brand teal #00897B with Material Color Utilities. It is used below Android 12 and whenever
// dynamic color is off. Regenerate from the seed rather than editing single values.

internal val LightScheme = lightColorScheme(
    primary = Color(0xFF1F6A60),
    onPrimary = Color(0xFFE2FFF8),
    primaryContainer = Color(0xFFA9F0E3),
    onPrimaryContainer = Color(0xFF085C53),
    inversePrimary = Color(0xFFB4FCEE),
    secondary = Color(0xFF4B645F),
    onSecondary = Color(0xFFE3FFF8),
    secondaryContainer = Color(0xFFCCE8E2),
    onSecondaryContainer = Color(0xFF3E5752),
    background = Color(0xFFF6FAF8),
    onBackground = Color(0xFF2A3432),
    surface = Color(0xFFF6FAF8),
    onSurface = Color(0xFF2A3432),
    surfaceVariant = Color(0xFFDAE5E1),
    onSurfaceVariant = Color(0xFF56615F),
    surfaceTint = Color(0xFF1F6A60),
    inverseSurface = Color(0xFF0A0F0E),
    inverseOnSurface = Color(0xFF999E9C),
    outline = Color(0xFF727D7A),
    outlineVariant = Color(0xFFA9B4B1),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFF6FAF8),
    surfaceDim = Color(0xFFD1DCD9),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFEEF5F2),
    surfaceContainer = Color(0xFFE7F0ED),
    surfaceContainerHigh = Color(0xFFE1EAE7),
    surfaceContainerHighest = Color(0xFFDAE5E1),
)

internal val DarkScheme = darkColorScheme(
    primary = Color(0xFF9CD1C7),
    onPrimary = Color(0xFF104841),
    primaryContainer = Color(0xFF275B53),
    onPrimaryContainer = Color(0xFFB8EEE3),
    inversePrimary = Color(0xFF35685F),
    secondary = Color(0xFFB1CCC6),
    onSecondary = Color(0xFF2C4540),
    secondaryContainer = Color(0xFF27403C),
    onSecondaryContainer = Color(0xFFAAC5BF),
    background = Color(0xFF0A0F0E),
    onBackground = Color(0xFFDDE8E4),
    surface = Color(0xFF0A0F0E),
    onSurface = Color(0xFFDDE8E4),
    surfaceVariant = Color(0xFF1E2826),
    onSurfaceVariant = Color(0xFFA2ADAA),
    surfaceTint = Color(0xFF9CD1C7),
    inverseSurface = Color(0xFFF6FAF8),
    inverseOnSurface = Color(0xFF515654),
    outline = Color(0xFF6D7875),
    outlineVariant = Color(0xFF3F4A48),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF242E2C),
    surfaceDim = Color(0xFF0A0F0E),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF0E1513),
    surfaceContainer = Color(0xFF131B1A),
    surfaceContainerHigh = Color(0xFF19211F),
    surfaceContainerHighest = Color(0xFF1E2826),
)

// The caution color (review badges, warnings): fixed, never dynamic. 7:1 contrast or more.
internal val CautionLight = Color(0xFFFF8F00)
internal val OnCautionLight = Color(0xFF2A1800)
internal val CautionContainerLight = Color(0xFFFFE0B2)
internal val OnCautionContainerLight = Color(0xFF4A2C00)
internal val CautionDark = Color(0xFFFFD54F)
internal val OnCautionDark = Color(0xFF3D2E00)
internal val CautionContainerDark = Color(0xFF584000)
internal val OnCautionContainerDark = Color(0xFFFFE08A)

/** Price falls and savings (android-app.md §7.4 ChangeBadge). */
val PositiveLight: Color = Color(0xFF2E7D32)
val PositiveDark: Color = Color(0xFF81C784)
