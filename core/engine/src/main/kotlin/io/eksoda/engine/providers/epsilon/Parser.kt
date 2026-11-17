package io.eksoda.engine.providers.epsilon

import io.eksoda.engine.FormatChanged
import io.eksoda.engine.InconsistentSource
import io.eksoda.engine.InvalidUrl
import io.eksoda.engine.ReceiptNotFound
import io.eksoda.engine.canonical.Address
import io.eksoda.engine.canonical.Branch
import io.eksoda.engine.canonical.CanonicalReceipt
import io.eksoda.engine.canonical.Coverage
import io.eksoda.engine.canonical.Document
import io.eksoda.engine.canonical.Identity
import io.eksoda.engine.canonical.IssueTime
import io.eksoda.engine.canonical.Line
import io.eksoda.engine.canonical.Party
import io.eksoda.engine.canonical.Payment
import io.eksoda.engine.canonical.Totals
import io.eksoda.engine.mydata.documentKind
import io.eksoda.engine.mydata.isAbsent
import io.eksoda.engine.mydata.isKnownVatCategory
import io.eksoda.engine.mydata.measurementUnit
import io.eksoda.engine.mydata.normalizeCode
import io.eksoda.engine.mydata.paymentMethod
import io.eksoda.engine.mydata.vatRate
import io.eksoda.engine.providers.Layout
import io.eksoda.engine.providers.UrlIdentity
import io.eksoda.engine.providers.parseWithLayouts
import io.eksoda.engine.providers.textOf
import io.eksoda.engine.text.pyIsDigit
import io.eksoda.engine.text.pyLocalToOffset
import io.eksoda.engine.url.parseSubmittedUrl
import io.eksoda.engine.vocabulary.currency
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeParseException
import java.util.Locale
import org.jsoup.nodes.Document as XmlDocument

// Epsilon Digital detailed myDATA XML → CanonicalReceipt.
//
// The XML is the receipt the merchant transmitted to AADE (myDATA InvoicesDoc v1) with the
// item descriptions added. It has official codes and per-line net and VAT amounts, but no line
// totals, unit prices, merchant name or time of day. So a line's gross is its net plus VAT and
// taxes, the merchant name comes from the host's tenant entry (Urls.kt), and the issue time is
// the date only. Σ line net and Σ line VAT must match the summary (fatal: a partial document).
//
// Never read: the authentication code, payment transaction and terminal ids, provider
// signatures, ECR tokens, free-text comments and the counterpart (the buyer).

public const val PARSER_VERSION: String = "epsilon/1"
private val SOURCE_TZ: ZoneId = ZoneId.of("Europe/Athens")
private val TOLERANCE = BigDecimal("0.02")
private val ZERO = BigDecimal("0")

/** The body of the XML endpoint for an unknown document (HTTP 404, text/plain). */
private const val NOT_FOUND_MARKER = "Document not found"
private const val ROOT = "InvoicesDoc"

/** myDATA's currency when the header omits it. */
private const val DEFAULT_CURRENCY = "EUR"

/** Plain XML decimals: no thousands separators, a point for decimals. */
private val XML_DECIMAL = Regex("""^-?\d+(?:\.\d+)?$""")

private val INVOICE_KNOWN = setOf(
    "uid", "mark", "cancelledByMark", "authenticationCode", "transmissionFailure", "issuer",
    "counterpart", "invoiceHeader", "paymentMethods", "invoiceDetails", "taxesTotals",
    "invoiceSummary", "qrCodeUrl", "downloadingInvoiceUrl", "otherTransportDetails",
)
private val LINE_KNOWN = setOf(
    "lineNumber", "recType", "itemDescr", "quantity", "measurementUnit", "netValue",
    "vatCategory", "vatAmount", "vatExemptionCategory", "withheldAmount",
    "withheldPercentCategory", "feesAmount", "feesPercentCategory", "otherTaxesAmount",
    "otherTaxesPercentCategory", "stampDutyAmount", "stampDutyPercentCategory",
    "deductionsAmount", "lineComments", "incomeClassification", "expensesClassification",
    "invoiceDetailType", "discountOption", "TaricNo", "itemCode", "quantity15",
    "otherMeasurementUnitQuantity", "otherMeasurementUnitTitle", "notVAT195", "dienergia",
    "fuelCode",
)

