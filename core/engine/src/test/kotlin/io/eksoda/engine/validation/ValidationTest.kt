package io.eksoda.engine.validation

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import io.eksoda.engine.canonical.Line
import org.junit.jupiter.api.Test

/** VAT rounding (R6, R14) and discount totals (R17). Line and receipt rules: their own tests. */
class ValidationTest {
    @Test
    fun `VAT rounded per line is not flagged against VAT of the summed net`() {
        // 14.12 × 13% = 1.84, the receipt reports 1.82: the retailer's per-line rounding.
        assertThat(codes(receipt(FIVE_LINES, vat = "1.82"))).isEmpty()
    }

    @Test
    fun `a summary-only receipt allows a two-cent rounding difference per rate`() {
        assertThat(codes(receipt(emptyList(), vat = "1.82"))).isEmpty()
    }

    @Test
    fun `a single line cannot drift two cents`() {
        val line = Line(
            lineNumber = 1,
            description = "Item",
            gross = d("15.94"),
            net = d("14.12"),
            vatRate = RATE,
            vat = d("1.82"),
        )
        assertThat(codes(receipt(listOf(line), vat = "1.82")))
            .containsExactly("vat_rate_mismatch", "vat_breakdown_rate_mismatch")
    }

    @Test
    fun `the rate's rounding remainder on one line is not flagged`() {
        // VAT per rate on the summed net (14.12 × 13% = 1.84): the 2 cents the lines lose by
        // rounding one by one go on the largest line (3.80 × 13% = 0.49, reported 0.51).
        val remainder = FIVE_LINES.map { line ->
            if (line.lineNumber == 3) {
                line.copy(vat = d("0.51"), gross = d("3.80") + d("0.51"))
            } else {
                line
            }
        }
        assertThat(codes(receipt(remainder, vat = "1.84"))).isEmpty()
    }

    @Test
    fun `a line beyond the rate's rounding is flagged even when the rate adds up`() {
        fun line(number: Int, vat: String) = Line(
            lineNumber = number,
            description = "Item $number",
            gross = d("10.00") + d(vat),
            net = d("10.00"),
            vatRate = RATE,
            vat = d(vat),
        )
        // 1.35 + 1.25 = 20.00 × 13%, but two lines can only round half a cent each.
        val issues = validate(receipt(listOf(line(1, "1.35"), line(2, "1.25")), vat = "2.60"))
        assertThat(issues.filter { it.code == "vat_rate_mismatch" }.map { it.lineNumber })
            .containsExactly(1, 2)
    }

    @Test
    fun `line discounts are compared with the discount total only when lines carry them`() {
        val withTotal = CLEAN.copy(totals = CLEAN.totals.copy(discount = d("1.00")))
        // A receipt-level discount: no line carries one.
        assertThat(codes(withTotal)).isEmpty()
        val discounted = FIVE_LINES.map { it.copy(discount = d("0.20")) }
        assertThat(codes(withTotal.copy(lines = discounted))).isEmpty()
        val short = discounted.map { it.copy(discount = d("0.10")) }
        assertThat(found(withTotal.copy(lines = short)))
            .containsExactly(listOf("line_discounts_mismatch", null, "0.50", "1.00"))
    }

    @Test
    fun `a breakdown VAT beyond what rounding explains is flagged`() {
        val issues = validate(receipt(emptyList(), vat = "1.79"))
        assertThat(issues.map { Triple(it.code, it.expected, it.actual) })
            .containsExactly(Triple("vat_breakdown_rate_mismatch", "1.84", "1.79"))
    }
}
