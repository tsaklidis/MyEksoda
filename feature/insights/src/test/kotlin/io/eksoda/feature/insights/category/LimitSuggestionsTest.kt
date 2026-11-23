package io.eksoda.feature.insights.category

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.Test
import java.math.BigDecimal

/** The limits offered on a category's screen. */
class LimitSuggestionsTest {
    private fun plain(usual: String?) =
        limitSuggestions(usual?.let(::BigDecimal)).map { it.toPlainString() }

    @Test
    fun `around what the category usually costs, rounded up to 50`() {
        assertThat(plain("268.30")).isEqualTo(listOf("250", "300", "350"))
        assertThat(plain("300")).isEqualTo(listOf("250", "300", "350"))
    }

    @Test
    fun `never below 50`() {
        assertThat(plain("12.40")).isEqualTo(listOf("50", "100", "150"))
    }

    @Test
    fun `a category that costs nothing yet gets round amounts`() {
        assertThat(plain(null)).isEqualTo(listOf("50", "100", "200"))
        assertThat(plain("0")).isEqualTo(listOf("50", "100", "200"))
    }
}
