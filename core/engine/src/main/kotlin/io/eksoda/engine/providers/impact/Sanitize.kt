package io.eksoda.engine.providers.impact

import io.eksoda.engine.providers.Provider
import io.eksoda.engine.providers.SanitizeResult
import io.eksoda.engine.providers.Secrets
import io.eksoda.engine.providers.childCells
import io.eksoda.engine.providers.finish
import io.eksoda.engine.providers.redactMydataQr
import io.eksoda.engine.providers.stripActiveContent
import io.eksoda.engine.providers.textOf

// Removes personal data from a saved Impact receipt page.
//
// Replaced in place (shape-preserving fakes): buyer/counterparty values (with the buyer's address
// and the delivery address), payment transaction
// ids, POS terminal id, payment and provider signatures, and the URL access GUID (→ 32 zeros)
// and provider authentication code (→ 40 zeros) wherever a receipt link or the not-found
// message shows them. Removed: scripts, styles, the QR code (it encodes the URL) and the myDATA
// QR token. The result is verified by [finish]. The s1ecos provider shares this sanitizer.

public val FAKE_GUID: String = "0".repeat(32)
public val FAKE_AUTHENTICATION: String = "0".repeat(40)
private val GUID_PATTERNS = listOf(
    // Receipt links.
    Regex("""/p/[A-Z]{2}\d{9}/[0-9A-F]{40}/([0-9A-F]{32})""", RegexOption.IGNORE_CASE),
    // s1ecos QR links: /v/{prefix}{VAT}-{number}-{authentication code}-{GUID}.
    Regex("""/v/[A-Z]{2}\d{9}-\d+-[0-9A-F]{40}-([0-9A-F]{32})""", RegexOption.IGNORE_CASE),
    // The not-found message.
    Regex("""Guid\s+([0-9A-F]{32})""", RegexOption.IGNORE_CASE),
)
private val AUTHENTICATION_PATTERNS = listOf(
    Regex("""/v/[A-Z]{2}\d{9}-\d+-([0-9A-F]{40})-""", RegexOption.IGNORE_CASE),
    // The not-found message of a /v/ link.
    Regex("""AuthenticationCode\s+([0-9A-F]{40})""", RegexOption.IGNORE_CASE),
)

public fun sanitizeHtml(rawHtml: String): SanitizeResult {
    val document = io.eksoda.engine.providers.parseHtml(rawHtml)
    val secrets = Secrets()

    // The buyer, and where the goods were delivered (the buyer's address).
    val buyerValues = document.select(".CounterParty .value, ${Labels.COMMERCIAL_DELIVERY} .value")
    for (valueElement in buyerValues) {
        // Leaf values only (select() includes the element itself).
        if (valueElement.select(".value").size == 1) {
            secrets.replaceElement(valueElement, "counterparty")
        }
    }
    for (element in document.select(".field")) {
        if (fieldName(element) in Labels.COMMERCIAL_DROP) {
            secrets.replaceElement(element.selectFirst(".value"), "payment_or_signature")
        }
    }

    for (tr in document.select(".InvoicesDocDetailed tr")) {
        val cells = childCells(tr, "td")
        if (cells.size != 2) continue
        val label = textOf(cells[0])
        // Party tables are told apart only by their <h3> title; any party that is not the issuer
        // is the buyer. A party's address is a table nested in the party's table.
        val party = tr.parents().firstOrNull { it.tagName() == "table" && it.hasClass("Party") }
        val inBuyerParty =
            party != null && textOf(party.selectFirst("h3")) != Labels.ISSUER_PARTY_TITLE
        val inDeliveryAddress =
            tr.parents().any { it.tagName() == "td" && it.hasClass(Labels.TAX_DELIVERY_ADDRESS) }
        val inCounterparty = inBuyerParty || inDeliveryAddress
        when {
            label in Labels.TAX_DROP -> secrets.replaceElement(cells[1], "payment_or_signature")
            inCounterparty -> secrets.replaceElement(cells[1], "counterparty")
        }
    }

    stripActiveContent(document)
    document.select(".qrcode-container").forEach { it.empty() }
    // Wherever it is: some pages link it from a <fluent-anchor>, not an <a>.
    secrets.redactMydataQr()
    for (pattern in GUID_PATTERNS) secrets.replacePattern(pattern, { FAKE_GUID }, "url_access_guid")
    for (pattern in AUTHENTICATION_PATTERNS) {
        secrets.replacePattern(pattern, { FAKE_AUTHENTICATION }, "payment_or_signature")
    }
    return finish(document, secrets)
}

/** Impact SA e-receipt provider (einvoice.impact.gr). */
public val IMPACT: Provider = Provider(
    code = PROVIDER_CODE,
    hosts = setOf(HOST),
    layouts = LAYOUTS,
    recognize = ::recognize,
    parseHtml = ::parseHtml,
    sanitize = ::sanitizeHtml,
)
