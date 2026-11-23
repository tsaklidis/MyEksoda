package io.eksoda.engine.analytics

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

class MonthAmountsTest {
    private val nov = YearMonth.of(2026, 11)
    private val oct = YearMonth.of(2026, 10)
    private val sep = YearMonth.of(2026, 9)

    @Test
    fun `a month's own row wins over the default, even without an amount`() {
        val rows = mapOf(oct to BigDecimal("600"), sep to null)
        assertThat(amountFor(oct, BigDecimal("700"), rows)).isEqualTo(BigDecimal("600"))
        assertThat(amountFor(sep, BigDecimal("700"), rows)).isNull()
        assertThat(amountFor(nov, BigDecimal("700"), rows)).isEqualTo(BigDecimal("700"))
        assertThat(amountFor(nov, null, rows)).isNull()
    }

    @Test
    fun `the past months without a row are frozen, oldest first`() {
        assertThat(monthsToFreeze(sep.minusMonths(1), nov, setOf(oct)))
            .containsExactly(YearMonth.of(2026, 8), sep)
    }

    @Test
    fun `nothing is frozen from the current month or a later one`() {
        assertThat(monthsToFreeze(nov, nov, emptySet())).isEmpty()
        assertThat(monthsToFreeze(nov.plusMonths(2), nov, emptySet())).isEmpty()
    }

    @Test
    fun `at most two years are frozen`() {
        val frozen = monthsToFreeze(YearMonth.of(2010, 1), nov, emptySet())
        assertThat(frozen.size).isEqualTo(MAX_FROZEN_MONTHS)
        assertThat(frozen.first()).isEqualTo(nov.minusMonths(MAX_FROZEN_MONTHS.toLong()))
        assertThat(frozen.last()).isEqualTo(oct)
    }

    @Test
    fun `only the current month can be changed`() {
        val today = LocalDate.of(2026, 11, 23)
        assertThat(monthEditable(nov, today)).isTrue()
        assertThat(monthEditable(oct, today)).isFalse()
        assertThat(monthEditable(nov.plusMonths(1), today)).isFalse()
    }

    @Test
    fun `last month's variable bills stay open until the 10th`() {
        assertThat(monthEditable(oct, LocalDate.of(2026, 11, 10), variable = true)).isTrue()
        assertThat(monthEditable(oct, LocalDate.of(2026, 11, 11), variable = true)).isFalse()
        assertThat(monthEditable(oct, LocalDate.of(2026, 11, 3))).isFalse()
        assertThat(monthEditable(sep, LocalDate.of(2026, 11, 3), variable = true)).isFalse()
    }
}
