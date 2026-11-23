package io.eksoda.core.model

import java.math.BigDecimal
import java.time.YearMonth

/** How a bill's amount is known. */
public enum class BillKind {
    /** The same every month (rent): its amount, changeable for the current month. */
    FIXED,

    /** Different every month (electricity): entered each month. */
    VARIABLE,
}

/**
 * A bill paid every month, kept apart from receipts. [amount] is what a [BillKind.FIXED] bill
 * costs every month, or what a [BillKind.VARIABLE] one usually costs (a hint, may be null).
 */
public data class Bill(
    val id: String,
    val name: String,
    val kind: BillKind,
    val amount: BigDecimal?,
)

/** A bill in one month. */
public data class BillLine(
    val bill: Bill,
    /** What it costs that month; null for a variable bill not entered yet. */
    val amount: BigDecimal?,
    /** A fixed bill whose amount that month differs from its usual one. */
    val changed: Boolean,
    /** Whether that month's amount can still be entered or changed. */
    val editable: Boolean,
)

/** The bills of one month. Amounts are in [currency]. */
public data class BillsMonth(
    val currency: String,
    val month: YearMonth,
    val lines: List<BillLine>,
    /** The amounts known so far. */
    val total: BigDecimal,
    /** How many variable bills still have no amount that month. */
    val toEnter: Int,
    /** Whether this is the current month (the past is read-only). */
    val current: Boolean,
)
