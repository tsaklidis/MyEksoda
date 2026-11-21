package io.eksoda.engine.validation

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import io.eksoda.engine.canonical.Line
import io.eksoda.engine.canonical.PriceBasis
import org.junit.jupiter.api.Test

/** Line composition (R4), unit price (R5), line discount (R13) and the line sums (R7). */
class LineRulesTest {
    @Test
    fun `the clean receipt has no issues`() {
        assertThat(found(CLEAN)).isEmpty()
    }

    @Test
    fun `a line whose amounts do not add up is flagged, and so is the gross total`() {
        assertThat(found(withFirstLine { it.copy(gross = d("3.00")) })).containsExactly(
            listOf("line_amount_mismatch", 1, "2.03", "3.00"),
            listOf("total_mismatch", null, "16.91", "15.94"),
        )
    }

    @Test
    fun `a line without its net is left out of the line-sum checks`() {
        assertThat(found(withFirstLine { it.copy(net = null) })).isEmpty()
    }

    @Test
    fun `quantity times a VAT-inclusive unit price is compared with the gross`() {
        val priced = withFirstLine {
            it.copy(quantity = d("2"), unitPrice = d("1.10"), unitPriceBasis = PriceBasis.GROSS)
        }
        assertThat(found(priced))
            .containsExactly(listOf("unit_price_mismatch", 1, "2.20", "2.03"))
    }

    @Test
    fun `quantity times a net unit price is compared with the net`() {
        val priced = withFirstLine {
            it.copy(quantity = d("1"), unitPrice = d("2.00"), unitPriceBasis = PriceBasis.NET)
        }
        assertThat(found(priced))
            .containsExactly(listOf("unit_price_mismatch", 1, "2.00", "1.80"))
    }

    @Test
    fun `no unit price check without a basis, a quantity or the pre-discount value`() {
        fun priced(line: Line) = line.copy(
            quantity = d("2"),
            unitPrice = d("1.10"),
            unitPriceBasis = PriceBasis.GROSS,
        )
        // Unknown basis.
        assertThat(found(withFirstLine { priced(it).copy(unitPriceBasis = PriceBasis.UNKNOWN) }))
            .isEmpty()
        // No quantity.
        assertThat(found(withFirstLine { priced(it).copy(quantity = null) })).isEmpty()
        // Discounted, and the value before the discount is not printed.
        assertThat(found(withFirstLine { priced(it).copy(discount = d("0.17")) })).isEmpty()
    }

    @Test
    fun `a line discount that does not lead to the line total is flagged`() {
        val discounted = withFirstLine {
            it.copy(grossBeforeDiscount = d("3.00"), discount = d("0.50"))
        }
        assertThat(found(discounted))
            .containsExactly(listOf("discount_mismatch", 1, "2.50", "2.03"))
        val matching = withFirstLine {
            it.copy(grossBeforeDiscount = d("2.53"), discount = d("0.50"))
        }
        assertThat(found(matching)).isEmpty()
    }
}
