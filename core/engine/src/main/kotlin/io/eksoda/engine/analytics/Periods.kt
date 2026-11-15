// Port of backend/apps/analytics/periods.py.
package io.eksoda.engine.analytics

import io.eksoda.engine.text.pyLocalToOffset
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

// Reporting periods in the household timezone (requirements §13).

private const val MONTHS_PER_YEAR = 12

/** Calendar days [first]..[last], both inclusive. */
public data class Period(val first: LocalDate, val last: LocalDate) {
    val days: Int get() = (ChronoUnit.DAYS.between(first, last) + 1).toInt()

    /** The period of the same length immediately before. */
    public fun previous(): Period {
        val last = first.minusDays(1)
        return Period(last.minusDays((days - 1).toLong()), last)
    }

    /** [start, end) as offset times: local midnights, so DST changes are respected. */
    public fun bounds(zone: ZoneId): Pair<OffsetDateTime, OffsetDateTime> {
        val start = pyLocalToOffset(first.atTime(LocalTime.MIDNIGHT), zone)
        val end = pyLocalToOffset(last.plusDays(1).atTime(LocalTime.MIDNIGHT), zone)
        return start to end
    }
}

public fun monthStart(day: LocalDate): LocalDate = day.withDayOfMonth(1)

/** The first day of the month [count] months after [month] (negative: before). */
public fun addMonths(month: LocalDate, count: Int): LocalDate {
    val index = month.year * MONTHS_PER_YEAR + month.monthValue - 1 + count
    return LocalDate.of(
        Math.floorDiv(index, MONTHS_PER_YEAR),
        Math.floorMod(index, MONTHS_PER_YEAR) + 1,
        1,
    )
}

public fun monthPeriod(day: LocalDate): Period {
    val first = monthStart(day)
    return Period(first, addMonths(first, 1).minusDays(1))
}

/** [count] calendar months, the last one being the month of [day]. */
public fun monthsEnding(day: LocalDate, count: Int): Period {
    val lastMonth = monthStart(day)
    return Period(addMonths(lastMonth, -(count - 1)), monthPeriod(day).last)
}
