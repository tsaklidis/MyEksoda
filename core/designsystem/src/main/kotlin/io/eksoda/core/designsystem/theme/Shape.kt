package io.eksoda.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * The redesign's corners (docs/design/ui-redesign.md §3): badges and menus 6, chips 10, cards 16,
 * larger containers 20, sheets and dialogs 28.
 */
val Shapes: Shapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Cards: 16 dp, with a 1 dp line border and no elevation (SectionCard). */
val CardShape: RoundedCornerShape = RoundedCornerShape(16.dp)

/** Chips and buttons: 10 dp. */
val ButtonShape: RoundedCornerShape = RoundedCornerShape(10.dp)

/** Badges: 6 dp. */
val BadgeShape: RoundedCornerShape = RoundedCornerShape(6.dp)
