// Port of backend/apps/receipts/validation.py.
package io.eksoda.engine.validation

import io.eksoda.engine.canonical.CanonicalReceipt
import io.eksoda.engine.canonical.Line
import io.eksoda.engine.canonical.PriceBasis
import io.eksoda.engine.mydata.DocumentKind
import io.eksoda.engine.text.pyDivide
import io.eksoda.engine.text.pyStr
import java.math.BigDecimal
import java.math.RoundingMode

// Provider-neutral receipt validation (requirements §10.2, TD §11).
//
// Rules R1–R3 (identity, view agreement, required fields) are fatal and enforced by the provider
// parsers. The rules here only produce warnings: source values are never changed to make a rule
// pass. Rules adapt to the receipt's `coverage` (e.g. summary-only sources have no lines; some
// sources have no line VAT amounts).
//
// R4  line composition          net + VAT + fees + … − withheld − deductions = gross
// R5  unit price                qty × unit price = gross before discount (or gross)
// R6  VAT rate                  net × rate = VAT
// R7  totals vs lines           Σ line values = receipt totals (only fully provided values)
// R8  receipt composition       net + VAT + fees + … − withheld − deductions = gross
// R9  VAT breakdown             Σ breakdown net/VAT = net/VAT totals
// R10 payments                  Σ payments = gross
// R11 MARK                      present for myDATA-registered documents
// R12 currency                  EUR
// R13 line discount             gross before discount − discount = gross
// R14 VAT breakdown rate        breakdown net × rate = breakdown VAT, allowing per-line rounding
// R15 document kind             the document type is recognized
// R16 total discount            total before discount − discount = gross
// +   provider notices          conditions the provider detected (e.g. signature mismatch)

public val LINE_TOLERANCE: BigDecimal = BigDecimal("0.01")
public val TOTAL_TOLERANCE: BigDecimal = BigDecimal("0.02")
public const val EXPECTED_CURRENCY: String = "EUR"
private val ZERO = BigDecimal("0")
private val HUNDRED = BigDecimal(100)
private val HALF_CENT = BigDecimal("0.005")
private const val CENT_SCALE = 2

/** A warning. [expected] and [actual] are `str(Decimal)` texts, or the raw value. */
public data class ValidationIssue(
    val code: String,
    val message: String,
    val lineNumber: Int? = null,
    val expected: String? = null,
    val actual: String? = null,
)

private fun z(value: BigDecimal?): BigDecimal = value ?: ZERO

/** `quantize(CENT, ROUND_HALF_UP)`. */
private fun money(value: BigDecimal): BigDecimal = value.setScale(CENT_SCALE, RoundingMode.HALF_UP)

private fun off(expected: BigDecimal, actual: BigDecimal, tolerance: BigDecimal): Boolean =
    (expected - actual).abs() > tolerance

private fun issue(
    code: String,
    message: String,
    line: Int? = null,
    expected: BigDecimal? = null,
    actual: BigDecimal? = null,
): ValidationIssue = ValidationIssue(code, message, line, expected?.pyStr(), actual?.pyStr())

public fun validate(receipt: CanonicalReceipt): List<ValidationIssue> {
    val issues = mutableListOf<ValidationIssue>()
    for (line in receipt.lines) {
        issues += lineComposition(line)
        issues += unitPrice(line)
        issues += vatRate(line)
        issues += lineDiscount(line)
    }
    issues += totalsMatchLines(receipt)
    issues += receiptComposition(receipt)
    issues += vatBreakdown(receipt)
    issues += vatBreakdownRates(receipt)
    issues += payments(receipt)
    issues += mark(receipt)
    issues += currency(receipt)
    issues += documentKind(receipt)
    issues += totalDiscount(receipt)
    issues += receipt.notices.map { issue(it.code, it.message) }
    return issues
}

public fun lineExpectedGross(line: Line): BigDecimal =
    z(line.net) + z(line.vat) + z(line.fees) + z(line.otherTaxes) + z(line.stampDuty) +
        z(line.digitalFee) -
        z(line.withheld) - z(line.deductions)

private fun lineComposition(line: Line): List<ValidationIssue> { // R4
    if (line.net == null || line.vat == null) return emptyList()
    val expected = lineExpectedGross(line)
    if (!off(expected, line.gross, LINE_TOLERANCE)) return emptyList()
    return listOf(
        issue(
            "line_amount_mismatch",
            "Line amounts do not add up to the line total.",
            line.lineNumber,
            expected,
            line.gross,
        ),
    )
}

