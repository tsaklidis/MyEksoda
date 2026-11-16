package io.eksoda.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import io.eksoda.core.designsystem.R

/**
 * Inter (SIL Open Font License, `assets/licenses/Inter-OFL.txt`), bundled: downloadable fonts
 * need Google Play services, which the app never uses (§8.1). The file is the variable font
 * subset to Latin, Greek, currency and math signs, with every OpenType feature kept.
 */
@OptIn(ExperimentalTextApi::class)
private fun inter(weight: FontWeight) = Font(
    resId = R.font.inter_variable,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

/** Every word in the app. */
val Inter: FontFamily = FontFamily(
    inter(FontWeight.Normal),
    inter(FontWeight.Medium),
    inter(FontWeight.SemiBold),
    inter(FontWeight.Bold),
)

/**
 * IBM Plex Mono 400 and 500 (SIL Open Font License with the Reserved Font Name "Plex",
 * `assets/licenses/IBMPlexMono-OFL.txt`), bundled unmodified like Inter. Every number uses it
 * (docs/design/ui-redesign.md §2). It has no Greek, ▲ or ▼: use it for digits, separators, €, %
 * and short Latin units only; Greek text stays in Inter (missing glyphs fall back to the system
 * font).
 */
val PlexMono: FontFamily = FontFamily(
    Font(R.font.ibm_plex_mono_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_mono_medium, FontWeight.Medium),
)

private val TIGHT = (-0.5).sp
private val TITLE = (-0.4).sp

/**
 * The redesign's scale (§2) in Inter: screen title 26 sp / 600 (headlineMedium), section title
 * 15 sp / 600 (titleMedium), body 14–15 sp. Labels in caps use [OverlineStyle].
 */
val Typography: Typography = Typography().let { base ->
    Typography(
        displayLarge = base.displayLarge.inter(FontWeight.SemiBold, tracking = TIGHT),
        displayMedium = base.displayMedium.inter(FontWeight.SemiBold, tracking = TIGHT),
        displaySmall = base.displaySmall.inter(FontWeight.SemiBold, tracking = TIGHT),
        headlineLarge = base.headlineLarge.inter(FontWeight.SemiBold, tracking = TIGHT),
        headlineMedium = base.headlineMedium.inter(FontWeight.SemiBold, 26.sp, 32.sp, TITLE),
        headlineSmall = base.headlineSmall.inter(FontWeight.SemiBold, 22.sp, 28.sp, TITLE),
        titleLarge = base.titleLarge.inter(FontWeight.SemiBold, 20.sp, 26.sp),
        titleMedium = base.titleMedium.inter(FontWeight.SemiBold, 15.sp, 20.sp, 0.sp),
        titleSmall = base.titleSmall.inter(FontWeight.Medium, 14.sp, 20.sp),
        bodyLarge = base.bodyLarge.inter(FontWeight.Normal, 15.sp, 22.sp, 0.sp),
        bodyMedium = base.bodyMedium.inter(FontWeight.Normal, 14.sp, 20.sp, 0.sp),
        bodySmall = base.bodySmall.inter(FontWeight.Normal, 12.sp, 16.sp),
        labelLarge = base.labelLarge.inter(FontWeight.Medium, 14.sp, 20.sp),
        labelMedium = base.labelMedium.inter(FontWeight.Medium, 12.sp, 16.sp),
        labelSmall = base.labelSmall.inter(FontWeight.Medium, 11.sp, 16.sp),
    )
}

private fun TextStyle.inter(
    weight: FontWeight,
    size: TextUnit = fontSize,
    height: TextUnit = lineHeight,
    tracking: TextUnit = letterSpacing,
): TextStyle = copy(
    fontFamily = Inter,
    fontWeight = weight,
    fontSize = size,
    lineHeight = height,
    letterSpacing = tracking,
)

/**
 * Labels above values and section kickers: 12 sp medium in ink-2, in sentence case as written.
 * Text is never put in capitals (owner rule, 2026-11-16).
 */
val OverlineStyle: TextStyle = TextStyle(
    fontFamily = Inter,
    fontWeight = FontWeight.Medium,
    fontSize = 12.sp,
    lineHeight = 16.sp,
    letterSpacing = 0.1.sp,
)

/** The hero amount: Plex Mono 44 sp / 500, −1.5 tracking (§2). */
val HeroNumberStyle: TextStyle = TextStyle(
    fontFamily = PlexMono,
    fontWeight = FontWeight.Medium,
    fontSize = 44.sp,
    lineHeight = 46.sp,
    letterSpacing = (-1.5).sp,
)

/** Chart axis labels and small figures: Plex Mono 10 sp. */
val ChartLabelStyle: TextStyle = TextStyle(
    fontFamily = PlexMono,
    fontWeight = FontWeight.Normal,
    fontSize = 10.sp,
    lineHeight = 12.sp,
)

/**
 * A number style: IBM Plex Mono, whose digits all have the same width, so amounts line up in
 * columns and do not shift while they change. Plex Mono is bundled in 400 and 500, so heavier
 * weights become 500 rather than a synthesized bold.
 */
fun TextStyle.tabular(): TextStyle = copy(
    fontFamily = PlexMono,
    fontWeight = fontWeight?.let { if (it > FontWeight.Medium) FontWeight.Medium else it },
)
