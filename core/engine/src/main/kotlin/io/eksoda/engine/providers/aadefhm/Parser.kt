// Port of backend/apps/receipts/providers/aade_fhm/parser.py.
package io.eksoda.engine.providers.aadefhm

import io.eksoda.engine.FormatChanged
import io.eksoda.engine.InconsistentSource
import io.eksoda.engine.ReceiptNotFound
import io.eksoda.engine.canonical.Address
import io.eksoda.engine.canonical.Branch
import io.eksoda.engine.canonical.CanonicalReceipt
import io.eksoda.engine.canonical.Coverage
import io.eksoda.engine.canonical.Document
import io.eksoda.engine.canonical.Identity
import io.eksoda.engine.canonical.IssueTime
import io.eksoda.engine.canonical.Notice
import io.eksoda.engine.canonical.Party
import io.eksoda.engine.canonical.Payment
import io.eksoda.engine.canonical.Totals
import io.eksoda.engine.canonical.VatEntry
import io.eksoda.engine.mydata.PaymentMethod
import io.eksoda.engine.providers.Layout
import io.eksoda.engine.providers.UrlIdentity
import io.eksoda.engine.providers.amount
import io.eksoda.engine.providers.childCells
import io.eksoda.engine.providers.keyOf
import io.eksoda.engine.providers.labelMap
import io.eksoda.engine.providers.parseWithLayouts
import io.eksoda.engine.providers.required
import io.eksoda.engine.providers.textOf
import io.eksoda.engine.text.Culture
import io.eksoda.engine.text.DateFormatError
import io.eksoda.engine.text.DateOrder
import io.eksoda.engine.text.parseLocalDatetime
import io.eksoda.engine.text.pyIsDigit
import io.eksoda.engine.text.pyRegex
import io.eksoda.engine.url.parseSubmittedUrl
import io.eksoda.engine.vocabulary.currency
import io.eksoda.engine.vocabulary.documentType
import io.eksoda.engine.vocabulary.vatCategoryForRate
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.math.BigDecimal
import java.time.ZoneId
import org.jsoup.nodes.Document as HtmlDocument

// AADE cash-register receipt check page → summary-only CanonicalReceipt.
//
// The page shows AADE's record of a cash-register (ΦΗΜ) receipt: merchant, date and time,
// totals, net/VAT per cash-register VAT category and the card amount. There are no line items
// and no MARK (coverage says so). Alerts on the page (e.g. a QR signature that differs from
// AADE's record) become notices, i.e. visible warnings.

public const val PARSER_VERSION: String = "aade_fhm/1"

/** Amounts use a decimal point: "€ 24.87". */
private val CULTURE = Culture.EN
private val SOURCE_TZ: ZoneId = ZoneId.of("Europe/Athens")

/** Shown when AADE has no record (yet): e.g. fuel receipts are transmitted at day end. */
private const val NOT_TRANSMITTED = "δεν έχει διαβιβαστεί στην ΑΑΔΕ"
private val TOTAL_LABEL = keyOf("Συνολική αξία")

/**
 * Cash-register VAT categories → (letter, rate). Β and Γ are confirmed by the sample's
 * arithmetic; Α, Δ, Ε follow the usual mapping and are checked per receipt (R14).
 */
private val VAT_CATEGORIES: Map<String, Pair<String, BigDecimal>> = linkedMapOf(
    "A" to ("Α" to BigDecimal("6")),
    "B" to ("Β" to BigDecimal("13")),
    "C" to ("Γ" to BigDecimal("24")),
    "D" to ("Δ" to BigDecimal("36")),
    "E" to ("Ε" to BigDecimal("0")),
)
private val FIELDS: Map<String, String> = linkedMapOf(
    "Συνολική αξία" to "gross",
    "Ποσό πληρωμής με κάρτα" to "card",
    "Ημερομηνία, ώρα" to "issued_at",
    "Ημερήσιος α/α" to "daily_sequence",
    "ΦΗΜ" to "register",
    "ΑΦΜ εκδότη" to "vat",
    "Επωνυμία" to "name",
    "Διεύθυνση" to "address",
    "Αριθμός Ζ" to "z_number",
    "Προοδευτικός α/α" to "progressive",
    "Είδος παραστατικού" to "type_label",
    "Σειρά" to "series",
    "Αριθμός παραστατικού" to "number",
    "Νόμισμα συναλλαγής" to "currency",
    "Κωδικός καυσίμου (πρατήρια)" to "fuel_code",
    "Λίτρα καυσίμου (πρατήρια)" to "fuel_liters",
    "Αριθμός παροχής ρεύματος" to "power_supply",
) +
    VAT_CATEGORIES.entries.associate { (key, category) ->
        "Καθαρή αξία ${category.first}" to
            "net_$key"
    } +
    VAT_CATEGORIES.entries.associate { (key, category) -> "ΦΠΑ ${category.first}" to "vat_$key" }

