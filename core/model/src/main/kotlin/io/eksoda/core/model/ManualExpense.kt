package io.eksoda.core.model

import java.math.BigDecimal
import java.time.LocalDate

/**
 * An expense typed in by hand: rent, the doctor, a paper receipt. It counts in totals, Insights and
 * budgets like a scanned receipt, under [categoryId]. [payee] is who was paid, shown as the
 * shop; [amount] is in the household currency and above 0.
 */
public data class ManualExpense(
    val amount: BigDecimal,
    val date: LocalDate,
    val payee: String,
    val categoryId: String,
    val note: String = "",
) {
    public companion object {
        /**
         * A payee's merchant has no VAT number: its `vat_number` is this prefix and the payee's
         * normalized name. Screens that show a VAT number leave such a merchant's out.
         */
        public const val PAYEE_VAT_PREFIX: String = "manual:"
    }
}
