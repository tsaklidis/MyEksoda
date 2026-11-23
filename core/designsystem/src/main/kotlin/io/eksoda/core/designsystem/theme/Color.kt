package io.eksoda.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// The palette of the UI (docs/design/ui-redesign.md §1): a neutral base, light or dark, and two
// accents, each with one meaning. Terracotta: this period, spending, "spent more", "pricier",
// attention items, Scan. Periwinkle: the previous period, selection, "spent less", "cheaper",
// links, the active tab. No green anywhere; red only for errors and destructive actions.
// Each accent is tuned per base so text in it keeps 4.5:1.

private object Dark {
    val Background = Color(0xFF0B0D11)
    val Panel = Color(0xFF101319)
    val Inset = Color(0xFF14181F)
    val Tile = Color(0xFF171B22)
    val Raised = Color(0xFF1A1F27)
    val Line = Color(0xFF1F242D)
    val ChartNeutral = Color(0xFF2A303A)
    val Bucket = Color(0xFF363C46)
    val Guide = Color(0xFF6B737D)
    val Ink = Color(0xFFE8EAED)
    val Ink2 = Color(0xFFA2AAB4)

    val Terracotta = Color(0xFFE2795A)
    val TerracottaText = Color(0xFFEE9A7F)
    val TerracottaLight = Color(0xFFF2B8A3)
    val TerracottaDeep = Color(0xFFA24B30)
    val TerracottaStripe = Color(0xFF9A4E36)
    val TerracottaTint = Color(0xFF22150F)
    val TerracottaBorder = Color(0xFF4A2A20)

    val Periwinkle = Color(0xFF8F8FD0)
    val PeriwinkleText = Color(0xFFA9A9E0)
    val PeriwinkleDeep = Color(0xFF5F5FA8)
    val PeriwinkleTint = Color(0xFF15172A)
    val PeriwinkleBorder = Color(0xFF2E3256)
    val NavIndicator = Color(0xFF1C1E33)
}

/**
 * The light base: a warm off-white page with white cards on hairlines. The accents are deeper
 * than in the dark palette so that, as text, they keep 4.5:1 on white and on the page.
 */
private object Light {
    val Background = Color(0xFFF4F4F1)
    val Panel = Color(0xFFFFFFFF)
    val Inset = Color(0xFFF2F2EF)
    val Tile = Color(0xFFEFEFEB)
    val Raised = Color(0xFFFFFFFF)
    val Line = Color(0xFFE4E4E0)
    val ChartNeutral = Color(0xFFDCDDD8)
    val Bucket = Color(0xFFC6C8C2)
    val Guide = Color(0xFF9A9FA6)
    val Ink = Color(0xFF16181D)
    val Ink2 = Color(0xFF5D6470)

    val Terracotta = Color(0xFFB5522F)
    val TerracottaText = Color(0xFFA2492A)
    val TerracottaLight = Color(0xFFE5A188)
    val TerracottaDeep = Color(0xFF7E3820)
    val TerracottaStripe = Color(0xFFEDBFAE)
    val TerracottaTint = Color(0xFFFBEDE7)
    val TerracottaBorder = Color(0xFFF0CDBF)

    val Periwinkle = Color(0xFF4F52A8)
    val PeriwinkleText = Color(0xFF43469A)
    val PeriwinkleDeep = Color(0xFF9294D8)
    val PeriwinkleTint = Color(0xFFEEEEFA)
    val PeriwinkleBorder = Color(0xFFD3D4F0)
    val NavIndicator = Color(0xFFE7E7F6)
}

/**
 * The dark scheme. Dynamic color is off: the palette carries meanings that wallpaper colors
 * would break. `outline` (#6B737D) is for lines only: it fails 4.5:1 as text.
 */
internal val DarkScheme: ColorScheme = darkColorScheme(
    primary = Dark.Terracotta,
    onPrimary = Dark.Background,
    primaryContainer = Dark.TerracottaTint,
    onPrimaryContainer = Dark.TerracottaText,
    inversePrimary = Dark.PeriwinkleDeep,
    secondary = Dark.Periwinkle,
    onSecondary = Dark.Background,
    secondaryContainer = Dark.NavIndicator,
    onSecondaryContainer = Dark.PeriwinkleText,
    // Attention items (review badges, warnings) are terracotta too.
    tertiary = Dark.Terracotta,
    onTertiary = Dark.Background,
    tertiaryContainer = Dark.TerracottaTint,
    onTertiaryContainer = Dark.TerracottaText,
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
 * white (the surface containers), the page is off-white.
 */
internal val LightScheme: ColorScheme = lightColorScheme(
    primary = Light.Terracotta,
    onPrimary = Color.White,
    primaryContainer = Light.TerracottaTint,
    onPrimaryContainer = Light.TerracottaText,
    inversePrimary = Light.PeriwinkleDeep,
    secondary = Light.Periwinkle,
    onSecondary = Color.White,
    secondaryContainer = Light.NavIndicator,
    onSecondaryContainer = Light.PeriwinkleText,
    tertiary = Light.Terracotta,
    onTertiary = Color.White,
    tertiaryContainer = Light.TerracottaTint,
    onTertiaryContainer = Light.TerracottaText,
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
    /** This period, spending, "more", "pricier", attention. */
    val spend: Color,
    /** Terracotta text on tints. */
    val spendText: Color,
    val spendLight: Color,
    val spendDeep: Color,
    /** The second color of the striped (partial) bar. */
    val spendStripe: Color,
    val spendTint: Color,
    val spendBorder: Color,
    /** The previous period, selection, "less", "cheaper", unit prices, links. */
    val compare: Color,
    /** Periwinkle text on tints. */
    val compareText: Color,
    val compareDeep: Color,
    val compareTint: Color,
    val compareBorder: Color,
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
    /** Chart colors by rank: 1 terracotta, 2 periwinkle, 3 light, 4 deep periwinkle, 5 deep. */
    val rank: List<Color> = listOf(spend, compare, spendLight, compareDeep, spendDeep)
}

internal val DarkEksodaColors = EksodaColors(
    spend = Dark.Terracotta,
    spendText = Dark.TerracottaText,
    spendLight = Dark.TerracottaLight,
    spendDeep = Dark.TerracottaDeep,
    spendStripe = Dark.TerracottaStripe,
    spendTint = Dark.TerracottaTint,
    spendBorder = Dark.TerracottaBorder,
    compare = Dark.Periwinkle,
    compareText = Dark.PeriwinkleText,
    compareDeep = Dark.PeriwinkleDeep,
    compareTint = Dark.PeriwinkleTint,
    compareBorder = Dark.PeriwinkleBorder,
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
    spend = Light.Terracotta,
    spendText = Light.TerracottaText,
    spendLight = Light.TerracottaLight,
    spendDeep = Light.TerracottaDeep,
    spendStripe = Light.TerracottaStripe,
    spendTint = Light.TerracottaTint,
    spendBorder = Light.TerracottaBorder,
    compare = Light.Periwinkle,
    compareText = Light.PeriwinkleText,
    compareDeep = Light.PeriwinkleDeep,
    compareTint = Light.PeriwinkleTint,
    compareBorder = Light.PeriwinkleBorder,
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
