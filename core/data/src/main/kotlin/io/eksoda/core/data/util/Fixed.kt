package io.eksoda.core.data.util

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Fixed-point conversion between BigDecimal and the database's Long columns: `_minor` and `_e2`
 * have scale 2, `_e4` scale 4, `_e6` scale 6. Values are rounded HALF_UP to the column scale.
 */
object Fixed {
    const val MINOR = 2
    const val E2 = 2
    const val E4 = 4
    const val E6 = 6

    fun toLong(value: BigDecimal, scale: Int): Long =
        value.setScale(scale, RoundingMode.HALF_UP).unscaledValue().longValueExact()

    fun toLongOrNull(value: BigDecimal?, scale: Int): Long? = value?.let { toLong(it, scale) }

    fun toDecimal(value: Long, scale: Int): BigDecimal = BigDecimal.valueOf(value, scale)

    fun toDecimalOrNull(value: Long?, scale: Int): BigDecimal? = value?.let { toDecimal(it, scale) }
}