private fun unitPrice(line: Line): List<ValidationIssue> { // R5
    val unitPrice = line.unitPrice ?: return emptyList()
    val quantity = line.quantity ?: return emptyList()
    val target = when {
        line.grossBeforeDiscount != null -> line.grossBeforeDiscount

        line.discount != null -> return emptyList()

        // discounted, and the pre-discount value is unknown
        line.unitPriceBasis == PriceBasis.GROSS -> line.gross

        line.unitPriceBasis == PriceBasis.NET -> line.net

        else -> null
    } ?: return emptyList()
    val expected = money(quantity * unitPrice)
    if (!off(expected, target, LINE_TOLERANCE)) return emptyList()
    return listOf(
        issue(
            "unit_price_mismatch",
            "Quantity × unit price does not match the line amount.",
            line.lineNumber,
            expected,
            target,
        ),
    )
}

private fun vatRate(line: Line): List<ValidationIssue> { // R6
    val rate = line.vatRate ?: return emptyList()
    val net = line.net ?: return emptyList()
    val vat = line.vat ?: return emptyList()
    val expected = money((net * rate).pyDivide(HUNDRED))
    if (!off(expected, vat, LINE_TOLERANCE)) return emptyList()
    return listOf(
        issue(
            "vat_rate_mismatch",
            "The VAT amount does not match the VAT rate.",
            line.lineNumber,
            expected,
            vat,
        ),
    )
}

private fun lineDiscount(line: Line): List<ValidationIssue> { // R13
    val before = line.grossBeforeDiscount ?: return emptyList()
    val discount = line.discount ?: return emptyList()
    val expected = before - discount
    if (!off(expected, line.gross, LINE_TOLERANCE)) return emptyList()
    return listOf(
        issue(
            "discount_mismatch",
            "The value before discount minus the discount does not match the line total.",
            line.lineNumber,
            expected,
            line.gross,
        ),
    )
}

private fun totalsMatchLines(receipt: CanonicalReceipt): List<ValidationIssue> { // R7
    if (receipt.lines.isEmpty()) return emptyList()
    val fields: List<Triple<String, (Line) -> BigDecimal?, BigDecimal?>> = listOf(
        Triple("net", { it.net }, receipt.totals.net),
        Triple("vat", { it.vat }, receipt.totals.vat),
        Triple("gross", { it.gross }, receipt.totals.gross),
    )
    return fields.mapNotNull { (name, value, total) ->
        val values = receipt.lines.map(value)
        // Only compare values every line provides.
        if (total == null || values.any { it == null }) return@mapNotNull null
        val lineSum = values.fold(ZERO) { sum, item -> sum + checkNotNull(item) }
        if (!off(lineSum, total, TOTAL_TOLERANCE)) return@mapNotNull null
        issue(
            "total_mismatch",
            "The receipt $name total differs from the sum of its lines.",
            null,
            lineSum,
            total,
        )
    }
}

private fun receiptComposition(receipt: CanonicalReceipt): List<ValidationIssue> { // R8
    val t = receipt.totals
    val net = t.net ?: return emptyList()
    val vat = t.vat ?: return emptyList()
    val expected = net + vat + z(t.fees) + z(t.otherTaxes) + z(t.stampDuty) + z(t.digitalFee) -
        z(t.withheld) - z(t.deductions)
    if (!off(expected, t.gross, LINE_TOLERANCE)) return emptyList()
    return listOf(
        issue(
            "receipt_amount_mismatch",
            "The receipt totals do not add up to the gross total.",
            null,
            expected,
            t.gross,
        ),
    )
}

private fun vatBreakdown(receipt: CanonicalReceipt): List<ValidationIssue> { // R9
    if (receipt.vatBreakdown.isEmpty()) return emptyList()
    val fields = listOf(
        Triple(
            "net",
            receipt.totals.net,
            receipt.vatBreakdown.fold(ZERO) { sum, entry ->
                sum +
                    entry.net
            },
        ),
        Triple(
            "vat",
            receipt.totals.vat,
            receipt.vatBreakdown.fold(ZERO) { sum, entry ->
                sum +
                    entry.vat
            },
        ),
    )
    return fields.mapNotNull { (name, total, breakdown) ->
        if (total == null || !off(breakdown, total, LINE_TOLERANCE)) return@mapNotNull null
        issue(
            "vat_breakdown_mismatch",
            "The VAT breakdown does not add up to the $name total.",
            null,
            breakdown,
            total,
        )
    }
}

