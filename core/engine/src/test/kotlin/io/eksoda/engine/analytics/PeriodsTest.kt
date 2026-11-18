package io.eksoda.engine.analytics

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId

class PeriodsTest {
    private fun day(text: String): LocalDate = LocalDate.parse(text)

    @Test
    fun `a period knows its length and the one before it`() {
        val march = Period(day("2024-03-01"), day("2024-03-31"))
        assertThat(march.days).isEqualTo(31)
        assertThat(march.previous()).isEqualTo(Period(day("2024-01-30"), day("2024-02-29")))
        assertThat(Period(day("2024-03-05"), day("2024-03-05")).previous())
            .isEqualTo(Period(day("2024-03-04"), day("2024-03-04")))
    }

    @Test
    fun `bounds are local midnights across a DST change`() {
        val dstStart = Period(day("2024-03-31"), day("2024-03-31"))
        val (start, end) = dstStart.bounds(ZoneId.of("Europe/Athens"))
        assertThat(start).isEqualTo(OffsetDateTime.parse("2024-03-31T00:00+02:00"))
        assertThat(end).isEqualTo(OffsetDateTime.parse("2024-04-01T00:00+03:00"))
    }

    @Test
    fun `months are counted across years`() {
        assertThat(monthStart(day("2024-02-15"))).isEqualTo(day("2024-02-01"))
        assertThat(addMonths(day("2024-01-01"), -1)).isEqualTo(day("2023-12-01"))
        assertThat(addMonths(day("2024-11-01"), 3)).isEqualTo(day("2025-02-01"))
        assertThat(monthPeriod(day("2024-02-10")))
            .isEqualTo(Period(day("2024-02-01"), day("2024-02-29")))
        assertThat(monthsEnding(day("2024-03-15"), 3))
            .isEqualTo(Period(day("2024-01-01"), day("2024-03-31")))
        assertThat(monthsEnding(day("2024-01-15"), 1))
            .isEqualTo(Period(day("2024-01-01"), day("2024-01-31")))
    }
}
