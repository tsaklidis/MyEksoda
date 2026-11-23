package io.eksoda.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * The redesign's corners (docs/design/ui-redesign.md §3): badges and menus 6, small containers
 * 10, cards 20, sheets and dialogs 28; buttons, chips and toggles are pills.
 */
val Shapes: Shapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Cards: 20 dp, with a 1 dp line border and no elevation (SectionCard). */
val CardShape: RoundedCornerShape = RoundedCornerShape(20.dp)

/** Buttons, chips and toggles: fully rounded ends. */
val PillShape: RoundedCornerShape = RoundedCornerShape(percent = 50)

/** Buttons: pills ([PillShape]). */
val ButtonShape: RoundedCornerShape = PillShape

/** Badges: 6 dp. */
val BadgeShape: RoundedCornerShape = RoundedCornerShape(6.dp)
