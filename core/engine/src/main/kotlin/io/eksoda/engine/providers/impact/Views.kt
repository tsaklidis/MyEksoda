package io.eksoda.engine.providers.impact

import io.eksoda.engine.providers.childCells
import io.eksoda.engine.providers.textOf
import org.jsoup.nodes.Element

// Extraction of the two views of an Impact receipt page into plain string maps.
//
// Nothing here interprets values: numbers, dates and codes stay strings. Personal fields
// (counterparty, payment transaction ids, signatures) are never read.

internal typealias StrMap = MutableMap<String, String>

private fun strMap(): StrMap = LinkedHashMap()

internal fun fieldName(element: Element): String? =
    element.classNames().firstOrNull { it.startsWith("field-") }?.removePrefix("field-")

// --- Commercial view ----------------------------------------------------------------------------

internal class CommercialView {
    val issuer: StrMap = strMap()
    var headOffice: Map<String, String> = emptyMap()
    var branch: Map<String, String> = emptyMap()
    val identity: StrMap = strMap()
    var lines: List<StrMap> = emptyList()
    var vatBreakdown: List<StrMap> = emptyList()
    val payments: MutableList<StrMap> = mutableListOf()
    val summary: StrMap = strMap()
    val certification: StrMap = strMap()
    val unknown: StrMap = strMap()

    fun noteUnknown(section: String, name: String, value: String) {
        if (name !in
            Labels.COMMERCIAL_KNOWN.getValue(section)
        ) {
            unknown["commercial.$section.$name"] = value
        }
    }
}

/** (name, value) for every field element under [container] with a .value child. */
private fun fields(container: Element?, selector: String = ".field"): List<Pair<String, String>> {
    if (container == null) return emptyList()
    return container.select(selector).mapNotNull { element ->
        val name = fieldName(element)
        val valueElement = element.selectFirst(".value")
        if (name == null || valueElement == null ||
            name in Labels.COMMERCIAL_DROP
        ) {
            null
        } else {
            name to textOf(valueElement)
        }
    }
}

private fun rows(container: Element?): List<StrMap> {
    if (container == null) return emptyList()
    return container.select("tbody tr").mapNotNull { tr ->
        val row = strMap()
        for (td in tr.select("td")) {
            val name = fieldName(td)
            if (name != null &&
                name !in Labels.COMMERCIAL_DROP
            ) {
                row[name] = textOf(td.selectFirst(".value") ?: td)
            }
        }
        row.ifEmpty { null }
    }
}

internal fun extractCommercial(invoice: Element): CommercialView {
    val view = CommercialView()

    // Header: issuer fields, then up to two address blocks (head office, branch).
    val addressBlocks = mutableListOf(strMap())
    for ((name, value) in fields(invoice.selectFirst(".section-header"))) {
        if (name in Labels.ADDRESS_FIELDS) {
            if (name in addressBlocks.last()) addressBlocks += strMap()
            addressBlocks.last()[name] = value
        } else {
            view.issuer.putIfAbsent(name, value)
        }
        view.noteUnknown("header", name, value)
    }
    view.headOffice = addressBlocks[0]
    view.branch = addressBlocks.getOrNull(1).orEmpty()
    if (addressBlocks.size >
        2
    ) {
        view.unknown["commercial.header.extra_address_blocks"] =
            (addressBlocks.size - 2).toString()
    }

    for ((name, value) in fields(invoice.selectFirst(".section-identity"))) {
        view.identity[name] = value
        view.noteUnknown("identity", name, value)
    }

    view.lines = rows(invoice.selectFirst(".section-details"))
    view.lines.forEach { row ->
        row.forEach { (name, value) -> view.noteUnknown("line", name, value) }
    }

    view.vatBreakdown = rows(invoice.selectFirst(".section-taxes"))

    // Payment rows are <tr class="field field-X"><td class="value">; a method row starts a new payment.
    for ((name, value) in fields(invoice.selectFirst(".section-paymentanalysis"), "tr.field")) {
        if (name == "PaymentMethodType" || view.payments.isEmpty()) view.payments += strMap()
        view.payments.last()[name] = value
        view.noteUnknown("payment", name, value)
    }

    for ((name, value) in fields(invoice.selectFirst(".section-summaries"), "tr.field")) {
        view.summary[name] = value
        view.noteUnknown("summary", name, value)
    }

    for ((name, value) in fields(invoice.selectFirst(".section-certification"))) {
        view.certification.putIfAbsent(name, value)
        view.noteUnknown("certification", name, value)
    }
    return view
}

