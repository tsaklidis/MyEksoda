// Port of backend/apps/receipts/providers/entersoft/parser.py.
package io.eksoda.engine.providers.entersoft

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
import io.eksoda.engine.canonical.Line
import io.eksoda.engine.canonical.Party
import io.eksoda.engine.canonical.Payment
import io.eksoda.engine.canonical.PriceBasis
import io.eksoda.engine.canonical.Totals
import io.eksoda.engine.canonical.VatEntry
import io.eksoda.engine.mydata.Unit
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
import io.eksoda.engine.text.parseDate
import io.eksoda.engine.text.parseLocalDatetime
import io.eksoda.engine.text.pyLocalToOffset
import io.eksoda.engine.text.pyRegex
import io.eksoda.engine.text.pyStrip
import io.eksoda.engine.url.ParsedUrl
import io.eksoda.engine.url.parseQs
import io.eksoda.engine.url.urlsplit
import io.eksoda.engine.vocabulary.currency
import io.eksoda.engine.vocabulary.documentType
import io.eksoda.engine.vocabulary.paymentMethod
import io.eksoda.engine.vocabulary.unit
import io.eksoda.engine.vocabulary.vatCategoryForRate
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.jsoup.nodes.Element
import java.math.BigDecimal
import java.time.LocalTime
import java.time.ZoneId
import java.util.Collections
import java.util.IdentityHashMap
import java.util.Locale
import org.jsoup.nodes.Document as HtmlDocument

// Entersoft receipt document → CanonicalReceipt (docs/providers/entersoft.md).
//
// Single rendering (no tax view to cross-check), so the parser enforces the receipt's own
// arithmetic as fatal consistency checks (Σ lines = total, VAT analysis = net/VAT totals,
// Σ discounts = discount total) and leaves per-line arithmetic to validation warnings. Fields
// are located by labels and data-title attributes, not positions, so reordered columns keep
// working.

public const val PARSER_VERSION: String = "entersoft/1"
private val CULTURE = Culture.EL
private val SOURCE_TZ: ZoneId = ZoneId.of("Europe/Athens")
private val TOLERANCE = BigDecimal("0.02")
private const val NOT_FOUND_MARKER = "Page not found"

/** td[data-title] → key */
private val ITEM_COLUMNS = mapOf(
    "Κωδικός" to "code",
    "Περιγραφή" to "description",
    "Μ.Μ." to "unit",
    "Ποσότητα" to "quantity",
    "Τιμή μονάδας" to "unit_price",
    "Αξία προ έκπτωσης" to "before_discount",
    "Έκπτωση" to "discount",
    "ΦΠΑ%" to "vat_rate",
    "Συνολική Αξία" to "gross",
)

/** thead th → key (fallback when cells lack data-title) */
private val ITEM_HEADERS = mapOf(
    "ΚΩΔΙΚΟΣ" to "code",
    "ΠΕΡΙΓΡΑΦΗ" to "description",
    "ΜΜ" to "unit",
    "ΠΟΣΟΤΗΤΑ" to "quantity",
    "ΤΙΜΗ ΜΟΝ." to "unit_price",
    "ΑΞΙΑ ΠΡΟ ΕΚΠΤ." to "before_discount",
    "ΕΚΠΤ." to "discount",
    "ΦΠΑ%" to "vat_rate",
    "ΣΥΝΟΛΙΚΗ ΑΞΙΑ" to "gross",
)
private val HEADER_FIELDS = mapOf(
    "Α.Φ.Μ" to "vat",
    "Αρ. Παραστατικού" to "number",
    "Ημ/νία έκδοσης" to "issue_date",
)
private val TOTAL_LABELS = mapOf(
    "Αξία προ έκπτωσης" to "before_discount",
    "Έκπτωση" to "discount",
    "ΤΕΛΙΚΗ ΑΞΙΑ" to "gross",
    "Καθαρή Αξία" to "net",
    "Φ.Π.Α" to "vat",
)
private val REMARK_LABELS = mapOf(
    "Πάροχος" to "provider",
    "Url πάροχου" to "provider_url",
    "Άδεια καταλληλότητας (ΥΠΑΗΕΣ)" to "license",
    "UID" to "uid",
    "M.AR.K" to "mark",
    "Ημ/νία διαβίβασης" to "transmitted_at",
)

