package io.eksoda.feature.receipts.list

import io.eksoda.feature.receipts.R
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** The filter sheet's period presets (android-app.md §6.4.2). Both ends inclusive. */
enum class PeriodPreset(val label: Int) {
    THIS_MONTH(R.string.period_this_month),
    LAST_MONTH(R.string.period_last_month),
    LAST_3_MONTHS(R.string.period_last_3_months),
    THIS_YEAR(R.string.period_this_year),
    ;

    fun range(today: LocalDate): ClosedRange<LocalDate> = when (this) {
        THIS_MONTH -> today.withDayOfMonth(1)..today.with(TemporalAdjusters.lastDayOfMonth())

        LAST_MONTH -> today.minusMonths(1).let {
            it.withDayOfMonth(1)..it.with(TemporalAdjusters.lastDayOfMonth())
        }

        LAST_3_MONTHS ->
            today.minusMonths(2).withDayOfMonth(1)..today.with(TemporalAdjusters.lastDayOfMonth())

        THIS_YEAR -> today.withDayOfYear(1)..today.with(TemporalAdjusters.lastDayOfYear())
    }

    companion object {
        /** The preset these dates are, if any. */
        fun of(from: LocalDate?, to: LocalDate?, today: LocalDate): PeriodPreset? =
            entries.firstOrNull { preset ->
                val range = preset.range(today)
                range.start == from && range.endInclusive == to
            }
    }
}
