package io.eksoda.core.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * The ready-made periods offered by Insights and the receipt filter, so both mean the same
 * days. Weeks start on Monday, as in Greece.
 */
public enum class PeriodPreset {
    TODAY,
    THIS_WEEK,
    THIS_MONTH,
    LAST_MONTH,
    LAST_3_MONTHS,
    THIS_YEAR,
    ;

    /**
     * The whole calendar period around [today], both ends inclusive: "this month" ends on the
     * month's last day. A saved filter then keeps matching new receipts until the period ends.
     */
    public fun range(today: LocalDate): ClosedRange<LocalDate> = when (this) {
        TODAY -> today..today

        THIS_WEEK -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).let {
            it..it.plusDays(DAYS_IN_A_WEEK - 1)
        }

        THIS_MONTH -> today.withDayOfMonth(1)..today.with(TemporalAdjusters.lastDayOfMonth())

        LAST_MONTH -> today.minusMonths(1).let {
            it.withDayOfMonth(1)..it.with(TemporalAdjusters.lastDayOfMonth())
        }

        LAST_3_MONTHS ->
            today.minusMonths(2).withDayOfMonth(1)..today.with(TemporalAdjusters.lastDayOfMonth())

        THIS_YEAR -> today.withDayOfYear(1)..today.with(TemporalAdjusters.lastDayOfYear())
    }

    /**
     * The days of the period up to [today], for figures: "this month" is the 1st to today, so
     * it compares with the same number of days before it.
     */
    public fun soFar(today: LocalDate): Period.Range {
        val range = range(today)
        return Period.Range(range.start, minOf(range.endInclusive, today))
    }

    public companion object {
        private const val DAYS_IN_A_WEEK = 7L

        /** The preset whose whole period is exactly [from]..[to], if any. */
        public fun of(from: LocalDate?, to: LocalDate?, today: LocalDate): PeriodPreset? =
            entries.firstOrNull { preset ->
                val range = preset.range(today)
                range.start == from && range.endInclusive == to
            }
    }
}