/** The provider signature: never read. */
internal val REMARK_DROP: Set<String> = setOf(keyOf("AuthenticationCode"))

// Titled blocks. The parser never reads the customer block (personal data); the sanitizer
// replaces its values.
internal val CUSTOMER_BLOCK: String = keyOf("ΣΤΟΙΧΕΙΑ ΠΕΛΑΤΗ")
private val VAT_BLOCK = keyOf("ΑΝΑΛΥΣΗ ΦΠΑ")
private val PAYMENT_BLOCK = keyOf("ΣΤΟΙΧΕΙΑ ΠΛΗΡΩΜΗΣ")
private val HEADER_LINE = pyRegex("""^([^:]{2,40}):\s*(\S.*)$""")
private val NUMBER = pyRegex("""^(?<series>.+)-(?<number>\d+)$""")
private val ADDRESS = pyRegex(
    """^(?<street>[^,]+),\s*(?<number>[^,]*),\s*(?<postal>\d{5}),\s*(?<city>.+)$""",
)
private val NON_DIGIT = pyRegex("""\D""")
private val NOT_CURRENCY = pyRegex("""[\d.,\s-]""")
private val HEADER_FIELD_KEYS = HEADER_FIELDS.keys.map(::keyOf).toSet()
private const val VAT_COLUMNS = 3
private const val PAYMENT_COLUMNS = 2

// --- fetching helpers ---------------------------------------------------------------------------

public fun isNotFound(html: String): Boolean = NOT_FOUND_MARKER in html

/** The receipt document framed by the shell page; it must be the same document. */
public fun documentUrlFromShell(html: String, link: EntersoftLink): ParsedUrl {
    val document = io.eksoda.engine.providers.parseHtml(html)
    val frame = document.selectFirst("iframe#iframeContent") ?: document.selectFirst("iframe[src]")
    if (frame == null || frame.attr("src").isEmpty()) {
        throw FormatChanged("the page has no document frame")
    }
    val parts = urlsplit(frame.attr("src"))
    val relative = parts.scheme.isEmpty() && parts.netloc.isEmpty()
    val sameSite = relative || (parts.scheme == "https" && parts.netloc == HOST)
    if (!sameSite || parts.path != DOCUMENT_PATH) {
        throw FormatChanged("the document frame points to an unexpected address")
    }
    val params = parseQs(parts.query)
    val documentId = params["intRefDocID"]?.firstOrNull().orEmpty().lowercase(Locale.ROOT)
    val token = params["hashToken"]?.firstOrNull().orEmpty()
    if (documentId != link.documentId || token != link.token) {
        throw InconsistentSource("the document frame points to another document")
    }
    return ParsedUrl("https", HOST, null, parts.path, parts.query)
}

// --- layout -------------------------------------------------------------------------------------

/** Receipt rendering as observed 2026-11-14 (LIDL retail receipts). */
internal object EntersoftLayoutV1 : Layout {
    override val name: String = PARSER_VERSION

    override fun matches(document: HtmlDocument): Boolean =
        document.select("table td[data-title]").isNotEmpty() &&
            document.selectFirst(".BoldBlueHeader") != null

    override fun parse(document: HtmlDocument, identity: UrlIdentity): CanonicalReceipt =
        Builder(document, identity).build()
}

public val LAYOUTS: List<Layout> = listOf(EntersoftLayoutV1)

public fun parseHtml(html: String, identity: UrlIdentity): CanonicalReceipt {
    if (isNotFound(html)) throw ReceiptNotFound("the provider shows its page-not-found message")
    return parseWithLayouts(io.eksoda.engine.providers.parseHtml(html), identity, LAYOUTS)
}

private fun identitySet(elements: Sequence<Element>): Set<Element> =
    Collections.newSetFromMap(IdentityHashMap<Element, Boolean>()).apply { addAll(elements) }

private class Builder(private val document: HtmlDocument, private val url: UrlIdentity) {
    private val unknown = LinkedHashMap<String, String>()
    private val blocks: Map<String, Element> = titledBlocks()
    private val blocked: Set<Element> =
        identitySet(blocks.values.asSequence().flatMap { it.allElements.drop(1) })

