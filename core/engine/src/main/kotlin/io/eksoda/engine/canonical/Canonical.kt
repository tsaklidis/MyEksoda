// Port of backend/apps/receipts/canonical.py (the types; to_json is in CanonicalJson.kt).
package io.eksoda.engine.canonical

import io.eksoda.engine.mydata.DocumentKind
import io.eksoda.engine.mydata.PaymentMethod
import io.eksoda.engine.mydata.Unit
import kotlinx.serialization.json.JsonObject
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime

// Provider-independent receipt representation (requirements §7.2, TD §9.1).
//
// Provider adapters produce a CanonicalReceipt; the core only consumes this type. Amounts are
// BigDecimal and always keep the values printed by the source (scale included, as Python's
// Decimal does). Sources differ in what they provide, so every receipt states its `coverage`.
// Maps keep insertion order, like Python dicts.

public enum class PriceBasis(public val code: String) {
    GROSS("gross"),
    NET("net"),
    UNKNOWN("unknown"),
}

public enum class IssueTime(public val code: String) {
    EXACT("exact"),

    /** E.g. the transmission time on the same day. */
    APPROXIMATE("approximate"),
    DATE_ONLY("date_only"),
}

/** What the source provides. Validation and analytics adapt to it. */
public data class Coverage(
    /** Purchased items are listed. */
    val lineItems: Boolean = true,
    /** Lines carry their own net and VAT amounts. */
    val lineTaxAmounts: Boolean = true,
    /** Type/VAT/unit codes are myDATA codes, not derived from labels. */
    val officialCodes: Boolean = true,
    /** An AADE MARK is expected. */
    val mydataRegistered: Boolean = true,
    val issueTime: IssueTime = IssueTime.EXACT,
    /** Two independent renderings were compared. */
    val crossChecked: Boolean = true,
)

/** A condition the provider detected that the user should see (becomes a warning). */
public data class Notice(val code: String, val message: String)

public data class Address(
    val street: String? = null,
    val number: String? = null,
    val city: String? = null,
    val postalCode: String? = null,
    val country: String? = null,
)

public data class Party(
    val countryCode: String,
    val vatNumber: String,
    val legalName: String,
    val activity: String? = null,
    val taxOffice: String? = null,
    val gemiNumber: String? = null,
    val address: Address? = null,
)

public data class Branch(val number: String? = null, val address: Address? = null)

public data class Document(
    val kind: DocumentKind,
    /** The issue time with the offset the source implies; the wall time is kept as printed. */
    val issuedAt: OffsetDateTime,
    val issuedDate: LocalDate,
    val currency: String,
    /** myDATA type; null when the source gives only a label. */
    val typeCode: String? = null,
    val typeLabel: String? = null,
    val series: String? = null,
    val number: String? = null,
    val formattedNumber: String? = null,
)

public data class Identity(
    val uid: String? = null,
    val mark: String? = null,
    val cancellationMark: String? = null,
    val transmissionFailure: Boolean = false,
    /** Natural key for sources without MARK/UID (e.g. cash registers). */
    val sourceKey: String? = null,
)

public data class Totals(
    val gross: BigDecimal,
    val net: BigDecimal? = null,
    val vat: BigDecimal? = null,
    val withheld: BigDecimal? = null,
    val fees: BigDecimal? = null,
    val otherTaxes: BigDecimal? = null,
    val stampDuty: BigDecimal? = null,
    val digitalFee: BigDecimal? = null,
    val deductions: BigDecimal? = null,
    val grossBeforeDiscount: BigDecimal? = null,
    val discount: BigDecimal? = null,
)

public data class VatEntry(
    val net: BigDecimal,
    val vat: BigDecimal,
    val rate: BigDecimal? = null,
    val categoryCode: String? = null,
)

public data class Payment(
    val amount: BigDecimal,
    val method: PaymentMethod? = null,
    /** myDATA code when the source shows it. */
    val methodCode: String? = null,
    val methodLabel: String? = null,
    val tip: BigDecimal = BigDecimal("0"),
    /** Inferred (e.g. cash = total − card), not printed by the source. */
    val derived: Boolean = false,
)

public data class Line(
    val lineNumber: Int,
    val description: String,
    /** Amount paid for the line, after any line discount. */
    val gross: BigDecimal,
    val quantity: BigDecimal? = null,
    val unit: Unit? = null,
    /** myDATA code when the source shows it. */
    val unitCode: String? = null,
    val unitLabel: String? = null,
    val unitPrice: BigDecimal? = null,
    val unitPriceBasis: PriceBasis = PriceBasis.UNKNOWN,
    val grossBeforeDiscount: BigDecimal? = null,
    val discount: BigDecimal? = null,
    val net: BigDecimal? = null,
    val vatCategoryCode: String? = null,
    val vatRate: BigDecimal? = null,
    val vat: BigDecimal? = null,
    val withheld: BigDecimal? = null,
    val fees: BigDecimal? = null,
    val otherTaxes: BigDecimal? = null,
    val stampDuty: BigDecimal? = null,
    val digitalFee: BigDecimal? = null,
    val deductions: BigDecimal? = null,
    val merchantProductCode: String? = null,
    val barcode: String? = null,
    val lineTypeCode: String? = null,
    val annotationCode: String? = null,
    val vatExemptionCode: String? = null,
    val raw: Map<String, String> = emptyMap(),
)

public data class CanonicalReceipt(
    val providerCode: String,
    val parserVersion: String,
    val culture: String,
    /** The receipt link: a credential. Never log it (§5.5). */
    val sourceUrl: String,
    val issuer: Party,
    val document: Document,
    val identity: Identity,
    val totals: Totals,
    /** Empty for summary-only sources (`coverage.lineItems` false). */
    val lines: List<Line>,
    val coverage: Coverage = Coverage(),
    val branch: Branch? = null,
    val vatBreakdown: List<VatEntry> = emptyList(),
    val payments: List<Payment> = emptyList(),
    val notices: List<Notice> = emptyList(),
    val providerPayload: JsonObject = JsonObject(emptyMap()),
    val unknownFields: Map<String, String> = emptyMap(),
) {
    /** JSON tree: decimals → strings, dates → ISO 8601, lists → arrays (`to_json()`). */
    public fun toJson(): JsonObject = CanonicalJson.encode(this)
}
