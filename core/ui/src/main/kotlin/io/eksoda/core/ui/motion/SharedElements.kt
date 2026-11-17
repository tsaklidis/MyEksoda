package io.eksoda.core.ui.motion

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier

/**
 * Shared-element transitions between screens: the app provides both
 * scopes around its nav graph, and screens mark the elements that travel. Without the scopes
 * (previews, tests) the modifiers do nothing.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope: ProvidableCompositionLocal<SharedTransitionScope?> =
    compositionLocalOf { null }

/** The current destination's enter and exit animation (the nav graph's AnimatedContentScope). */
val LocalNavAnimatedVisibilityScope: ProvidableCompositionLocal<AnimatedVisibilityScope?> =
    compositionLocalOf { null }

/** Keys of elements shared between the receipt list and receipt detail. */
object SharedKeys {
    fun receiptMerchant(receiptId: String): String = "receipt-merchant-$receiptId"

    fun receiptTotal(receiptId: String): String = "receipt-total-$receiptId"
}

/** Moves and resizes this element between two screens that use the same [key]. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedBoundsIfAvailable(key: String): Modifier {
    val shared = LocalSharedTransitionScope.current
    val animated = LocalNavAnimatedVisibilityScope.current
    if (shared == null || animated == null) return this
    return with(shared) {
        this@sharedBoundsIfAvailable.sharedBounds(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = animated,
        )
    }
}