/** The buyer VAT: personal. */
internal val DROP: Set<String> = setOf(keyOf("ΑΦΜ λήπτη (σε τιμολόγιο)"))
private const val SIGNATURE_MISMATCH = "Διαφορά στην ψηφιακή υπογραφή"

// The page also echoes the receipt number and amount it read from the QR code (the URL).
private val ECHO_SEQUENCE = pyRegex("""Με αύξοντα αριθμό \(α/α\)\s*(\d+)""")
private val ECHO_AMOUNT = pyRegex("""Συνολικού ποσού\s*([\d.,]+)\s*ευρώ""")
private val ADDRESS = pyRegex("""^(?<street>.*?)\s*TK:\s*(?<postal>\d{5})\s*(?<city>.*)$""")

/** Check page as observed 2026-11-14. */
internal object FhmLayoutV1 : Layout {
    override val name: String = PARSER_VERSION

    override fun matches(document: HtmlDocument): Boolean = document.select("table.info td").any {
        keyOf(textOf(it)) ==
            TOTAL_LABEL
    }

    override fun parse(document: HtmlDocument, identity: UrlIdentity): CanonicalReceipt =
        build(document, identity)
}

public val LAYOUTS: List<Layout> = listOf(FhmLayoutV1)

public fun parseHtml(html: String, identity: UrlIdentity): CanonicalReceipt {
    val document = io.eksoda.engine.providers.parseHtml(html)
    if (NOT_TRANSMITTED in
        textOf(document)
    ) {
        throw ReceiptNotFound("AADE has no record of this receipt (yet)")
    }
    return parseWithLayouts(document, identity, LAYOUTS)
}

private fun num(text: String?, where: String): BigDecimal? = amount(text, CULTURE, where)

/** Python's `_num(...) or Decimal("0")`: a missing or zero amount becomes "0". */
private fun numOrZero(text: String?, where: String): BigDecimal =
    num(text, where)?.takeIf { it.signum() != 0 } ?: BigDecimal("0")

