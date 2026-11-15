package io.eksoda.core.designsystem.chart

import java.math.BigDecimal

/**
 * Converts a percent (0–100) to a 0–1 fraction for drawing a bar or a chart. The only place a
 * decimal becomes a Float, and only for drawing (docs/android-standalone.md §5.2).
 */
fun percentToFraction(percent: BigDecimal?): Float =
    ((percent?.toFloat() ?: 0f) / PERCENT).coerceIn(0f, 1f)

private const val PERCENT = 100f
