package io.eksoda.core.designsystem.chart

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import org.junit.Test
import java.math.BigDecimal
import java.math.RoundingMode

class LineChartDomainTest {
    private fun prices(vararg values: String) = values.map(::BigDecimal)

    /** The grid values as the chart shows them, in cents. */
    private fun gridInCents(values: List<BigDecimal>): List<String> =
        gridValues(domainOf(values, emptyList(), null), 4)
            .map { it.setScale(2, RoundingMode.HALF_UP).toPlainString() }

    @Test
    fun `a change of a cent is a gentle slope with grid values apart`() {
        val (low, high) = domainOf(prices("9.95", "9.96"), emptyList(), null)
        // At least 5% of the highest value above and below: the line spans a small part.
        assertThat(BigDecimal("9.95") - low).isGreaterThan(BigDecimal("0.4"))
        assertThat(high - BigDecimal("9.96")).isGreaterThan(BigDecimal("0.4"))
        val grid = gridInCents(prices("9.95", "9.96"))
        assertThat(grid.toSet().size).isEqualTo(4)
    }

    @Test
    fun `prices of a few cents still have four different grid values`() {
        assertThat(gridInCents(prices("0.05", "0.06")).toSet().size).isEqualTo(4)
        assertThat(gridInCents(prices("0.05", "0.05")).toSet().size).isEqualTo(4)
    }

    @Test
    fun `negative values keep the bottom below the top, equal ones in the middle`() {
        val (flatLow, flatHigh) = domainOf(prices("-5", "-5"), emptyList(), null)
        assertThat(flatLow.compareTo(BigDecimal("-5.6"))).isEqualTo(0)
        assertThat(flatHigh.compareTo(BigDecimal("-4.4"))).isEqualTo(0)
        val (low, high) = domainOf(prices("-10", "-2"), emptyList(), null)
        assertThat(low.compareTo(BigDecimal("-10.96"))).isEqualTo(0)
        assertThat(high.compareTo(BigDecimal("-1.04"))).isEqualTo(0)
        assertThat(gridInCents(prices("-0.05", "-0.05")).toSet().size).isEqualTo(4)
    }

    @Test
    fun `totals start at zero and wide ranges keep their padding`() {
        val (low, high) = domainOf(prices("10", "500"), emptyList(), null)
        assertThat(low).isEqualTo(BigDecimal.ZERO)
        assertThat(high.compareTo(BigDecimal("558.80"))).isEqualTo(0)
        // Nothing to draw: 0 to 1, with its padding.
        assertThat(domainOf(emptyList(), emptyList(), null).second.compareTo(BigDecimal("1.12")))
            .isEqualTo(0)
    }
}
