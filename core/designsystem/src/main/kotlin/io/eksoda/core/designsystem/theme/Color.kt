package io.eksoda.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// The palette of the UI redesign (docs/design/ui-redesign.md §1): one dark neutral base and two
// accents, each with one meaning. Terracotta: this period, spending, "spent more", "pricier",
// attention items, Scan. Periwinkle: the previous period, selection, "spent less", "cheaper",
// links, the active tab. No green anywhere; red only for errors and destructive actions.

private val Background = Color(0xFF0B0D11)
private val Panel = Color(0xFF101319)
private val Inset = Color(0xFF14181F)
private val Tile = Color(0xFF171B22)
private val Raised = Color(0xFF1A1F27)
private val Line = Color(0xFF1F242D)
private val ChartNeutral = Color(0xFF2A303A)
private val Bucket = Color(0xFF363C46)
private val Guide = Color(0xFF6B737D)
private val Ink = Color(0xFFE8EAED)
private val Ink2 = Color(0xFFA2AAB4)

private val Terracotta = Color(0xFFE2795A)
private val TerracottaText = Color(0xFFEE9A7F)
private val TerracottaLight = Color(0xFFF2B8A3)
private val TerracottaDeep = Color(0xFFA24B30)
private val TerracottaStripe = Color(0xFF9A4E36)
private val TerracottaTint = Color(0xFF22150F)
private val TerracottaBorder = Color(0xFF4A2A20)

private val Periwinkle = Color(0xFF8F8FD0)
private val PeriwinkleText = Color(0xFFA9A9E0)
private val PeriwinkleDeep = Color(0xFF5F5FA8)
private val PeriwinkleTint = Color(0xFF15172A)
private val PeriwinkleBorder = Color(0xFF2E3256)
private val NavIndicator = Color(0xFF1C1E33)

/**
 * The one color scheme, dark. Until a light theme is designed it is used in both system modes
 * (open question 1 of the redesign). Dynamic color is off: the palette carries meanings that
 * wallpaper colors would break. `outline` (#6B737D) is for lines only: it fails 4.5:1 as text.
 */
internal val DarkScheme: ColorScheme = darkColorScheme(
    primary = Terracotta,
    onPrimary = Background,
    primaryContainer = TerracottaTint,
    onPrimaryContainer = TerracottaText,
    inversePrimary = PeriwinkleDeep,
    secondary = Periwinkle,
    onSecondary = Background,
    secondaryContainer = NavIndicator,
    onSecondaryContainer = PeriwinkleText,
    // Attention items (review badges, warnings) are terracotta too.
    tertiary = Terracotta,
    onTertiary = Background,
    tertiaryContainer = TerracottaTint,
    onTertiaryContainer = TerracottaText,
    background = Background,
    onBackground = Ink,
    surface = Background,
    onSurface = Ink,
    surfaceVariant = Tile,
    onSurfaceVariant = Ink2,
    surfaceTint = Ink2,
    inverseSurface = Ink,
    inverseOnSurface = Background,
    outline = Guide,
    outlineVariant = Line,
    scrim = Color.Black,
    surfaceBright = Raised,
    surfaceDim = Background,
    surfaceContainerLowest = Background,
    surfaceContainerLow = Panel,
    surfaceContainer = Panel,
    surfaceContainerHigh = Inset,
    surfaceContainerHighest = Raised,
)

/**
 * The redesign's semantic colors, so screens never hard-code a hex value. Read them with
 * `EksodaTheme.colors`. Charts color by rank ([rank]), never by category.
 */
@Immutable
data class EksodaColors(
    /** This period, spending, "more", "pricier", attention. */
    val spend: Color,
    /** Terracotta text on dark tints. */
    val spendText: Color,
    val spendLight: Color,
    val spendDeep: Color,
    /** The second color of the striped (partial) bar. */
    val spendStripe: Color,
    val spendTint: Color,
    val spendBorder: Color,
    /** The previous period, selection, "less", "cheaper", unit prices, links. */
    val compare: Color,
    /** Periwinkle text on dark tints. */
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
) {
    /** Chart colors by rank: 1 terracotta, 2 periwinkle, 3 light, 4 deep periwinkle, 5 deep. */
    val rank: List<Color> = listOf(spend, compare, spendLight, compareDeep, spendDeep)
}

internal val DarkEksodaColors = EksodaColors(
    spend = Terracotta,
    spendText = TerracottaText,
    spendLight = TerracottaLight,
    spendDeep = TerracottaDeep,
    spendStripe = TerracottaStripe,
    spendTint = TerracottaTint,
    spendBorder = TerracottaBorder,
    compare = Periwinkle,
    compareText = PeriwinkleText,
    compareDeep = PeriwinkleDeep,
    compareTint = PeriwinkleTint,
    compareBorder = PeriwinkleBorder,
    navIndicator = NavIndicator,
    panel = Panel,
    inset = Inset,
    tile = Tile,
    line = Line,
    chartNeutral = ChartNeutral,
    guide = Guide,
    bucket = Bucket,
    ink = Ink,
    ink2 = Ink2,
)

/** Provided by [EksodaTheme]; the default lets previews without the theme still draw. */
val LocalEksodaColors = staticCompositionLocalOf { DarkEksodaColors }
