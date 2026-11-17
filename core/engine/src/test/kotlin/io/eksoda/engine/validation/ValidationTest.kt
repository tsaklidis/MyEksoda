package io.eksoda.engine.validation

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import io.eksoda.engine.canonical.CanonicalReceipt
import io.eksoda.engine.canonical.Document
import io.eksoda.engine.canonical.Identity
import io.eksoda.engine.canonical.Line
import io.eksoda.engine.canonical.Party
import io.eksoda.engine.canonical.Totals
import io.eksoda.engine.canonical.VatEntry
import io.eksoda.engine.mydata.DocumentKind
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.OffsetDateTime

class ValidationTest {
    private fun d(value: String) = BigDecimal(value)

    private val rate = d("13")

    /** Per-line VAT, each rounded to the cent: 0.23 + 0.36 + 0.49 + 0.35 + 0.39 = 1.82. */
    private val lines = listOf(
        "1.80" to "0.23",
        "2.80" to "0.36",
        "3.80" to "0.49",
        "2.72" to "0.35",
        "3.00" to "0.39",
    ).mapIndexed { index, (net, vat) ->
        Line(
            lineNumber = index + 1,
            description = "Item ${index + 1}",
            gross = d(net) + d(vat),
            net = d(net),
            vatRate = rate,
            vat = d(vat),
        )
    }

    /** A 13% receipt with net 14.12 (14.12 × 13% = 1.84) reporting [vat]. */
    private fun receipt(lines: List<Line>, vat: String): CanonicalReceipt {
        val net = "14.12"
        val issuedAt = OffsetDateTime.parse("2026-11-16T10:00:00+02:00")
        return CanonicalReceipt(
            providerCode = "test",
            parserVersion = "1",
            culture = "el",
            sourceUrl = "https://example.invalid/receipt",
            issuer = Party(countryCode = "GR", vatNumber = "000000000", legalName = "Shop"),
            document = Document(
                kind = DocumentKind.SALE,
                issuedAt = issuedAt,
                issuedDate = issuedAt.toLocalDate(),
                currency = "EUR",
            ),
            identity = Identity(mark = "1"),
            totals = Totals(gross = d(net) + d(vat), net = d(net), vat = d(vat)),
            lines = lines,
            vatBreakdown = listOf(VatEntry(net = d(net), vat = d(vat), rate = rate)),
        )
    }

    private fun codes(receipt: CanonicalReceipt) = validate(receipt).map { it.code }

    @Test
    fun `VAT rounded per line is not flagged against VAT of the summed net`() {
        // 14.12 × 13% = 1.84, the receipt reports 1.82: the retailer's per-line rounding.
        assertThat(codes(receipt(lines, vat = "1.82"))).isEmpty()
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
            vatRate = rate,
            vat = d("1.82"),
        )
        assertThat(codes(receipt(listOf(line), vat = "1.82")))
            .containsExactly("vat_rate_mismatch", "vat_breakdown_rate_mismatch")
    }

    @Test
    fun `a breakdown VAT beyond what rounding explains is flagged`() {
        val issues = validate(receipt(emptyList(), vat = "1.79"))
        assertThat(issues.map { Triple(it.code, it.expected, it.actual) })
            .containsExactly(Triple("vat_breakdown_rate_mismatch", "1.84", "1.79"))
    }
}
