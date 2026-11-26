package io.eksoda.core.ui.text

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.startsWith
import io.eksoda.core.model.Money
import org.junit.Test
import java.math.BigDecimal
import java.util.Locale

class FormatAmountTest {
    private val greek = Locale.forLanguageTag("el-GR")

    @Test
    fun `a unit price with more places rounds half up to cents, as the statistics do`() {
        // Half-even would give 9,94 here, while Product detail shows 9,95.
        assertThat(formatAmount(BigDecimal("9.9450"), greek)).isEqualTo("9,95")
        assertThat(formatAmount(BigDecimal("9.9449"), greek)).isEqualTo("9,94")
        // The space before € depends on the JDK's locale data.
        assertThat(formatMoney(Money(BigDecimal("9.9450"), "EUR"), greek)).startsWith("9,95")
    }

    @Test
    fun `negative money has a true minus sign`() {
        assertThat(formatMoney(Money(BigDecimal("1.20"), "EUR"), greek, negative = true))
            .startsWith("−1,20")
    }
}