/** Free text that may hold anything the cashier typed: not kept with the line. */
private val LINE_DROP = setOf("lineComments")

/** Header fields kept with the receipt (delivery details may hold addresses). */
private val HEADER_KEPT = setOf(
    "series", "aa", "issueDate", "invoiceType", "currency", "exchangeRate", "dispatchDate",
    "dispatchTime", "selfPricing", "vatPaymentSuspension", "isDeliveryNote",
)

/** Parses XML without fetching or expanding external entities. */
internal fun parseXml(xml: String): XmlDocument = Jsoup.parse(xml, "", Parser.xmlParser())

// Jsoup's XML parser keeps the case of tag names; these compare them exactly.
internal fun Element.child(name: String): Element? = children().firstOrNull { it.tagName() == name }

internal fun Element.childrenNamed(name: String): List<Element> =
    children().filter { it.tagName() == name }

private fun Element.value(name: String): String? = child(name)?.let(::textOf)?.ifEmpty { null }

/** Leaf children as tag → text (the first of a repeated tag wins). */
private fun Element.leaves(): Map<String, String> {
    val values = LinkedHashMap<String, String>()
    for (element in children()) {
        if (element.childrenSize() == 0) values.putIfAbsent(element.tagName(), textOf(element))
    }
    return values
}

private fun root(document: XmlDocument): Element? = document.children().firstOrNull {
    it.tagName() == ROOT
}

/** The detailed XML as observed 2026-11-17 (Sklavenitis retail receipts). */
internal object EpsilonLayoutV1 : Layout {
    override val name: String = PARSER_VERSION

    override fun matches(document: XmlDocument): Boolean = root(document)?.child("invoice") != null

    override fun parse(document: XmlDocument, identity: UrlIdentity): CanonicalReceipt {
        val invoices = checkNotNull(root(document)).childrenNamed("invoice")
        if (invoices.size != 1) throw FormatChanged("${invoices.size} invoices in one document")
        return Builder(invoices.single(), identity).build()
    }
}

public val LAYOUTS: List<Layout> = listOf(EpsilonLayoutV1)

public fun parseDocument(text: String, identity: UrlIdentity): CanonicalReceipt {
    if (NOT_FOUND_MARKER in text && ROOT !in text) {
        throw ReceiptNotFound("the provider has no such document")
    }
    return parseWithLayouts(parseXml(text), identity, LAYOUTS)
}

private fun decimal(text: String?, where: String): BigDecimal? {
    if (text == null) return null
    if (!XML_DECIMAL.matches(text)) throw FormatChanged("$where: '$text' is not a number")
    return BigDecimal(text)
}

private fun required(element: Element?, name: String, where: String): String =
    element?.value(name) ?: throw FormatChanged("missing required field $where")

private fun requiredDecimal(element: Element, name: String, where: String): BigDecimal =
    checkNotNull(decimal(required(element, name, where), where))

private fun z(value: BigDecimal?): BigDecimal = value ?: ZERO

private class Builder(private val invoice: Element, private val url: UrlIdentity) {
    // The canonical URL was produced by recognize(), so it always splits.
    private val link = checkNotNull(split(parseSubmittedUrl(url.canonicalUrl)))
    private val unknown = LinkedHashMap<String, String>()