    /**
     * Elements wrapping a titled block (e.g. the row that holds the customer block) are not free
     * rows either: their text includes the block's, possibly personal, content.
     */
    private val wrapping: Set<Element> =
        identitySet(blocks.values.asSequence().flatMap { it.parents() })

    fun num(text: String?, where: String): BigDecimal? = amount(text, CULTURE, where)

    fun requiredNum(text: String?, where: String): BigDecimal =
        num(text, where) ?: throw FormatChanged("missing required field $where")

    // --- structure ------------------------------------------------------------------------------

    private fun titledBlocks(): Map<String, Element> {
        val result = LinkedHashMap<String, Element>()
        for (block in document.select(".backgrey")) {
            val title = block.selectFirst(".BoldBlueHeader") ?: continue
            result.putIfAbsent(keyOf(textOf(title)), block)
        }
        return result
    }

    /** Cell texts of div.row elements neither inside nor around the titled blocks. */
    fun freeRows(): List<Pair<String, String>> = document.select("div.row")
        .filter { it !in blocked && it !in wrapping }
        .map { childCells(it) }
        .filter { it.size == 2 }
        .map { textOf(it[0]) to textOf(it[1]) }

    fun blockRows(titleKey: String, width: Int): List<List<String>> {
        val block = blocks[titleKey] ?: return emptyList()
        return block.select("div.row")
            .filterNot { it.hasClass("BoldBlueHeader") }
            .map { row -> childCells(row).map(::textOf) }
            // Column-header rows carry no digits in their value cell.
            .filter { cells -> cells.size == width && cells.last().any(Char::isDigit) }
    }

    // --- sections -------------------------------------------------------------------------------

    fun header(): MutableMap<String, String> {
        val nameElement = document.selectFirst(".BoldBlueHeader")
        val info = linkedMapOf("name" to textOf(nameElement))
        val lines = nameElement?.parent()?.children().orEmpty()
            .filter { it.tagName() == "div" && it.hasClass("fontSize8pt") }
            .map(::textOf)
        info["address"] = lines.firstOrNull { it.isNotEmpty() && !isHeaderField(it) }.orEmpty()
        val pairs = mutableListOf<Pair<String, String>>()
        for (div in document.select("div.fontSize8pt").filter { it !in blocked }) {
            val match = HEADER_LINE.matchEntire(textOf(div)) ?: continue
            val (label, value) = match.destructured
            pairs += label to value
            if (keyOf(label) == keyOf("Αρ. Παραστατικού")) {
                val title = div.previousElementSiblings()
                    .firstOrNull { it.tagName() == "div" && it.hasClass("BoldBlueHeader") }
                info["title"] = textOf(title)
            }
        }
        info.putAll(labelMap(pairs, HEADER_FIELDS).first)
        return info
    }

    fun items(): List<Map<String, String>> {
        val table = document.select("table").first { it.select("td[data-title]").isNotEmpty() }
        val headers = table.select("thead th").map { keyOf(textOf(it)) }
        val byTitle = ITEM_COLUMNS.entries.associate { (label, key) -> keyOf(label) to key }
        val byHeader = ITEM_HEADERS.entries.associate { (label, key) -> keyOf(label) to key }
        return table.select("tbody tr").mapNotNull { tr ->
            val row = LinkedHashMap<String, String>()
            childCells(tr, "td").forEachIndexed { position, td ->
                val title = td.attr("data-title")
                val key = (if (title.isNotEmpty()) byTitle[keyOf(title)] else null)
                    ?: headers.getOrNull(position)?.let { byHeader[it] }
                if (key == null) {
                    unknown["item.${title.ifEmpty { position.toString() }}"] = textOf(td)
                } else {
                    row[key] = textOf(td)
                }
            }
            row.ifEmpty { null }
        }
    }

    fun remarks(freeRows: List<Pair<String, String>>): Map<String, String> {
        val pairs = freeRows.filter { (label, _) ->
            label.endsWith(":") && keyOf(label) !in REMARK_DROP
        }
        val (values, unknownLabels) = labelMap(pairs, REMARK_LABELS)
        unknownLabels.forEach { (label, value) -> unknown["remark.$label"] = value }
        return values
    }

    // --- build ----------------------------------------------------------------------------------

