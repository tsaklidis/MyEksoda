package io.eksoda.core.model

import java.math.BigDecimal
import java.math.RoundingMode

/** An amount in one currency. Always BigDecimal, never Double (docs/android-standalone.md §5.2). */
public data class Money(
    val amount: BigDecimal,
    val currency: String,
) {
    public fun format(decimals: Int = 2): String =
        amount.setScale(decimals, RoundingMode.HALF_UP).toPlainString()

    public companion object {
        public fun fromString(amount: String, currency: String): Money =
            Money(BigDecimal(amount), currency)
    }
}
