// Port of backend/apps/products/units.py.
package io.eksoda.engine.products

import java.math.BigDecimal

// Package units and their base units (requirements §9.4, TD §13.3).
//
// Mass converts to kg and volume to l. Mass and volume are never converted into each other, so a
// price per kg is never compared with a price per liter.

/** unit → (base unit, factor) */
public val TO_BASE: Map<String, Pair<String, BigDecimal>> = mapOf(
    "mg" to ("kg" to BigDecimal("0.000001")),
    "g" to ("kg" to BigDecimal("0.001")),
    "kg" to ("kg" to BigDecimal("1")),
    "ml" to ("l" to BigDecimal("0.001")),
    "cl" to ("l" to BigDecimal("0.01")),
    "l" to ("l" to BigDecimal("1")),
    "piece" to ("piece" to BigDecimal("1")),
)

/**
 * [quantity] of [unit] in its base unit: (0.500, "l") for (500, "ml"). Throws
 * [IllegalArgumentException] for an unknown unit, as Python raises ValueError.
 */
public fun toBase(quantity: BigDecimal, unit: String): Pair<BigDecimal, String> {
    val (base, factor) = TO_BASE[unit] ?: throw IllegalArgumentException("unknown unit '$unit'")
    return quantity * factor to base
}
