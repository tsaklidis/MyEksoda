package io.eksoda.engine.text

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

// Python Decimal behavior the ported code depends on (docs/android-standalone.md §5.2, §9.3).

private const val PY_DECIMAL_PRECISION = 28

/** Python `Decimal` division: 28 significant digits, ROUND_HALF_EVEN (§5.2). */
internal val PY_DECIMAL_CONTEXT: MathContext =
    MathContext(PY_DECIMAL_PRECISION, RoundingMode.HALF_EVEN)

/** `a / b` on Python Decimals. */
internal fun BigDecimal.pyDivide(divisor: BigDecimal): BigDecimal =
    divide(divisor, PY_DECIMAL_CONTEXT)

/**
 * `str(Decimal)`. Java's `BigDecimal.toString()` implements the same "to-scientific-string"
 * rule (plain when the exponent is ≤ 0 and the adjusted exponent ≥ −6), so it prints the same
 * text, except that BigDecimal has no negative zero: Python prints `-0.00` where this prints
 * `0.00`.
 */
internal fun BigDecimal.pyStr(): String = toString()
