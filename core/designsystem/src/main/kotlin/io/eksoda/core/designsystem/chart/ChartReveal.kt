package io.eksoda.core.designsystem.chart

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalInspectionMode

private const val REVEAL_MILLIS = 600

/**
 * 0 → 1 once, when the chart first appears (docs/design/ui-redesign.md §4: charts animate in once
 * per load). Compose scales animations by the system's animator duration scale, so with "Remove
 * animations" the chart is drawn whole at once. Previews draw it whole.
 */
@Composable
internal fun rememberChartReveal(): Float {
    if (LocalInspectionMode.current) return 1f
    val progress = remember { Animatable(0f) }
    LaunchedEffect(progress) {
        progress.animateTo(1f, tween(REVEAL_MILLIS, easing = FastOutSlowInEasing))
    }
    return progress.value
}
