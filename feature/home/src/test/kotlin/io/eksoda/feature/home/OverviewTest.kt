package io.eksoda.feature.home

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime

/** The Overview's small rules: what is left per day, and the greeting. */
class OverviewTest {
    @Test
    fun `what is left is spread over the days to the month's end, today included`() {
        // 9 days from the 22nd to the 30th of November.
        assertThat(perDay(BigDecimal("286.40"), LocalDate.of(2026, 11, 22))?.toPlainString())
            .isEqualTo("31")
        // On the last day, all of it.
        assertThat(perDay(BigDecimal("40"), LocalDate.of(2026, 11, 30))?.toPlainString())
            .isEqualTo("40")
    }

    @Test
    fun `nothing per day once the budget is used up`() {
        assertThat(perDay(BigDecimal.ZERO, LocalDate.of(2026, 11, 22))).isNull()
        assertThat(perDay(BigDecimal("-12"), LocalDate.of(2026, 11, 22))).isNull()
    }

    @Test
    fun `the greeting follows the time of day`() {
        assertThat(greetingAt(LocalTime.of(7, 0))).isEqualTo(Greeting.MORNING)
        assertThat(greetingAt(LocalTime.of(13, 0))).isEqualTo(Greeting.DAY)
        assertThat(greetingAt(LocalTime.of(21, 0))).isEqualTo(Greeting.EVENING)
        assertThat(greetingAt(LocalTime.of(2, 0))).isEqualTo(Greeting.EVENING)
    }
}
