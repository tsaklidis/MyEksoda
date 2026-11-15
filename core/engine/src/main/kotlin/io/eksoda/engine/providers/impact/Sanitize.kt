// Port of backend/apps/receipts/providers/impact/sanitize.py and impact/__init__.py.
package io.eksoda.engine.providers.impact

import io.eksoda.engine.providers.Provider
import io.eksoda.engine.providers.SanitizeResult
import io.eksoda.engine.providers.Secrets
import io.eksoda.engine.providers.childCells
import io.eksoda.engine.providers.finish
import io.eksoda.engine.providers.stripActiveContent
import io.eksoda.engine.providers.textOf

// Removes personal data from a saved Impact receipt page.
//
// Replaced in place (shape-preserving fakes): buyer/counterparty values, payment transaction
// ids, POS terminal id, payment and provider signatures, and the URL access GUID (→ 32 zeros)
// wherever a receipt link or the not-found message shows it. Removed: scripts, styles, the QR
// code (it encodes the URL) and the myDATA QR token. The result is verified by [finish].

public val FAKE_GUID: String = "0".repeat(32)
internal const val MYDATA_QR_REDACTED =
    "https://mydatapi.aade.gr/myDATA/TimologioQR/QRInfo" + "?q=REDACTED"
private val GUID_PATTERNS = listOf(
    // Receipt links.
    Regex("""/p/[A-Z]{2}\d{9}/[0-9A-F]{40}/([0-9A-F]{32})""", RegexOption.IGNORE_CASE),
    // The not-found message.
    Regex("""Guid\s+([0-9A-F]{32})""", RegexOption.IGNORE_CASE),
)

public fun sanitizeHtml(rawHtml: String): SanitizeResult {
    val document = io.eksoda.engine.providers.parseHtml(rawHtml)
    val secrets = Secrets()

    for (valueElement in document.select(".CounterParty .value")) {
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
        val table = tr.parents().firstOrNull { it.tagName() == "table" }
        // Party tables are told apart only by their <h3> title; any party that is not the issuer
        // is the buyer.
        val inCounterparty = table != null && table.hasClass("Party") &&
            textOf(table.selectFirst("h3")) != Labels.ISSUER_PARTY_TITLE
        when {
            label in Labels.TAX_DROP -> secrets.replaceElement(cells[1], "payment_or_signature")
            inCounterparty -> secrets.replaceElement(cells[1], "counterparty")
        }
    }

    stripActiveContent(document)
    document.select(".qrcode-container").forEach { it.empty() }
    document.select("a[href*=mydatapi.aade.gr]").attr("href", MYDATA_QR_REDACTED)
    for (pattern in GUID_PATTERNS) secrets.replacePattern(pattern, { FAKE_GUID }, "url_access_guid")
    return finish(document, secrets)
}

/** Impact SA e-receipt provider (einvoice.impact.gr). Format: docs/providers/impact.md. */
public val IMPACT: Provider = Provider(
    code = PROVIDER_CODE,
    hosts = setOf(HOST),
    layouts = LAYOUTS,
    recognize = ::recognize,
    parseHtml = ::parseHtml,
    sanitize = ::sanitizeHtml,
)