private fun build(document: HtmlDocument, identity: UrlIdentity): CanonicalReceipt {
    // The canonical URL was produced by recognize(), so it always splits.
    val link = checkNotNull(split(parseSubmittedUrl(identity.canonicalUrl)))
    val pairs = document.select("table.info tr")
        .map { childCells(it, "td") }
        .filter { it.size == 2 && keyOf(textOf(it[0])) !in DROP }
        .map { textOf(it[0]) to textOf(it[1]) }
    val (values, unknown) = labelMap(pairs, FIELDS)

    val register = required(values, "register", "cash register id")
    val progressive = required(values, "progressive", "receipt sequence")
    if (register != link.register || !progressive.pyIsDigit() ||
        progressive.toBigInteger() != link.sequence.toBigInteger()
    ) {
        throw InconsistentSource("the AADE record is for another receipt than the link")
    }

    // collapse_whitespace(soup.get_text(" ")): the same text as textOf over the whole page.
    val pageText = textOf(document)
    ECHO_SEQUENCE.find(pageText)?.let { echo ->
        if (echo.groupValues[1].toBigInteger() != link.sequence.toBigInteger()) {
            throw InconsistentSource("the page echoes another receipt number than the link")
        }
    }

    val gross =
        num(required(values, "gross", "total"), "total")
            ?: throw FormatChanged("missing required field total")
    val notices = mutableListOf<Notice>()
    val alerts = document.select(".callout.alert").map(::textOf)
    if (alerts.any { SIGNATURE_MISMATCH in it }) {
        notices += Notice(
            "signature_mismatch",
            "The QR signature differs from the data AADE holds for this receipt; AADE's data was used.",
        )
    }
    ECHO_AMOUNT.find(pageText)?.let { echo ->
        val qrAmount = num(echo.groupValues[1], "QR amount")
        if (qrAmount == null || qrAmount.compareTo(gross) != 0) {
            notices +=
                Notice("amount_mismatch", "The amount in the QR code differs from AADE's record.")
        }
    }

    val issuedAt = try {
        parseLocalDatetime(
            required(values, "issued_at", "date and time"),
            CULTURE,
            DateOrder.YMD,
            SOURCE_TZ,
        )
    } catch (exception: DateFormatError) {
        throw FormatChanged(exception.message.orEmpty()).apply { initCause(exception) }
    }
    val currency =
        currency(values["currency"]) ?: throw FormatChanged("missing or unknown currency")

    val breakdown = VAT_CATEGORIES.mapNotNull { (key, category) ->
        val net = numOrZero(values["net_$key"], "net per category")
        val vat = numOrZero(values["vat_$key"], "VAT per category")
        if (net.signum() == 0 && vat.signum() == 0) {
            null
        } else {
            VatEntry(
                net = net,
                vat = vat,
                rate = category.second,
                categoryCode = vatCategoryForRate(category.second),
            )
        }
    }

    val card = numOrZero(values["card"], "card amount")
    val payments = mutableListOf<Payment>()
    if (card.signum() >
        0
    ) {
        payments += Payment(amount = card, method = PaymentMethod.CARD, methodLabel = "ΚΑΡΤΑ")
    }
    if ((gross - card).signum() >
        0
    ) {
        payments += Payment(amount = gross - card, method = PaymentMethod.CASH, derived = true)
    }

    val typeLabel = values["type_label"]?.ifEmpty { null }
    val (code, kind) = documentType(typeLabel)
    val series = values["series"]?.ifEmpty { null }
    val number = values["number"]?.ifEmpty { null }
    return CanonicalReceipt(
        providerCode = PROVIDER_CODE,
        parserVersion = PARSER_VERSION,
        culture = CULTURE.code,
        sourceUrl = identity.canonicalUrl,
        issuer = Party(
            countryCode = "GR",
            vatNumber = required(values, "vat", "issuer VAT"),
            legalName = required(values, "name", "merchant name"),
        ),
        branch = branch(values["address"]),
        document = Document(
            kind = kind,
            typeCode = code,
            typeLabel = typeLabel,
            series = series,
            number = number,
            formattedNumber = if (series != null && number != null) "$series-$number" else number,
            issuedAt = issuedAt,
            issuedDate = issuedAt.toLocalDate(),
            currency = currency,
        ),
        identity = Identity(sourceKey = link.sourceKey),
        totals = Totals(
            gross = gross,
            net = breakdown.fold(BigDecimal("0")) { sum, entry -> sum + entry.net },
            vat = breakdown.fold(BigDecimal("0")) { sum, entry -> sum + entry.vat },
        ),
        lines = emptyList(),
        vatBreakdown = breakdown,
        payments = payments,
        notices = notices,
        coverage = Coverage(
            lineItems = false,
            lineTaxAmounts = false,
            officialCodes = false,
            mydataRegistered = false,
            issueTime = IssueTime.EXACT,
            crossChecked = false,
        ),
        providerPayload = buildJsonObject {
            put("record", JsonObject(values.mapValues { (_, value) -> JsonPrimitive(value) }))
            put("alerts", JsonArray(alerts.map(::JsonPrimitive)))
        },
        unknownFields = unknown.entries.associate { (label, value) -> "record.$label" to value },
    )
}

private fun branch(address: String?): Branch? {
    if (address.isNullOrEmpty()) return null
    val match = ADDRESS.matchEntire(address) ?: return Branch(address = Address(street = address))
    val groups = match.groups
    return Branch(
        address = Address(
            street = groups["street"]?.value?.ifEmpty { null },
            postalCode = checkNotNull(groups["postal"]).value,
            city = groups["city"]?.value?.ifEmpty { null },
        ),
    )
}
