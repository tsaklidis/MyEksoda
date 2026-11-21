package io.eksoda.engine.providers.impact

import io.eksoda.engine.FormatChanged
import io.eksoda.engine.InconsistentSource
import io.eksoda.engine.InvalidUrl
import io.eksoda.engine.ReceiptNotFound
import io.eksoda.engine.canonical.Address
import io.eksoda.engine.canonical.Branch
import io.eksoda.engine.canonical.CanonicalReceipt
import io.eksoda.engine.canonical.Document
import io.eksoda.engine.canonical.Identity
import io.eksoda.engine.canonical.Line
import io.eksoda.engine.canonical.Party
import io.eksoda.engine.canonical.Payment
import io.eksoda.engine.canonical.PriceBasis
import io.eksoda.engine.canonical.Totals
import io.eksoda.engine.canonical.VatEntry
import io.eksoda.engine.mydata.documentKind
import io.eksoda.engine.mydata.isAbsent
import io.eksoda.engine.mydata.isKnownVatCategory
import io.eksoda.engine.mydata.measurementUnit
import io.eksoda.engine.mydata.normalizeCode
import io.eksoda.engine.mydata.vatRate
import io.eksoda.engine.providers.Layout
import io.eksoda.engine.providers.UrlIdentity
import io.eksoda.engine.providers.amount
import io.eksoda.engine.providers.parseWithLayouts
import io.eksoda.engine.providers.required
import io.eksoda.engine.text.Culture
import io.eksoda.engine.text.DateFormatError
import io.eksoda.engine.text.DateOrder
import io.eksoda.engine.text.collapseWhitespace
import io.eksoda.engine.text.parseDate
import io.eksoda.engine.text.parseLocalDatetime
import io.eksoda.engine.text.pyIsAlpha
import io.eksoda.engine.text.pyIsDigit
import io.eksoda.engine.text.pyStrip
import io.eksoda.engine.url.parseSubmittedUrl
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.math.BigDecimal
import java.time.ZoneId
import java.util.Locale
import io.eksoda.engine.mydata.paymentMethod as mydataPaymentMethod
import io.eksoda.engine.vocabulary.paymentMethod as labelPaymentMethod
import org.jsoup.nodes.Document as HtmlDocument

// Impact receipt page → CanonicalReceipt.
//
// Both views of the page are read and cross-checked. Identifiers, codes and amounts come from
// the tax (myDATA) view; unit price, unit label, VAT %, names, addresses and the time of day come
// from the commercial view. Any disagreement fails the import (InconsistentSource); a missing
// required field fails it as FormatChanged.
//
// Other hosts serve the same pages under their own name (s1ecos); each is a [Site] of its own,
// with its provider code, parser version and the rule for the link the page prints.

public const val PARSER_VERSION: String = "impact/1"
private val SOURCE_TZ: ZoneId = ZoneId.of("Europe/Athens")

/**
 * Date order per (culture, view). With the "en" culture the tax view switches to month/day while
 * the commercial view stays day/month (observed 2026-11-14).
 */
private val DATE_ORDER = mapOf(
    (Culture.EL to "commercial") to DateOrder.DMY,
    (Culture.EL to "tax") to DateOrder.DMY,
    (Culture.EN to "commercial") to DateOrder.DMY,
    (Culture.EN to "tax") to DateOrder.MDY,
)

/** A host serving these receipt pages. */
internal class Site(
    val providerCode: String,
    val parserVersion: String,
    /** Whether `printed`, the link in the page's tax view, is the receipt of the submitted link. */
    val printsLink: (printed: String, submitted: UrlIdentity) -> Boolean,
)

private val IMPACT_SITE = Site(PROVIDER_CODE, PARSER_VERSION) { printed, submitted ->
    printedCanonicalUrl(printed) == submitted.canonicalUrl
}

internal enum class PageKind { RECEIPT, NOT_FOUND, UNKNOWN }

internal fun classifyPage(document: HtmlDocument): PageKind = when {
    document.selectFirst("div.invoice") != null &&
        document.selectFirst("div.InvoicesDocDetailed") != null -> PageKind.RECEIPT

    document.text().let { text -> Labels.NOT_FOUND_MARKERS.any { it in text } } ->
        PageKind.NOT_FOUND

    else -> PageKind.UNKNOWN
}

/** The page as observed 2026-11-13: commercial view + myDATA tax view. */
internal class ImpactLayoutV1(private val site: Site) : Layout {
    override val name: String = site.parserVersion

    override fun matches(document: HtmlDocument): Boolean =
        classifyPage(document) == PageKind.RECEIPT