private fun vatBreakdownRates(receipt: CanonicalReceipt): List<ValidationIssue> = // R14
    receipt.vatBreakdown.mapNotNull { entry ->
        val rate = entry.rate ?: return@mapNotNull null
        val expected = money((entry.net * rate).pyDivide(HUNDRED))
        val tolerance = breakdownRateTolerance(receipt, rate)
        if (!off(expected, entry.vat, tolerance)) return@mapNotNull null
        issue(
            "vat_breakdown_rate_mismatch",
            "The VAT for the ${rate.pyStr()}% rate does not match its net amount.",
            null,
            expected,
            entry.vat,
        )
    }

/**
 * How far a rate's reported VAT may be from `net × rate`. Retailers compute VAT per line, round
 * each line to the cent and add the rounded amounts, so the reported VAT can drift from the VAT
 * of the summed net: e.g. net 14.12 at 13% gives 1.84, while the receipt reports 1.82. Each line
 * contributes at most half a cent, or `half a cent × (1 + rate)` when the retailer rounds the
 * net derived from a VAT-inclusive price. Without lines at this rate the line count is unknown,
 * so [TOTAL_TOLERANCE] applies.
 */
private fun breakdownRateTolerance(receipt: CanonicalReceipt, rate: BigDecimal): BigDecimal {
    val lines = receipt.lines.count { it.vatRate?.compareTo(rate) == 0 }
    if (lines == 0) return TOTAL_TOLERANCE
    val perLine = HALF_CENT * (BigDecimal.ONE + rate.pyDivide(HUNDRED))
    return LINE_TOLERANCE.max(perLine * BigDecimal(lines))
}

private fun payments(receipt: CanonicalReceipt): List<ValidationIssue> { // R10
    if (receipt.payments.isEmpty()) return emptyList()
    val paid = receipt.payments.fold(ZERO) { sum, payment -> sum + payment.amount }
    if (!off(paid, receipt.totals.gross, LINE_TOLERANCE)) return emptyList()
    return listOf(
        issue(
            "payment_mismatch",
            "The payments do not add up to the gross total.",
            null,
            receipt.totals.gross,
            paid,
        ),
    )
}

private fun mark(receipt: CanonicalReceipt): List<ValidationIssue> { // R11
    val identity = receipt.identity
    val missing =
        receipt.coverage.mydataRegistered && identity.mark == null && !identity.transmissionFailure
    return if (missing) {
        listOf(
            issue("mark_missing", "The receipt has no AADE registration number."),
        )
    } else {
        emptyList()
    }
}

private fun currency(receipt: CanonicalReceipt): List<ValidationIssue> { // R12
    if (receipt.document.currency == EXPECTED_CURRENCY) return emptyList()
    return listOf(
        ValidationIssue(
            "non_eur_currency",
            "The receipt is not in EUR.",
            null,
            EXPECTED_CURRENCY,
            receipt.document.currency,
        ),
    )
}

private fun documentKind(receipt: CanonicalReceipt): List<ValidationIssue> { // R15
    if (receipt.document.kind != DocumentKind.OTHER) return emptyList()
    // Python's `type_label or type_code`: an empty label falls back to the code.
    val printed =
        receipt.document.typeLabel?.takeIf { it.isNotEmpty() } ?: receipt.document.typeCode
    return listOf(
        ValidationIssue(
            "unknown_document_type",
            "The document type is not recognized; check whether it is a sale or a refund.",
            null,
            null,
            printed,
        ),
    )
}

private fun totalDiscount(receipt: CanonicalReceipt): List<ValidationIssue> { // R16
    val t = receipt.totals
    val before = t.grossBeforeDiscount ?: return emptyList()
    val discount = t.discount ?: return emptyList()
    val expected = before - discount
    if (!off(expected, t.gross, LINE_TOLERANCE)) return emptyList()
    return listOf(
        issue(
            "total_discount_mismatch",
            "The total before discount minus the discount does not match the gross total.",
            null,
            expected,
            t.gross,
        ),
    )
}
