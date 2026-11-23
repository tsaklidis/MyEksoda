package io.eksoda.engine.analytics

import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

// Amounts that hold month by month: the everyday budget, a category's limit, a bill. Each has a
// default ("every month") and may have a row for one month. A month's row wins over the default;
// a row without an amount means "none that month". Only the current month can be changed, so
// the past stays as it was: before a default changes, the past months that have no row of their
// own get one with the old value ([monthsToFreeze]).

/** How many past months a change of default keeps as they were, at most. */
public const val MAX_FROZEN_MONTHS: Int = 24

/**
 * The last day of a month on which the previous month's variable bills can still be entered or
 * corrected: bills such as electricity often arrive after their month has ended.
 */
public const val BILL_GRACE_DAY: Int = 10

/** The amount for [month]: its own row's when it has one (null: none), else [default]. */
public fun amountFor(
    month: YearMonth,
    default: BigDecimal?,
    rows: Map<YearMonth, BigDecimal?>,
): BigDecimal? = if (month in rows) rows[month] else default

/**
 * The months before [current], from [first] on (at most [MAX_FROZEN_MONTHS], the newest), that
 * have no row in [withRows]: they would take a new default, so they get a row with the old one
 * first. Empty when [first] is not before [current].
 */
public fun monthsToFreeze(
    first: YearMonth,
    current: YearMonth,
    withRows: Set<YearMonth>,
): List<YearMonth> {
    val oldest = maxOf(first, current.minusMonths(MAX_FROZEN_MONTHS.toLong()))
    return generateSequence(current.minusMonths(1)) { it.minusMonths(1) }
        .takeWhile { it >= oldest }
        .filter { it !in withRows }
        .toList()
        .reversed()
}

/**
 * Whether [month]'s amount can still be changed on [today]: the current month always; the
 * previous one only for a [variable] bill, until the [BILL_GRACE_DAY]th; never an older or a
 * future month.
 */
public fun monthEditable(month: YearMonth, today: LocalDate, variable: Boolean = false): Boolean {
    val current = YearMonth.from(today)
    return month == current ||
        (variable && month == current.minusMonths(1) && today.dayOfMonth <= BILL_GRACE_DAY)
}
