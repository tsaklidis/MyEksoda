package io.eksoda.core.model

import java.math.BigDecimal
import java.time.LocalDate

/** What a manual expense was. */
public enum class ManualKind(public val code: String) {
    PRODUCT("product"),
    SERVICE("service"),
    OTHER("other"),
    ;

    public companion object {
        public fun of(code: String?): ManualKind? = entries.firstOrNull { it.code == code }
    }
}

/**
 * An expense typed in by hand: the doctor, a plumber, a paper receipt. It counts in totals,
 * spending and budgets like a scanned receipt, under [categoryId] when one is picked (else as
 * "Other"). [description] says what it was or who was paid ("Dentist"), shown as the shop;
 * [amount] is in the household currency and above 0.
 */
public data class ManualExpense(
    val amount: BigDecimal,
    val date: LocalDate,
    val kind: ManualKind,
    val description: String,
    val categoryId: String? = null,
    val note: String = "",
) {
    public companion object {
        /**
         * A description's merchant has no VAT number: its `vat_number` is this prefix and the
         * normalized description. Screens that show a VAT number leave such a merchant's out.
         */
        public const val PAYEE_VAT_PREFIX: String = "manual:"
    }
}
