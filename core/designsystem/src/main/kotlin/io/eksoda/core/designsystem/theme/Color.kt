package io.eksoda.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// The palette of the UI (docs/design/ui-redesign.md §1): monochrome, light or charcoal dark, with
// one blue accent and red for one meaning only. Ink: this period, spending, buttons, Scan.
// Blue: the previous period, selection, "cheaper", links, progress toward a budget. Red: over a
// budget or a limit, and errors. No green anywhere. Contrast comes from text and icons, not from
// colored boxes. The blue is tuned per base so text in it keeps 4.5:1.

private object Dark {
    val Background = Color(0xFF17181C)
    val Panel = Color(0xFF222429)
    val Inset = Color(0xFF2A2C32)
    val Tile = Color(0xFF2E3036)
    val Raised = Color(0xFF2A2C32)
    val Line = Color(0xFF34363C)
    val ChartNeutral = Color(0xFF3A3D44)
    val Bucket = Color(0xFF5C6068)
    val Guide = Color(0xFF8E919A)
    val Ink = Color(0xFFF5F6F8)
    val Ink2 = Color(0xFF9A9DA6)

    val InkLight = Color(0xFF9A9DA6)
    val InkDeep = Color(0xFFC9CBD2)
    val InkStripe = Color(0xFF5C6068)
    val InkTint = Color(0xFF2E3036)
    val InkBorder = Color(0xFF44464D)

    val Blue = Color(0xFF6C84FF)
    val BlueText = Color(0xFFA9B8FF)
    val BlueDeep = Color(0xFF4C61C9)
    val BlueTint = Color(0xFF262E5C)
    val BlueBorder = Color(0xFF34428C)
    val NavIndicator = Color(0xFF2E3036)

    val Over = Color(0xFFFF6369)
    val OverText = Color(0xFFFF6369)
    val OverTint = Color(0xFF4A1F22)
}

/**
 * The light base: a cool gray page with white cards. The blue and the red are deeper than in the
 * dark palette so that, as text, they keep 4.5:1 on white and on the page.
 */
private object Light {
    val Background = Color(0xFFF5F5F7)
    val Panel = Color(0xFFFFFFFF)
    val Inset = Color(0xFFF5F5F7)
    val Tile = Color(0xFFEFF0F3)
    val Raised = Color(0xFFFFFFFF)
    val Line = Color(0xFFE6E7EB)
    val ChartNeutral = Color(0xFFE2E3E8)
    val Bucket = Color(0xFFC9CBD2)
    val Guide = Color(0xFF8A8D95)
    val Ink = Color(0xFF0E0F12)
    val Ink2 = Color(0xFF6B6E76)

    val InkLight = Color(0xFF8A8D95)
    val InkDeep = Color(0xFF3F424A)
    val InkStripe = Color(0xFFD5D7DD)
    val InkTint = Color(0xFFEFF0F3)
    val InkBorder = Color(0xFFD7D9DE)

    val Blue = Color(0xFF3B5BFD)
    val BlueText = Color(0xFF2A44D6)
    val BlueDeep = Color(0xFF8CA0FF)
    val BlueTint = Color(0xFFE8ECFF)
    val BlueBorder = Color(0xFFC9D3FF)
    val NavIndicator = Color(0xFFEFF0F3)

    val Over = Color(0xFFE5484D)
    val OverText = Color(0xFFC62F35)
    val OverTint = Color(0xFFFDECEC)
}

/**
 * The charcoal dark scheme. Dynamic color is off: the palette carries meanings that wallpaper
 * colors would break. Buttons are ink (see EksodaButton); `primary` is the blue accent, for
 * switches, progress and links.
 */
internal val DarkScheme: ColorScheme = darkColorScheme(
    primary = Dark.Blue,
    onPrimary = Dark.Background,
    primaryContainer = Dark.BlueTint,
    onPrimaryContainer = Dark.BlueText,
    inversePrimary = Dark.BlueDeep,
    secondary = Dark.Blue,
    onSecondary = Dark.Background,
    secondaryContainer = Dark.NavIndicator,
    onSecondaryContainer = Dark.Ink,
    // Attention items (review badges, warnings) are ink: color is kept for blue and red.
    tertiary = Dark.Ink,
    onTertiary = Dark.Background,
    tertiaryContainer = Dark.InkTint,
    onTertiaryContainer = Dark.Ink,
    error = Dark.OverText,
    onError = Dark.Background,
    background = Dark.Background,
    onBackground = Dark.Ink,
    surface = Dark.Background,
    onSurface = Dark.Ink,
    surfaceVariant = Dark.Tile,
    onSurfaceVariant = Dark.Ink2,
    surfaceTint = Dark.Ink2,
    inverseSurface = Dark.Ink,
    inverseOnSurface = Dark.Background,
    outline = Dark.Guide,
    outlineVariant = Dark.Line,
    scrim = Color.Black,
    surfaceBright = Dark.Raised,
    surfaceDim = Dark.Background,
    surfaceContainerLowest = Dark.Background,
    surfaceContainerLow = Dark.Panel,
    surfaceContainer = Dark.Panel,
    surfaceContainerHigh = Dark.Inset,
    surfaceContainerHighest = Dark.Raised,
)

/**
 * The light scheme: the same roles as [DarkScheme] on the light base. Sheets and dialogs are
 * white (the surface containers), the page is light gray.
 */
