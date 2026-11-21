package io.eksoda.engine.validation

import io.eksoda.engine.canonical.CanonicalReceipt
import io.eksoda.engine.canonical.Document
import io.eksoda.engine.canonical.Identity
import io.eksoda.engine.canonical.Line
import io.eksoda.engine.canonical.Party
import io.eksoda.engine.canonical.Totals
import io.eksoda.engine.canonical.VatEntry
import io.eksoda.engine.mydata.DocumentKind
import java.math.BigDecimal
import java.time.OffsetDateTime

// Receipts shared by the validation tests.

internal fun d(value: String) = BigDecimal(value)

internal val RATE = d("13")

/** Per-line VAT, each rounded to the cent: 0.23 + 0.36 + 0.49 + 0.35 + 0.39 = 1.82. */
internal val FIVE_LINES = listOf(
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
        vatRate = RATE,
        vat = d(vat),
    )
}

/** A 13% receipt with net 14.12 (14.12 × 13% = 1.84) reporting [vat]. */
internal fun receipt(lines: List<Line>, vat: String): CanonicalReceipt {
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
        vatBreakdown = listOf(VatEntry(net = d(net), vat = d(vat), rate = RATE)),
    )
}

/** Net 14.12 + VAT 1.82 = 15.94 over [FIVE_LINES]: no issue. */
internal val CLEAN = receipt(FIVE_LINES, vat = "1.82")

/** [CLEAN] with its first line (net 1.80, VAT 0.23, gross 2.03) changed. */
internal fun withFirstLine(change: (Line) -> Line) =
    CLEAN.copy(lines = listOf(change(FIVE_LINES.first())) + FIVE_LINES.drop(1))

internal fun codes(receipt: CanonicalReceipt) = validate(receipt).map { it.code }

/** (code, line, expected, actual) of every issue. */
internal fun found(receipt: CanonicalReceipt) =
    validate(receipt).map { listOf(it.code, it.lineNumber, it.expected, it.actual) }