    fun build(): CanonicalReceipt {
        val header = header()
        val freeRows = freeRows()
        val remarks = remarks(freeRows)
        val totalsRaw = labelMap(freeRows, TOTAL_LABELS).first
        val lines = lines()
        val totals = totals(totalsRaw)
        val breakdown = vatBreakdown()
        checkConsistency(lines, totals, breakdown)
        val (document, issueTime) = document(header, remarks, totalsRaw)

        val uid = remarks["uid"].orEmpty().uppercase(Locale.ROOT).ifEmpty { null }
        val mark = remarks["mark"]?.ifEmpty { null }
        if (uid == null && mark == null) throw FormatChanged("neither UID nor MARK on the page")
        val vatNumber = NON_DIGIT.replace(required(header, "vat", "issuer VAT"), "")
        return CanonicalReceipt(
            providerCode = PROVIDER_CODE,
            parserVersion = PARSER_VERSION,
            culture = CULTURE.code,
            sourceUrl = url.canonicalUrl,
            issuer = Party(
                countryCode = "GR",
                vatNumber = vatNumber,
                legalName = required(header, "name", "merchant name"),
            ),
            branch = branch(header["address"]),
            document = document,
            identity = Identity(uid = uid, mark = mark),
            totals = totals,
            lines = lines,
            vatBreakdown = breakdown,
            payments = payments(),
            coverage = Coverage(
                lineItems = true,
                lineTaxAmounts = false,
                officialCodes = false,
                mydataRegistered = true,
                issueTime = issueTime,
                crossChecked = false,
            ),
            providerPayload = buildJsonObject {
                put("header", json(header))
                put("remarks", json(remarks))
                put("totals", json(totalsRaw))
            },
            unknownFields = unknown,
        )
    }

    fun document(
        header: Map<String, String>,
        remarks: Map<String, String>,
        totalsRaw: Map<String, String>,
    ): Pair<Document, IssueTime> {
        val title = header["title"]?.ifEmpty { null }
        val (code, kind) = documentType(title)
        if (code == null) unknown["document.type_label"] = title.orEmpty()
        val (issuedDate, transmittedAt) = try {
            val date = parseDate(required(header, "issue_date", "issue date"), DateOrder.DMY)
            val transmitted = remarks["transmitted_at"]?.ifEmpty { null }
            date to transmitted?.let { parseLocalDatetime(it, CULTURE, DateOrder.DMY, SOURCE_TZ) }
        } catch (exception: DateFormatError) {
            throw FormatChanged(exception.message.orEmpty()).apply { initCause(exception) }
        }
        // The page prints only the issue date. The myDATA transmission time, when it is on the
        // same day, is the best available time of purchase.
        val sameDay = transmittedAt != null && transmittedAt.toLocalDate() == issuedDate
        val (issuedAt, issueTime) = if (sameDay && transmittedAt != null) {
            transmittedAt to IssueTime.APPROXIMATE
        } else {
            pyLocalToOffset(issuedDate.atTime(LocalTime.MIDNIGHT), SOURCE_TZ) to IssueTime.DATE_ONLY
        }

        // The currency is printed only as a suffix of the total, e.g. "75,48EUR".
        val grossText = required(totalsRaw, "gross", "total")
        val currency = currency(NOT_CURRENCY.replace(grossText, ""))
            ?: throw FormatChanged("missing currency on the total")
        val numberText = header["number"].orEmpty()
        val numberMatch = NUMBER.matchEntire(numberText)
        return Document(
            kind = kind,
            typeCode = code,
            typeLabel = title,
            series = numberMatch?.groups?.get("series")?.value,
            number = numberMatch?.groups?.get("number")?.value ?: numberText.ifEmpty { null },
            formattedNumber = numberText.ifEmpty { null },
            issuedAt = issuedAt,
            issuedDate = issuedDate,
            currency = currency,
        ) to issueTime
    }