internal val LightScheme: ColorScheme = lightColorScheme(
    primary = Light.Blue,
    onPrimary = Color.White,
    primaryContainer = Light.BlueTint,
    onPrimaryContainer = Light.BlueText,
    inversePrimary = Light.BlueDeep,
    secondary = Light.Blue,
    onSecondary = Color.White,
    secondaryContainer = Light.NavIndicator,
    onSecondaryContainer = Light.Ink,
    tertiary = Light.Ink,
    onTertiary = Light.Background,
    tertiaryContainer = Light.InkTint,
    onTertiaryContainer = Light.Ink,
    error = Light.OverText,
    onError = Color.White,
    background = Light.Background,
    onBackground = Light.Ink,
    surface = Light.Background,
    onSurface = Light.Ink,
    surfaceVariant = Light.Tile,
    onSurfaceVariant = Light.Ink2,
    surfaceTint = Light.Ink2,
    inverseSurface = Light.Ink,
    inverseOnSurface = Light.Background,
    outline = Light.Guide,
    outlineVariant = Light.Line,
    scrim = Color.Black,
    surfaceBright = Light.Raised,
    surfaceDim = Light.Tile,
    surfaceContainerLowest = Light.Panel,
    surfaceContainerLow = Light.Panel,
    surfaceContainer = Light.Panel,
    surfaceContainerHigh = Light.Panel,
    surfaceContainerHighest = Light.Inset,
)

/**
 * The semantic colors, so screens never hard-code a hex value. Read them with
 * `EksodaTheme.colors`. Charts color by rank ([rank]), never by category.
 */
@Immutable
data class EksodaColors(
    /** This period, spending, "more", "pricier", attention: the ink scale. */
    val spend: Color,
    /** Spend text on tints. */
    val spendText: Color,
    val spendLight: Color,
    val spendDeep: Color,
    /** The second color of the striped (partial) bar. */
    val spendStripe: Color,
    val spendTint: Color,
    val spendBorder: Color,
    /** The previous period, selection, "less", "cheaper", unit prices, links: the blue accent. */
    val compare: Color,
    /** Blue text on tints. */
    val compareText: Color,
    val compareDeep: Color,
    val compareTint: Color,
    val compareBorder: Color,
    /** Over a budget or a limit: bars, figures, the month underline. Never for spending as such. */
    val over: Color,
    /** Red text: "12 € over". */
    val overText: Color,
    val overTint: Color,
    /** The selected tab's pill. */
    val navIndicator: Color,
    /** Cards. */
    val panel: Color,
    /** Callouts inside cards. */
    val inset: Color,
    /** Neutral icon tiles (categories, merchants). */
    val tile: Color,
    /** Card borders, dividers, gridlines, bar tracks. */
    val line: Color,
    /** Inactive bars. */
    val chartNeutral: Color,
    /** Average lines and grouped chart slices. Lines only, never text. */
    val guide: Color,
    /** Chart buckets that are not a category (uncategorized and the like). */
    val bucket: Color,
    /** Primary text. */
    val ink: Color,
    /** Secondary text, labels, units. */
    val ink2: Color,
    /** The page behind the cards. */
    val page: Color,
    /** Whether this is the dark palette (system bar icons, a few contrast choices). */
    val isDark: Boolean,
) {
    /** Chart colors by rank: 1 ink, 2 blue, 3 gray, 4 light blue, 5 dark gray. */
    val rank: List<Color> = listOf(spend, compare, spendLight, compareDeep, spendDeep)
}

internal val DarkEksodaColors = EksodaColors(
    spend = Dark.Ink,
    spendText = Dark.Ink,
    spendLight = Dark.InkLight,
    spendDeep = Dark.InkDeep,
    spendStripe = Dark.InkStripe,
    spendTint = Dark.InkTint,
    spendBorder = Dark.InkBorder,
    compare = Dark.Blue,
    compareText = Dark.BlueText,
    compareDeep = Dark.BlueDeep,
    compareTint = Dark.BlueTint,
    compareBorder = Dark.BlueBorder,
    over = Dark.Over,
    overText = Dark.OverText,
    overTint = Dark.OverTint,
    navIndicator = Dark.NavIndicator,
    panel = Dark.Panel,
    inset = Dark.Inset,
    tile = Dark.Tile,
    line = Dark.Line,
    chartNeutral = Dark.ChartNeutral,
    guide = Dark.Guide,
    bucket = Dark.Bucket,
    ink = Dark.Ink,
    ink2 = Dark.Ink2,
    page = Dark.Background,
    isDark = true,
)

internal val LightEksodaColors = EksodaColors(
    spend = Light.Ink,
    spendText = Light.Ink,
    spendLight = Light.InkLight,
    spendDeep = Light.InkDeep,
    spendStripe = Light.InkStripe,
    spendTint = Light.InkTint,
    spendBorder = Light.InkBorder,
    compare = Light.Blue,
    compareText = Light.BlueText,
    compareDeep = Light.BlueDeep,
    compareTint = Light.BlueTint,
    compareBorder = Light.BlueBorder,
    over = Light.Over,
    overText = Light.OverText,
    overTint = Light.OverTint,
    navIndicator = Light.NavIndicator,
    panel = Light.Panel,
    inset = Light.Inset,
    tile = Light.Tile,
    line = Light.Line,
    chartNeutral = Light.ChartNeutral,
    guide = Light.Guide,
    bucket = Light.Bucket,
    ink = Light.Ink,
    ink2 = Light.Ink2,
    page = Light.Background,
    isDark = false,
)

/** Provided by [EksodaTheme]; the default lets previews without the theme still draw. */
val LocalEksodaColors = staticCompositionLocalOf { LightEksodaColors }
