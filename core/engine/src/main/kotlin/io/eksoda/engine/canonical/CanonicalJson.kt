// Port of to_json / to_jsonable in backend/apps/receipts/canonical.py.
package io.eksoda.engine.canonical

import io.eksoda.engine.text.pyIsoFormat
import io.eksoda.engine.text.pyStr
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import java.math.BigDecimal

/**
 * The canonical receipt as the JSON tree Python's `to_json()` produces: dataclass fields under
 * their snake_case names, `Decimal` as `str(value)`, dates and times as `isoformat()`, enums as
 * their values. Golden files (`testdata/server/fixtures/<provider>/expected`) are this tree,
 * written with sorted keys; compare trees, not text (§9.3).
 */
public object CanonicalJson {
    public fun encode(receipt: CanonicalReceipt): JsonObject = buildJsonObject {
        put("provider_code", receipt.providerCode.json)
        put("parser_version", receipt.parserVersion.json)
        put("culture", receipt.culture.json)
        put("source_url", receipt.sourceUrl.json)
        put("issuer", encode(receipt.issuer))
        put("document", encode(receipt.document))
        put("identity", encode(receipt.identity))
        put("totals", encode(receipt.totals))
        put("lines", buildJsonArray { receipt.lines.forEach { add(encode(it)) } })
        put("coverage", encode(receipt.coverage))
        put("branch", receipt.branch?.let(::encode) ?: JsonNull)
        put("vat_breakdown", buildJsonArray { receipt.vatBreakdown.forEach { add(encode(it)) } })
        put("payments", buildJsonArray { receipt.payments.forEach { add(encode(it)) } })
        put("notices", buildJsonArray { receipt.notices.forEach { add(encode(it)) } })
        put("provider_payload", receipt.providerPayload)
        put("unknown_fields", receipt.unknownFields.json)
    }

    private fun encode(coverage: Coverage): JsonObject = buildJsonObject {
        put("line_items", coverage.lineItems.json)
        put("line_tax_amounts", coverage.lineTaxAmounts.json)
        put("official_codes", coverage.officialCodes.json)
        put("mydata_registered", coverage.mydataRegistered.json)
        put("issue_time", coverage.issueTime.code.json)
        put("cross_checked", coverage.crossChecked.json)
    }

    private fun encode(notice: Notice): JsonObject = buildJsonObject {
        put("code", notice.code.json)
        put("message", notice.message.json)
    }

    private fun encode(address: Address): JsonObject = buildJsonObject {
        put("street", address.street.json)
        put("number", address.number.json)
        put("city", address.city.json)
        put("postal_code", address.postalCode.json)
        put("country", address.country.json)
    }

    private fun encode(party: Party): JsonObject = buildJsonObject {
        put("country_code", party.countryCode.json)
        put("vat_number", party.vatNumber.json)
        put("legal_name", party.legalName.json)
        put("activity", party.activity.json)
        put("tax_office", party.taxOffice.json)
        put("gemi_number", party.gemiNumber.json)
        put("address", party.address?.let(::encode) ?: JsonNull)
    }

    private fun encode(branch: Branch): JsonObject = buildJsonObject {
        put("number", branch.number.json)
        put("address", branch.address?.let(::encode) ?: JsonNull)
    }

    private fun encode(document: Document): JsonObject = buildJsonObject {
        put("kind", document.kind.code.json)
        put("issued_at", document.issuedAt.pyIsoFormat().json)
        put("issued_date", document.issuedDate.pyIsoFormat().json)
        put("currency", document.currency.json)
        put("type_code", document.typeCode.json)
        put("type_label", document.typeLabel.json)
        put("series", document.series.json)
        put("number", document.number.json)
        put("formatted_number", document.formattedNumber.json)
    }

    private fun encode(identity: Identity): JsonObject = buildJsonObject {
        put("uid", identity.uid.json)
        put("mark", identity.mark.json)
        put("cancellation_mark", identity.cancellationMark.json)
        put("transmission_failure", identity.transmissionFailure.json)
        put("source_key", identity.sourceKey.json)
    }

    private fun encode(totals: Totals): JsonObject = buildJsonObject {
        put("gross", totals.gross.json)
        put("net", totals.net.json)
        put("vat", totals.vat.json)
        put("withheld", totals.withheld.json)
        put("fees", totals.fees.json)
        put("other_taxes", totals.otherTaxes.json)
        put("stamp_duty", totals.stampDuty.json)
        put("digital_fee", totals.digitalFee.json)
        put("deductions", totals.deductions.json)
        put("gross_before_discount", totals.grossBeforeDiscount.json)
        put("discount", totals.discount.json)
    }

    private fun encode(entry: VatEntry): JsonObject = buildJsonObject {
        put("net", entry.net.json)
        put("vat", entry.vat.json)
        put("rate", entry.rate.json)
        put("category_code", entry.categoryCode.json)
    }

    private fun encode(payment: Payment): JsonObject = buildJsonObject {
        put("amount", payment.amount.json)
        put("method", payment.method?.code.json)
        put("method_code", payment.methodCode.json)
        put("method_label", payment.methodLabel.json)
        put("tip", payment.tip.json)
        put("derived", payment.derived.json)
    }

    private fun encode(line: Line): JsonObject = buildJsonObject {
        put("line_number", JsonPrimitive(line.lineNumber))
        put("description", line.description.json)
        put("gross", line.gross.json)
        put("quantity", line.quantity.json)
        put("unit", line.unit?.code.json)
        put("unit_code", line.unitCode.json)
        put("unit_label", line.unitLabel.json)
        put("unit_price", line.unitPrice.json)
        put("unit_price_basis", line.unitPriceBasis.code.json)
        put("gross_before_discount", line.grossBeforeDiscount.json)
        put("discount", line.discount.json)
        put("net", line.net.json)
        put("vat_category_code", line.vatCategoryCode.json)
        put("vat_rate", line.vatRate.json)
        put("vat", line.vat.json)
        put("withheld", line.withheld.json)
        put("fees", line.fees.json)
        put("other_taxes", line.otherTaxes.json)
        put("stamp_duty", line.stampDuty.json)
        put("digital_fee", line.digitalFee.json)
        put("deductions", line.deductions.json)
        put("merchant_product_code", line.merchantProductCode.json)
        put("barcode", line.barcode.json)
        put("line_type_code", line.lineTypeCode.json)
        put("annotation_code", line.annotationCode.json)
        put("vat_exemption_code", line.vatExemptionCode.json)
        put("raw", line.raw.json)
    }

    private val String?.json: JsonElement get() = if (this ==
        null
    ) {
        JsonNull
    } else {
        JsonPrimitive(this)
    }

    private val Boolean.json: JsonElement get() = JsonPrimitive(this)

    private val BigDecimal?.json: JsonElement get() = this?.pyStr().json

    private val Map<String, String>.json: JsonObject get() = JsonObject(
        mapValues { (_, value) ->
            JsonPrimitive(value)
        },
    )
}
