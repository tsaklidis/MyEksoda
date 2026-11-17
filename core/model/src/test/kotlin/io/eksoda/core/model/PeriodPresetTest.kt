package io.eksoda.core.model

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.junit.jupiter.api.Test
import java.time.LocalDate

class PeriodPresetTest {
    // A Tuesday.
    private val today = LocalDate.of(2026, 11, 17)

    @Test
    fun `today is one day`() {
        assertThat(PeriodPreset.TODAY.range(today)).isEqualTo(today..today)
        assertThat(PeriodPreset.TODAY.soFar(today)).isEqualTo(Period.Range(today, today))
    }

    @Test
    fun `the week runs Monday to Sunday and so far ends today`() {
        val monday = LocalDate.of(2026, 11, 16)
        assertThat(PeriodPreset.THIS_WEEK.range(today))
            .isEqualTo(monday..LocalDate.of(2026, 11, 22))
        assertThat(PeriodPreset.THIS_WEEK.soFar(today)).isEqualTo(Period.Range(monday, today))
        // On a Monday the week is that day so far.
        assertThat(PeriodPreset.THIS_WEEK.soFar(monday)).isEqualTo(Period.Range(monday, monday))
        // On a Sunday it started six days before.
        val sunday = LocalDate.of(2026, 11, 22)
        assertThat(PeriodPreset.THIS_WEEK.range(sunday).start).isEqualTo(monday)
    }

    @Test
    fun `months and years`() {
        assertThat(PeriodPreset.THIS_MONTH.range(today))
            .isEqualTo(LocalDate.of(2026, 11, 1)..LocalDate.of(2026, 11, 30))
        assertThat(PeriodPreset.LAST_MONTH.soFar(today))
            .isEqualTo(Period.Range(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)))
        assertThat(PeriodPreset.LAST_3_MONTHS.soFar(today))
            .isEqualTo(Period.Range(LocalDate.of(2026, 9, 1), today))
        assertThat(PeriodPreset.THIS_YEAR.range(today))
            .isEqualTo(LocalDate.of(2026, 1, 1)..LocalDate.of(2026, 12, 31))
        // Last month across a year boundary.
        assertThat(PeriodPreset.LAST_MONTH.range(LocalDate.of(2027, 1, 10)))
            .isEqualTo(LocalDate.of(2026, 12, 1)..LocalDate.of(2026, 12, 31))
    }

    @Test
    fun `a range is recognized as its preset`() {
        val week = PeriodPreset.THIS_WEEK.range(today)
        assertThat(PeriodPreset.of(week.start, week.endInclusive, today))
            .isEqualTo(PeriodPreset.THIS_WEEK)
        assertThat(PeriodPreset.of(today, today, today)).isEqualTo(PeriodPreset.TODAY)
        assertThat(PeriodPreset.of(LocalDate.of(2026, 11, 2), today, today)).isNull()
        assertThat(PeriodPreset.of(null, null, today)).isNull()
    }
}
