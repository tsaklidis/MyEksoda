package io.eksoda.engine.classification

import assertk.assertThat
import assertk.assertions.isEqualTo
import io.eksoda.engine.canonical.Line
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class ClassificationTest {
    private fun type(
        description: String,
        gross: String,
        net: String? = null,
        fees: String? = null,
        otherTaxes: String? = null,
    ): LineType = classify(
        Line(
            lineNumber = 1,
            description = description,
            gross = BigDecimal(gross),
            net = net?.let(::BigDecimal),
            fees = fees?.let(::BigDecimal),
            otherTaxes = otherTaxes?.let(::BigDecimal),
        ),
    )

    @Test
    fun `negative amounts are discounts when named so, otherwise other`() {
        assertThat(type("ΕΚΠΤΩΣΗ ΠΡΟΣΦΟΡΑΣ", "-0.50")).isEqualTo(LineType.DISCOUNT)
        assertThat(type("DISCOUNT 10%", "-1.00")).isEqualTo(LineType.DISCOUNT)
        assertThat(type("ΕΠΙΣΤΡΟΦΗ ΓΑΛΑ", "-1.20")).isEqualTo(LineType.OTHER)
    }

    @Test
    fun `keywords match the start of words`() {
        assertThat(type("Κουπόνι δώρου", "0.00")).isEqualTo(LineType.DISCOUNT)
        assertThat(type("Περιβαλλοντικό Τέλος", "0.09")).isEqualTo(LineType.FEE)
        assertThat(type("ΕΓΓΥΗΣ ΦΙΑΛΗΣ", "0.10")).isEqualTo(LineType.FEE)
        // A two-word keyword needs both words, in order.
        assertThat(type("ΠΕΡΙΒΑΛΛΟΝΤΙΚΟ ΚΑΘΑΡΙΣΤΙΚΟ", "3.40")).isEqualTo(LineType.PRODUCT)
        // Bags are sold as products.
        assertThat(type("ΣΑΚΟΥΛΑ ΑΠΟΡΡΙΜΜΑΤΩΝ", "2.10")).isEqualTo(LineType.PRODUCT)
    }

    @Test
    fun `a zero net amount carrying only charges is a fee`() {
        assertThat(type("ΑΝΑΚΥΚΛΩΣΗ", "0.04", net = "0", fees = "0.04")).isEqualTo(LineType.FEE)
        assertThat(type("ΦΟΡΟΣ", "0.02", net = "0", otherTaxes = "0.02")).isEqualTo(LineType.FEE)
        assertThat(type("ΔΩΡΟ", "0", net = "0")).isEqualTo(LineType.PRODUCT)
        assertThat(type("ΓΑΛΑ", "1.20", net = "1.06")).isEqualTo(LineType.PRODUCT)
    }

    @Test
    fun `an already normalized description is used as given`() {
        val line = Line(lineNumber = 1, description = "anything", gross = BigDecimal("1"))
        assertThat(classify(line, normalizedDescription = "ΕΚΠΤΩΣΗ")).isEqualTo(LineType.DISCOUNT)
    }
}
