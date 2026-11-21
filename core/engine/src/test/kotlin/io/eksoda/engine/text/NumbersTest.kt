package io.eksoda.engine.text

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.hasMessage
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class NumbersTest {
    private val athens = ZoneId.of("Europe/Athens")

    private fun decimal(text: String, culture: Culture) =
        parseDecimal(text, culture).toPlainString()

    @Test
    fun `numbers are read with the culture's separators`() {
        assertThat(decimal("1.234,56", Culture.EL)).isEqualTo("1234.56")
        assertThat(decimal("1,234.56", Culture.EN)).isEqualTo("1234.56")
        assertThat(decimal("0,50", Culture.EL)).isEqualTo("0.50")
        // Both minus signs, a percent sign, no-break spaces.
        assertThat(decimal("-3,50", Culture.EL)).isEqualTo("-3.50")
        assertThat(decimal("− 3,50", Culture.EL)).isEqualTo("-3.50")
        assertThat(decimal("13 %", Culture.EL)).isEqualTo("13")
        assertThat(decimal(" 24,00 ", Culture.EL)).isEqualTo("24.00")
    }

    @Test
    fun `malformed or ambiguous numbers are refused`() {
        for (text in listOf("", "-", "abc", "12.34", "1.23", "1,2,3", "1.234.5")) {
            assertFailure { parseDecimal(text, Culture.EL) }.isInstanceOf<NumberFormatError>()
        }
        assertFailure { parseDecimal("1,234,5", Culture.EN) }
            .isInstanceOf<NumberFormatError>()
            .hasMessage("'1,234,5' is not a valid en number")
    }

    @Test
    fun `a culture may be given by its code, and only a known one`() {
        assertThat(parseDecimal("2,50", "el").toPlainString()).isEqualTo("2.50")
        assertFailure { parseDecimal("2,50", "fr") }
            .isInstanceOf<NumberFormatError>()
            .hasMessage("unknown culture 'fr'")
        assertThat(Culture.fromCode("fr")).isNull()
    }

    @Test
    fun `blank optional numbers are not provided`() {
        assertThat(parseOptionalDecimal(null, Culture.EL)).isNull()
        assertThat(parseOptionalDecimal("  ", Culture.EL)).isNull()
        assertThat(parseOptionalDecimal("2,50", Culture.EL)?.toPlainString()).isEqualTo("2.50")
    }

    @Test
    fun `dates are read in each part order`() {
        val day = LocalDate.of(2026, 7, 13)
        assertThat(parseDate("13/07/2026", DateOrder.DMY)).isEqualTo(day)
        assertThat(parseDate("7-13-2026", DateOrder.MDY)).isEqualTo(day)
        assertThat(parseDate("2026.07.13", DateOrder.YMD)).isEqualTo(day)
    }

    @Test
    fun `malformed dates are refused`() {
        val bad = listOf(
            "not a date", // no date shape
            "13/07/26", // two-digit year
            "113/07/2026", // three-digit day
            "31/02/2026", // no such day
            "01/01/0000", // year 0 (Python dates start at year 1)
        )
        for (text in bad) {
            assertFailure { parseDate(text, DateOrder.DMY) }.isInstanceOf<DateFormatError>()
        }
        assertFailure { parseDate("2026/07/13", DateOrder.DMY) }
            .hasMessage("'2026/07/13' does not match the dmy order")
    }

    @Test
    fun `times with a 12-hour marker or none are read as local Athens time`() {
        fun local(text: String, culture: Culture = Culture.EL, order: DateOrder = DateOrder.DMY) =
            parseLocalDatetime(text, culture, order, athens)
        val evening = local("13/07/2026 9:05 μ.μ.")
        assertThat(evening.toLocalDateTime()).isEqualTo(LocalDateTime.of(2026, 7, 13, 21, 5))
        assertThat(evening.offset).isEqualTo(ZoneOffset.ofHours(3))
        assertThat(local("13/07/2026 12:00:30 π.μ.").toLocalDateTime())
            .isEqualTo(LocalDateTime.of(2026, 7, 13, 0, 0, 30))
        assertThat(local("7/13/2026 1:05 PM", Culture.EN, DateOrder.MDY).toLocalDateTime())
            .isEqualTo(LocalDateTime.of(2026, 7, 13, 13, 5))
        // No marker: 24-hour.
        assertThat(local("13/01/2026 21:05").toLocalDateTime())
            .isEqualTo(LocalDateTime.of(2026, 1, 13, 21, 5))
        assertThat(local("13/01/2026 21:05").offset).isEqualTo(ZoneOffset.ofHours(2))
    }

    @Test
    fun `malformed times are refused`() {
        val bad = listOf(
            "13/07/2026", // no time
            "13/07/2026 9:05 xx", // unknown marker
            "13/07/2026 9:05 PM", // an English marker on a Greek page
            "13/07/2026 13:05 μ.μ.", // not a 12-hour time
            "13/07/2026 25:00", // no such hour
            "31/02/2026 10:00", // no such day
        )
        for (text in bad) {
            assertFailure { parseLocalDatetime(text, Culture.EL, DateOrder.DMY, athens) }
                .isInstanceOf<DateFormatError>()
        }
    }
}