    fun lines(): List<Line> {
        val rows = items()
        if (rows.isEmpty()) throw FormatChanged("the receipt has no item rows")
        return rows.mapIndexed { offset, row ->
            val index = offset + 1
            val where = "line $index"
            val unitLabel = row["unit"]?.ifEmpty { null }
            var unit = unit(unitLabel)
            if (unit == null && unitLabel != null) {
                unknown["unit_label.$unitLabel"] = unitLabel
                unit = Unit.OTHER
            }
            val rate = num(row["vat_rate"], where)
            val unitPrice = num(row["unit_price"], where)
            Line(
                lineNumber = index,
                description = row["description"].orEmpty(),
                gross = requiredNum(required(row, "gross", "$where total"), where),
                quantity = num(row["quantity"], where),
                unit = unit,
                unitLabel = unitLabel,
                unitPrice = unitPrice,
                unitPriceBasis = if (unitPrice != null) PriceBasis.GROSS else PriceBasis.UNKNOWN,
                grossBeforeDiscount = num(row["before_discount"], where),
                discount = num(row["discount"], where),
                vatRate = rate,
                vatCategoryCode = vatCategoryForRate(rate),
                merchantProductCode = row["code"]?.ifEmpty { null },
                raw = row.entries.associate { (key, value) -> "item.$key" to value },
            )
        }
    }

    fun totals(raw: Map<String, String>): Totals = Totals(
        gross = requiredNum(required(raw, "gross", "total"), "total"),
        net = num(raw["net"], "net total"),
        vat = num(raw["vat"], "VAT total"),
        grossBeforeDiscount = num(raw["before_discount"], "total before discount"),
        discount = num(raw["discount"], "discount total"),
    )

    fun vatBreakdown(): List<VatEntry> = blockRows(VAT_BLOCK, VAT_COLUMNS).map { cells ->
        val (rateText, netText, vatText) = cells
        val rate = num(rateText, "VAT analysis")
        VatEntry(
            rate = rate,
            categoryCode = vatCategoryForRate(rate),
            net = requiredNum(netText, "VAT analysis"),
            vat = requiredNum(vatText, "VAT analysis"),
        )
    }

    fun payments(): List<Payment> = blockRows(PAYMENT_BLOCK, PAYMENT_COLUMNS).map { cells ->
        val (label, amountText) = cells
        val method = paymentMethod(label)
        if (method == null) unknown["payment_label.$label"] = label
        Payment(amount = requiredNum(amountText, "payment"), method = method, methodLabel = label)
    }

    fun checkConsistency(lines: List<Line>, totals: Totals, breakdown: List<VatEntry>) {
        fun off(a: BigDecimal, b: BigDecimal?) = b != null && (a - b).abs() > TOLERANCE
        fun sum(values: List<BigDecimal>) = values.fold(BigDecimal.ZERO, BigDecimal::add)

        if (off(sum(lines.map { it.gross }), totals.gross)) {
            throw InconsistentSource("the line totals do not add up to the receipt total")
        }
        val discounts = lines.mapNotNull { it.discount }
        if (discounts.isNotEmpty() && off(sum(discounts), totals.discount)) {
            throw InconsistentSource(
                "the line discounts do not add up to the discount total",
            )
        }
        if (breakdown.isNotEmpty() && off(sum(breakdown.map { it.net }), totals.net)) {
            throw InconsistentSource("the VAT analysis does not add up to the net total")
        }
        if (breakdown.isNotEmpty() && off(sum(breakdown.map { it.vat }), totals.vat)) {
            throw InconsistentSource("the VAT analysis does not add up to the VAT total")
        }
    }
}

private fun json(values: Map<String, String>): JsonObject =
    JsonObject(values.mapValues { (_, value) -> JsonPrimitive(value) })

private fun branch(address: String?): Branch? {
    if (address.isNullOrEmpty()) return null
    val match = ADDRESS.matchEntire(address) ?: return Branch(address = Address(street = address))
    val groups = match.groups
    val number = checkNotNull(groups["number"]).value.pyStrip()
    return Branch(
        address = Address(
            street = checkNotNull(groups["street"]).value.pyStrip(),
            number = if (number.isEmpty() || number == "-") null else number,
            postalCode = checkNotNull(groups["postal"]).value,
            city = checkNotNull(groups["city"]).value.pyStrip(),
        ),
    )
}

/** A known "Label: value" header line (VAT, number, date), as opposed to an address. */
private fun isHeaderField(line: String): Boolean {
    val match = HEADER_LINE.matchEntire(line) ?: return false
    return keyOf(match.groupValues[1]) in HEADER_FIELD_KEYS
}
