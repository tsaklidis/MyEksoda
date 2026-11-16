package io.eksoda.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
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

val Inter: FontFamily = FontFamily(
    inter(FontWeight.Normal),
    inter(FontWeight.Medium),
    inter(FontWeight.SemiBold),
    inter(FontWeight.Bold),
)

/** Material's scale in Inter. Headlines and titles are a step bolder, display is tighter. */
val Typography: Typography = Typography().let { base ->
    Typography(
        displayLarge = base.displayLarge.inter(FontWeight.SemiBold, tighter = true),
        displayMedium = base.displayMedium.inter(FontWeight.SemiBold, tighter = true),
        displaySmall = base.displaySmall.inter(FontWeight.SemiBold, tighter = true),
        headlineLarge = base.headlineLarge.inter(FontWeight.SemiBold, tighter = true),
        headlineMedium = base.headlineMedium.inter(FontWeight.SemiBold),
        headlineSmall = base.headlineSmall.inter(FontWeight.SemiBold),
        titleLarge = base.titleLarge.inter(FontWeight.SemiBold),
        titleMedium = base.titleMedium.inter(FontWeight.SemiBold),
        titleSmall = base.titleSmall.inter(FontWeight.Medium),
        bodyLarge = base.bodyLarge.inter(FontWeight.Normal),
        bodyMedium = base.bodyMedium.inter(FontWeight.Normal),
        bodySmall = base.bodySmall.inter(FontWeight.Normal),
        labelLarge = base.labelLarge.inter(FontWeight.Medium),
        labelMedium = base.labelMedium.inter(FontWeight.Medium),
        labelSmall = base.labelSmall.inter(FontWeight.Medium),
    )
}

private fun TextStyle.inter(weight: FontWeight, tighter: Boolean = false): TextStyle = copy(
    fontFamily = Inter,
    fontWeight = weight,
    letterSpacing = if (tighter) (-0.5).sp else letterSpacing,
)

/**
 * Tabular figures: every digit has the same width, so amounts line up in columns and do not
 * shift while they change (android-app.md §7.4).
 */
fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = "tnum")
