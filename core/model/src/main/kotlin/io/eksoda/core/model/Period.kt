package io.eksoda.core.model

import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth

public sealed interface Period {
    public val start: LocalDate
    public val end: LocalDate

    public data class Month(val yearMonth: YearMonth) : Period {
        override val start: LocalDate = yearMonth.atDay(1)
        override val end: LocalDate = yearMonth.atEndOfMonth()
    }

    public data class Range(override val start: LocalDate, override val end: LocalDate) : Period

    public companion object {
        public fun current(clock: Clock): Month = Month(YearMonth.now(clock))
    }
}
