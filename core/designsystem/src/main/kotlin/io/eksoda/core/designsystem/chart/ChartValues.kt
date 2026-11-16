package io.eksoda.core.designsystem.chart

import java.math.BigDecimal
import java.math.MathContext

/**
 * Converts a percent (0–100) to a 0–1 fraction for drawing a bar or a chart. This package is the
 * only place a decimal becomes a Float, and only for drawing (docs/android-standalone.md §5.2).
 */
fun percentToFraction(percent: BigDecimal?): Float =
    ((percent?.toFloat() ?: 0f) / PERCENT).coerceIn(0f, 1f)

/** Where [value] sits between [low] and [high] (0..1); the middle when they are equal. */
fun fractionBetween(value: BigDecimal, low: BigDecimal, high: BigDecimal): Float {
    val range = high - low
    if (range.signum() <= 0) return HALF
    return (value - low).divide(range, MathContext.DECIMAL64).toFloat().coerceIn(0f, 1f)
}

/** [value] as a share of [whole] (0..1); 0 when [whole] is not positive. */
fun fractionOf(value: BigDecimal, whole: BigDecimal): Float {
    if (whole.signum() <= 0) return 0f
    return value.divide(whole, MathContext.DECIMAL64).toFloat().coerceIn(0f, 1f)
}

private const val PERCENT = 100f
private const val HALF = 0.5f