// --- Tax view (myDATA) --------------------------------------------------------------------------

internal class TaxView {
    val header: StrMap = strMap()
    val issuer: StrMap = strMap()
    val summary: StrMap = strMap()
    val payments: MutableList<StrMap> = mutableListOf()
    val identifiers: StrMap = strMap()
    val lines: MutableList<StrMap> = mutableListOf()
    val unknown: StrMap = strMap()
}

private val SECTION_LABELS = mapOf(
    "header" to Labels.TAX_HEADER,
    "issuer" to Labels.TAX_ISSUER,
    "summary" to Labels.TAX_SUMMARY,
    "payment" to Labels.TAX_PAYMENT,
    "identifiers" to Labels.TAX_IDENTIFIERS,
    "line" to Labels.TAX_LINE,
)

private fun Element.parent(tag: String, cssClass: String? = null): Element? =
    parents().firstOrNull { it.tagName() == tag && (cssClass == null || it.hasClass(cssClass)) }

/**
 * The tax-view section of a label/value row and the element that groups it. Rows of one line
 * or one payment share a group element; [extractTaxView] turns each group into one row map.
 */
private fun sectionOf(tr: Element): Pair<String, Element?> {
    val table = tr.parent("table")
    val classes = table?.classNames().orEmpty()
    tr.parent("div", "InvoiceLines")?.let { return "line" to it }
    return when {
        "InvoiceSummary" in classes -> "summary" to table

        "PaymentMethodType" in classes -> "payment" to table

        "Party" in classes -> {
            val title = textOf(table?.selectFirst("h3"))
            (if (title == Labels.ISSUER_PARTY_TITLE) "issuer" else "counterparty") to table
        }

        tr.parent("div", "InvoiceHeader") != null -> "header" to table

        else -> "identifiers" to table
    }
}

private fun keyFor(section: String, label: String): String? {
    val key = SECTION_LABELS.getValue(section)[label]
    if (key == null && section == "identifiers") {
        Labels.TAX_IDENTIFIER_PREFIXES.entries.firstOrNull { (prefix, _) ->
            label.startsWith(prefix)
        }?.let { return it.value }
    }
    return key
}

internal fun extractTaxView(container: Element): TaxView {
    val view = TaxView()
    // group element → (section, row map), in document order. Jsoup elements compare by identity.
    val groups = LinkedHashMap<Element?, Pair<String, StrMap>>()
    for (tr in container.select("tr")) {
        val cells = childCells(tr, "td")
        val label = if (cells.size == 2) textOf(cells[0]) else ""
        if (label.isEmpty() || label in Labels.TAX_DROP) continue
        val value = textOf(cells[1])
        val (section, group) = sectionOf(tr)
        // Buyer data (the counterparty section) is personal: never read.
        if (section != "counterparty") {
            val key = keyFor(section, label)
            if (key == null) {
                // Payment values may be personal; keep only that the label appeared.
                view.unknown["tax.$section.$label"] = if (section == "payment") "" else value
            } else {
                groups.getOrPut(group) { section to strMap() }.second[key] = value
            }
        }
    }
    for ((section, values) in groups.values) {
        when (section) {
            "line" -> view.lines += values
            "payment" -> view.payments += values
            "header" -> view.header.putAll(values)
            "issuer" -> view.issuer.putAll(values)
            "summary" -> view.summary.putAll(values)
            else -> view.identifiers.putAll(values)
        }
    }
    return view
}