    override fun parse(document: HtmlDocument, identity: UrlIdentity): CanonicalReceipt {
        val invoice = checkNotNull(document.selectFirst("div.invoice"))
        // s1ecos pages have only `lang` (observed 2026-11-21).
        val code = invoice.attr("data-culture").ifEmpty { invoice.attr("lang") }
        val culture = Culture.fromCode(code) ?: throw FormatChanged("unknown page culture '$code'")
        val commercial = extractCommercial(invoice)
        val tax = extractTaxView(checkNotNull(document.selectFirst("div.InvoicesDocDetailed")))
        return Builder(site, commercial, tax, culture, identity).build()
    }
}

/** Newest first. A changed page format gets a new layout; old ones stay for old fixtures. */
public val LAYOUTS: List<Layout> = listOf(ImpactLayoutV1(IMPACT_SITE))

public fun parseHtml(html: String, identity: UrlIdentity): CanonicalReceipt =
    parsePage(html, identity, LAYOUTS)

/** Parses a page of any [Site] with that site's [layouts]. */
internal fun parsePage(
    html: String,
    identity: UrlIdentity,
    layouts: List<Layout>,
): CanonicalReceipt {
    val document = io.eksoda.engine.providers.parseHtml(html)
    // Only the not-found page is decided here; recognizing receipt structure is each layout's job
    // (matches()), so a new layout can handle a changed page.
    if (classifyPage(document) ==
        PageKind.NOT_FOUND
    ) {
        throw ReceiptNotFound("the provider shows its document-not-found page")
    }
    return parseWithLayouts(document, identity, layouts)
}