    fun build(): CanonicalReceipt {
        checkLink()
        invoice.children().map { it.tagName() }.filter { it !in INVOICE_KNOWN }
            .forEach { unknown["invoice.$it"] = "" }
        val header = invoice.child("invoiceHeader")
            ?: throw FormatChanged("missing required field invoiceHeader")
        val summary = invoice.child("invoiceSummary")
            ?: throw FormatChanged("missing required field invoiceSummary")
        val issuer = invoice.child("issuer")
        val lines = invoice.childrenNamed("invoiceDetails").mapIndexed { offset, row ->
            line(offset + 1, row)
        }
        if (lines.isEmpty()) throw FormatChanged("the receipt has no lines")
        val totals = totals(summary)
        checkConsistency(lines, totals)
        return CanonicalReceipt(
            providerCode = PROVIDER_CODE,
            parserVersion = PARSER_VERSION,
            culture = "el",
            sourceUrl = url.canonicalUrl,
            issuer = issuer(issuer),
            branch = issuer?.value("branch")?.let { Branch(number = it) },
            document = document(header),
            identity = identity(),
            totals = totals,
            lines = lines,
            payments = payments(),
            coverage = Coverage(
                lineItems = true,
                lineTaxAmounts = true,
                officialCodes = true,
                mydataRegistered = true,
                issueTime = IssueTime.DATE_ONLY,
                crossChecked = false,
            ),
            providerPayload = buildJsonObject {
                put("header", json(header.leaves().filterKeys { it in HEADER_KEPT }))
                put("issuer", json(issuer?.leaves().orEmpty()))
                put("summary", json(summary.leaves()))
            },
            unknownFields = unknown,
        )
    }

    /** The XML names the receipt link it belongs to; it must be the requested document. */
    fun checkLink() {
        val printed = invoice.value("downloadingInvoiceUrl") ?: return
        val printedLink = try {
            split(parseSubmittedUrl(printed))
        } catch (_: InvalidUrl) {
            null
        }
        if (printedLink == null || printedLink.host != link.host ||
            printedLink.documentId != link.documentId
        ) {
            throw InconsistentSource("the XML belongs to another receipt than the link")
        }
    }

    fun identity(): Identity {
        val uid = invoice.value("uid")?.uppercase(Locale.ROOT)
        val mark = invoice.value("mark")?.takeUnless(::isAbsent)
        if (uid == null && mark == null) throw FormatChanged("neither UID nor MARK in the XML")
        return Identity(
            uid = uid,
            mark = mark,
            cancellationMark = invoice.value("cancelledByMark")?.takeUnless(::isAbsent),
            transmissionFailure = !isAbsent(invoice.value("transmissionFailure")),
        )
    }

    fun issuer(issuer: Element?): Party {
        val vat = required(issuer, "vatNumber", "issuer VAT")
        // Greek issuers send no name to myDATA; the host's tenant entry names them.
        val tenant = tenant(link.host)?.takeIf { it.vatNumber == vat }
        val name = issuer?.value("name") ?: tenant?.legalName
            ?: throw FormatChanged("no merchant name known for issuer VAT $vat on ${link.host}")
        val address = issuer?.child("address")
        return Party(
            countryCode = issuer?.value("country")?.uppercase(Locale.ROOT) ?: "GR",
            vatNumber = vat,
            legalName = name,
            address = address?.let {
                Address(
                    street = it.value("street"),
                    number = it.value("number"),
                    postalCode = it.value("postalCode"),
                    city = it.value("city"),
                )
            },
        )
    }

    fun document(header: Element): Document {
        val issuedDate = try {
            LocalDate.parse(required(header, "issueDate", "issue date"))
        } catch (exception: DateTimeParseException) {
            throw FormatChanged("issue date: ${exception.message}").apply { initCause(exception) }
        }
        val typeCode = required(header, "invoiceType", "document type")
        val currencyText = header.value("currency") ?: DEFAULT_CURRENCY
        val currency = currency(currencyText)
            ?: throw FormatChanged("unknown currency '$currencyText'")
        val series = header.value("series")
        val number = header.value("aa")
        return Document(
            kind = documentKind(typeCode),
            typeCode = typeCode,
            series = series,
            number = number,
            formattedNumber = if (series != null && number != null) "$series-$number" else number,
            issuedAt = pyLocalToOffset(issuedDate.atTime(LocalTime.MIDNIGHT), SOURCE_TZ),
            issuedDate = issuedDate,
            currency = currency,
        )
    }