private class Builder(
    private val site: Site,
    private val c: CommercialView,
    private val t: TaxView,
    private val culture: Culture,
    private val url: UrlIdentity,
) {
    // --- helpers --------------------------------------------------------------------------------
    fun num(text: String?, where: String): BigDecimal? = amount(text, culture, where)

    /** A value that is only a currency marker is a missing field. */
    fun requiredNum(mapping: Map<String, String>, key: String, where: String): BigDecimal =
        num(required(mapping, key, where), where)
            ?: throw FormatChanged("missing required field $where")

    /**
     * A discount in euros, or null: zero is no discount. A value that is not a positive amount
     * in euros (a percentage, text, a negative number) is a layout not seen yet: it is reported
     * under [key] in the unknown fields (format drift) and not read, so the import still works.
     */
    fun discount(text: String?, key: String): BigDecimal? {
        if (text.isNullOrBlank()) return null
        val value = if ('%' in text) null else amountOrNull(text, key)
        if (value == null || value.signum() < 0) {
            c.unknown[key] = text
            return null
        }
        return value.takeIf { it.signum() > 0 }
    }

    /** [num], with a value that is not a number as null. */
    private fun amountOrNull(text: String, where: String): BigDecimal? = try {
        num(text, where)
    } catch (_: FormatChanged) {
        null
    }

    /** Both views must agree whenever both provide a value. */
    fun agree(a: String?, b: String?, what: String) {
        if (a != null && b != null &&
            a != b
        ) {
            throw InconsistentSource("$what differs between the commercial and tax views")
        }
    }

    fun agree(a: BigDecimal?, b: BigDecimal?, what: String) {
        if (a != null && b != null && a.compareTo(b) != 0) {
            throw InconsistentSource("$what differs between the commercial and tax views")
        }
    }

    // --- build ----------------------------------------------------------------------------------
    fun build(): CanonicalReceipt {
        val identity = identity()
        val document = document()
        val lines = lines()
        return CanonicalReceipt(
            providerCode = site.providerCode,
            parserVersion = site.parserVersion,
            culture = culture.code,
            sourceUrl = url.canonicalUrl,
            issuer = issuer(),
            branch = branch(),
            document = document,
            identity = identity,
            totals = totals(),
            lines = lines,
            vatBreakdown = vatBreakdown(lines),
            payments = payments(),
            providerPayload = payload(),
            unknownFields = c.unknown + t.unknown,
        )
    }

    fun identity(): Identity {
        val uid = required(t.identifiers, "uid", "tax.uid").uppercase(Locale.ROOT)
        agree(c.certification["Uid"].orEmpty().uppercase(Locale.ROOT).ifEmpty { null }, uid, "UID")
        val pageUrl = t.identifiers["url"]?.ifEmpty { null }
        // Canonicalized the way the submitted link was (host case, port 443, a trailing slash,
        // hex case); a printed link that does not resolve is another receipt's.
        if (pageUrl != null && !site.printsLink(pageUrl, url)) {
            throw InconsistentSource("the receipt URL printed on the page differs")
        }
        // The link's 40-hex id is usually the myDATA UID (METRO), but some issuers use an id of
        // their own (observed 2026-11-20, VAT 801810254). Then the page must print this link.
        if (uid != url.uid && pageUrl == null) {
            throw InconsistentSource("the page UID differs from the URL")
        }

        val taxVat = required(t.issuer, "vat", "tax.issuer.vat")
        var commercialVat = c.issuer["Vat"].orEmpty().uppercase(Locale.ROOT)
        // The commercial view may print the VAT with its country prefix ("EL094062259").
        if (commercialVat.take(2).pyIsAlpha()) commercialVat = commercialVat.drop(2)
        agree(commercialVat.ifEmpty { null }, taxVat, "issuer VAT")
        if (taxVat !=
            url.issuerVat
        ) {
            throw InconsistentSource("the page issuer VAT differs from the URL")
        }
        val country = t.issuer["country"]
        if (!country.isNullOrEmpty() && country.uppercase(Locale.ROOT) != url.issuerCountry) {
            throw InconsistentSource("the page issuer country differs from the URL")
        }

        val failure =
            (t.identifiers["transmission_failure"]?.ifEmpty { null } ?: "0").pyStrip() == "1"
        val mark = if (isAbsent(t.identifiers["mark"])) null else t.identifiers["mark"]
        agree(c.certification["Mark"]?.ifEmpty { null }, mark, "MARK")
        if (mark == null &&
            !failure
        ) {
            throw FormatChanged("missing MARK without a transmission-failure flag")
        }
        val cancellation = t.identifiers["cancellation_mark"]
        return Identity(
            uid = uid,
            mark = mark,
            cancellationMark = if (isAbsent(cancellation)) null else cancellation,
            transmissionFailure = failure,
        )
    }

    fun document(): Document {
        val taxDateText = required(t.header, "issue_date", "tax.issue_date")
        val commercialText = required(c.identity, "DateIssued", "commercial.DateIssued")
        val (issuedDate, issuedAt) = try {
            parseDate(taxDateText, DATE_ORDER.getValue(culture to "tax")) to
                parseLocalDatetime(
                    commercialText,
                    culture,
                    DATE_ORDER.getValue(culture to "commercial"),
                    SOURCE_TZ,
                )
        } catch (exception: DateFormatError) {
            throw FormatChanged(exception.message.orEmpty()).apply { initCause(exception) }
        }
        if (issuedAt.toLocalDate() !=
            issuedDate
        ) {
            throw InconsistentSource("issue date differs between the commercial and tax views")
        }

        val currency = t.header["currency"]?.ifEmpty { null } ?: c.summary["CurrencyCode"]
        agree(c.summary["CurrencyCode"], t.header["currency"], "currency")
        if (currency.isNullOrEmpty()) throw FormatChanged("missing required field currency")
        val typeCode = required(t.header, "type_code", "tax.type_code")
        return Document(
            kind = documentKind(typeCode),
            typeCode = typeCode,
            typeLabel = c.identity["InvoiceType"]?.ifEmpty { null },
            series = t.header["series"]?.ifEmpty { null },
            number = t.header["number"]?.ifEmpty { null },
            formattedNumber = c.identity["IssuerFormatedInvoiceSeriesNumber"]?.ifEmpty { null },
            issuedAt = issuedAt,
            issuedDate = issuedDate,
            currency = currency.uppercase(Locale.ROOT),
        )
    }

    fun issuer(): Party = Party(
        countryCode = checkNotNull(url.issuerCountry),
        vatNumber = checkNotNull(url.issuerVat),
        legalName = required(c.issuer, "RegisteredName", "commercial.RegisteredName"),
        activity = c.issuer["Activities"]?.ifEmpty { null },
        taxOffice = c.issuer["TaxOffice"]?.ifEmpty { null },
        gemiNumber = c.issuer["GeneralCommercialRegistryNumber"]?.ifEmpty { null },
        address = address(c.headOffice),
    )

    fun branch(): Branch? {
        val number = t.issuer["branch"]?.ifEmpty { null }
        val address = address(c.branch)
        return if (number == null &&
            address == null
        ) {
            null
        } else {
            Branch(number = number, address = address)
        }
    }

    fun lines(): List<Line> {
        if (c.lines.size !=
            t.lines.size
        ) {
            throw InconsistentSource(
                "${c.lines.size} commercial lines but ${t.lines.size} tax lines",
            )
        }
        if (t.lines.isEmpty()) throw FormatChanged("the receipt has no lines")
        return c.lines.zip(t.lines).mapIndexed { index, (commercial, tax) ->
            line(index + 1, commercial, tax)
        }
    }

    fun line(index: Int, c: Map<String, String>, t: Map<String, String>): Line {
        val where = "line $index"
        val net = requiredNum(t, "net", "$where net")
        val vat = requiredNum(t, "vat", "$where VAT")
        val gross = requiredNum(c, "Total", "$where total")
        agree(num(c["NetTotal"] ?: c[Labels.LINE_NET_TOTAL_ELISE], where), net, "$where net")
        agree(num(c["VATTotal"], where), vat, "$where VAT")

        val taxQuantity = num(t["quantity"], where)
        agree(num(c["Quantity"], where), taxQuantity, "$where quantity")
        val quantity = taxQuantity ?: num(c["Quantity"], where)

        val code = t["code"]?.ifEmpty { null }
        agree(c["Code"]?.ifEmpty { null }, code, "$where item code")
        val description = collapseWhitespace(t["description"].orEmpty())
        val commercialDescription = collapseWhitespace(c["Description1"].orEmpty())
        agree(
            commercialDescription.ifEmpty {
                null
            },
            description.ifEmpty { null },
            "$where description",
        )

        val category = normalizeCode(t["vat_category"])
        var rate = num(c["VatCategory"], where)
        if (isKnownVatCategory(category)) {
            agree(rate, vatRate(category), "$where VAT rate")
            rate = vatRate(category)
        }

        val unitPrice = num(c["UnitPrice"], where)
        // The line discount in euros, VAT included (column "Έκπτ. 1", observed 2026-11-21; fixture
        // mymarket_7_lines_discount line 7: 1.918 × 7.69 = 14.75, 7.38 off, 7.37 paid). The page
        // prints no value before the discount: it is the total plus the discount, and validation
        // checks it against quantity × unit price (R5) and the discount total (R17); R13 holds
        // by construction.
        val discount = discount(c[Labels.LINE_DISCOUNT], "commercial.line.${Labels.LINE_DISCOUNT}")
        val lineNumber = t["line_number"]?.ifEmpty { null } ?: index.toString()
        if (!lineNumber.pyIsDigit()) throw FormatChanged("$where: line number '$lineNumber'")
        val raw = LinkedHashMap<String, String>()
        c.forEach { (key, value) -> raw["commercial.$key"] = value }
        t.forEach { (key, value) -> raw["tax.$key"] = value }
        return Line(
            lineNumber = lineNumber.toInt(),
            description = description.ifEmpty { commercialDescription },
            gross = gross,
            quantity = quantity,
            unit = measurementUnit(t["unit_code"]),
            unitCode = normalizeCode(t["unit_code"]),
            unitLabel = c["MeasurementUnit"]?.ifEmpty { null },
            unitPrice = unitPrice,
            // Impact prints VAT-inclusive unit prices (0.622 kg × 7.21 = 4.48 gross).
            unitPriceBasis = if (unitPrice != null) PriceBasis.GROSS else PriceBasis.UNKNOWN,
            grossBeforeDiscount = discount?.let { gross + it },
            discount = discount,
            net = net,
            vatCategoryCode = category,
            vatRate = rate,
            vat = vat,
            withheld = num(t["withheld"], where),
            fees = num(t["fees"], where),
            otherTaxes = num(t["other_taxes"], where),
            stampDuty = num(t["stamp_duty"], where),
            digitalFee = num(t["digital_fee"], where),
            deductions = num(t["deductions"], where),
            merchantProductCode = code ?: c["Code"]?.ifEmpty { null },
            lineTypeCode = normalizeCode(t["line_type_code"]),
            annotationCode = normalizeCode(t["detail_type"]),
            vatExemptionCode = normalizeCode(t["vat_exemption"]),
            raw = raw,
        )
    }

    fun totals(): Totals {
        val s = t.summary
        val gross = requiredNum(s, "gross", "tax.summary.gross")
        val net = num(s["net"], "summary")
        val vat = num(s["vat"], "summary")
        agree(num(c.summary["TotalGrossValue"], "summary"), gross, "gross total")
        agree(num(c.summary["TotalNetAmount"], "summary"), net, "net total")
        agree(num(c.summary["TotalVATAmount"], "summary"), vat, "VAT total")
        return Totals(
            gross = gross,
            net = net,
            vat = vat,
            withheld = num(s["withheld"], "summary"),
            fees = num(s["fees"], "summary"),
            otherTaxes = num(s["other_taxes"], "summary"),
            stampDuty = num(s["stamp_duty"], "summary"),
            digitalFee = num(s["digital_fee"], "summary"),
            deductions = num(s["deductions"], "summary"),
            // "Σύνολο Εκπτώσεων" (commercial view only).
            discount = discount(c.summary["TotalAllowances"], "commercial.summary.TotalAllowances"),
        )
    }

    fun vatBreakdown(lines: List<Line>): List<VatEntry> {
        // Rates are keyed by value ("24" and "24.00" are one key).
        val codeByRate = lines.associate { it.vatRate?.stripTrailingZeros() to it.vatCategoryCode }
        return c.vatBreakdown.map { row ->
            val rate = num(row["Percentage"], "VAT breakdown")
            VatEntry(
                rate = rate,
                categoryCode = codeByRate[rate?.stripTrailingZeros()],
                net = requiredNum(row, "UnderlyingValue", "VAT breakdown net"),
                vat = requiredNum(row, "VatAmount", "VAT breakdown amount"),
            )
        }
    }

    fun payments(): List<Payment> {
        // Tax-view payments are authoritative. Commercial rows are paired with them by position,
        // and only when both views list the same number of payments.
        if (t.payments.isNotEmpty()) {
            val sameCount = c.payments.size == t.payments.size
            val commercialRows: List<Map<String, String>> = if (sameCount) {
                c.payments
            } else {
                List(
                    t.payments.size,
                ) {
                    emptyMap()
                }
            }
            return t.payments.zip(commercialRows).map { (row, commercial) ->
                val paid = requiredNum(row, "amount", "payment amount")
                agree(num(commercial["Amount"], "payment"), paid, "payment amount")
                agree(
                    num(commercial["TipAmount"], "payment tip"),
                    num(row["tip"], "payment tip"),
                    "payment tip",
                )
                // A missing or zero tip ("0,00") becomes "0".
                val tip =
                    num(row["tip"], "payment tip")?.takeIf { it.signum() != 0 } ?: BigDecimal("0")
                Payment(
                    amount = paid,
                    method = mydataPaymentMethod(row["method_code"]),
                    methodCode = normalizeCode(row["method_code"]),
                    methodLabel = commercial["PaymentMethodType"]?.ifEmpty { null },
                    tip = tip,
                )
            }
        }
        return c.payments.map { row ->
            Payment(
                amount = requiredNum(row, "Amount", "payment amount"),
                method = labelPaymentMethod(row["PaymentMethodType"]),
                methodLabel = row["PaymentMethodType"]?.ifEmpty { null },
                tip = num(row["TipAmount"], "payment tip")?.takeIf { it.signum() != 0 }
                    ?: BigDecimal("0"),
            )
        }
    }

    /**
     * Allowlisted, personal-data-free extraction kept with the receipt. Only known fields
     * are kept: an unknown field is reported by name in
     * `unknownFields`, never stored with its value. The free-text miscellaneous section is not kept.
     */
    fun payload(): JsonObject {
        fun only(section: String, values: Map<String, String>) =
            json(values.filterKeys { it in Labels.COMMERCIAL_KNOWN.getValue(section) })
        return buildJsonObject {
            put(
                "commercial",
                buildJsonObject {
                    put("issuer", only("header", c.issuer))
                    put("head_office", only("header", c.headOffice))
                    put("branch", only("header", c.branch))
                    put("identity", only("identity", c.identity))
                    put("summary", only("summary", c.summary))
                    put("certification", only("certification", c.certification))
                },
            )
            put(
                "tax",
                buildJsonObject {
                    put("header", json(t.header))
                    put("issuer", json(t.issuer))
                    put("summary", json(t.summary))
                    put("identifiers", json(t.identifiers.filterKeys { it != "url" }))
                },
            )
        }
    }
}

/** The canonical form of a link printed on the page, or null if it is not an Impact link. */
private fun printedCanonicalUrl(printed: String): String? = try {
    recognize(parseSubmittedUrl(printed))?.canonicalUrl
} catch (_: InvalidUrl) {
    null
} catch (_: IllegalArgumentException) {
    // urlsplit's malformed bracketed host.
    null
}

private fun json(values: Map<String, String>): JsonObject = JsonObject(
    values.mapValues { (_, value) ->
        JsonPrimitive(value)
    },
)

private fun address(fields: Map<String, String>): Address? {
    if (fields.values.none { it.isNotEmpty() }) return null
    return Address(
        street = fields["Street"]?.ifEmpty { null },
        number = fields["Number"]?.ifEmpty { null },
        city = fields["City"]?.ifEmpty { null },
        postalCode = fields["Postal"]?.ifEmpty { null },
        country = fields["Country"]?.ifEmpty { null },
    )
}