    fun line(index: Int, row: Element): Line {
        val where = "line $index"
        row.children().map { it.tagName() }.filter { it !in LINE_KNOWN }
            .forEach { unknown["line.$it"] = "" }
        val lineNumber = row.value("lineNumber") ?: index.toString()
        if (!lineNumber.pyIsDigit()) throw FormatChanged("$where: line number '$lineNumber'")
        val net = requiredDecimal(row, "netValue", "$where net")
        val vat = requiredDecimal(row, "vatAmount", "$where VAT")
        val withheld = decimal(row.value("withheldAmount"), where)
        val fees = decimal(row.value("feesAmount"), where)
        val otherTaxes = decimal(row.value("otherTaxesAmount"), where)
        val stampDuty = decimal(row.value("stampDutyAmount"), where)
        val deductions = decimal(row.value("deductionsAmount"), where)
        // The XML has no line total: myDATA's gross composition gives it.
        val gross = net + vat + z(fees) + z(otherTaxes) + z(stampDuty) - z(withheld) -
            z(deductions)
        val category = normalizeCode(row.value("vatCategory"))
        val unitCode = normalizeCode(row.value("measurementUnit"))
        return Line(
            lineNumber = lineNumber.toInt(),
            description = row.value("itemDescr").orEmpty(),
            gross = gross,
            quantity = decimal(row.value("quantity"), where),
            unit = measurementUnit(unitCode),
            unitCode = unitCode,
            net = net,
            vatCategoryCode = category,
            vatRate = if (isKnownVatCategory(category)) vatRate(category) else null,
            vat = vat,
            withheld = withheld,
            fees = fees,
            otherTaxes = otherTaxes,
            stampDuty = stampDuty,
            deductions = deductions,
            merchantProductCode = row.value("itemCode"),
            lineTypeCode = normalizeCode(row.value("recType")),
            annotationCode = normalizeCode(row.value("invoiceDetailType")),
            vatExemptionCode = normalizeCode(row.value("vatExemptionCategory")),
            raw = row.leaves().filterKeys { it !in LINE_DROP }
                .mapKeys { (key, _) -> "line.$key" },
        )
    }

    fun totals(summary: Element): Totals = Totals(
        gross = requiredDecimal(summary, "totalGrossValue", "gross total"),
        net = decimal(summary.value("totalNetValue"), "net total"),
        vat = decimal(summary.value("totalVatAmount"), "VAT total"),
        withheld = decimal(summary.value("totalWithheldAmount"), "withheld total"),
        fees = decimal(summary.value("totalFeesAmount"), "fees total"),
        otherTaxes = decimal(summary.value("totalOtherTaxesAmount"), "other taxes total"),
        stampDuty = decimal(summary.value("totalStampDutyAmount"), "stamp duty total"),
        deductions = decimal(summary.value("totalDeductionsAmount"), "deductions total"),
    )

    /** Only the tags read here: the transaction id, terminal id and signatures never are. */
    fun payments(): List<Payment> {
        val rows = invoice.child("paymentMethods")?.childrenNamed("paymentMethodDetails").orEmpty()
        return rows.map { row ->
            val code = normalizeCode(row.value("type"))
            Payment(
                amount = requiredDecimal(row, "amount", "payment amount"),
                method = paymentMethod(code),
                methodCode = code,
                tip = decimal(row.value("tipAmount"), "payment tip")?.takeIf { it.signum() != 0 }
                    ?: BigDecimal("0"),
            )
        }
    }

    fun checkConsistency(lines: List<Line>, totals: Totals) {
        fun off(sum: BigDecimal, total: BigDecimal?) =
            total != null && (sum - total).abs() > TOLERANCE

        // Gross is not compared here: invoice-level taxes (taxesTotals) are in the gross total
        // but on no line. Validation warns about it (R7).
        if (off(lines.fold(ZERO) { sum, line -> sum + z(line.net) }, totals.net)) {
            throw InconsistentSource("the line net values do not add up to the net total")
        }
        if (off(lines.fold(ZERO) { sum, line -> sum + z(line.vat) }, totals.vat)) {
            throw InconsistentSource("the line VAT amounts do not add up to the VAT total")
        }
    }
}

private fun json(values: Map<String, String>): JsonObject =
    JsonObject(values.mapValues { (_, value) -> JsonPrimitive(value) })
