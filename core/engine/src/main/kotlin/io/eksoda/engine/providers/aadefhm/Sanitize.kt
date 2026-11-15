// Port of backend/apps/receipts/providers/aade_fhm/sanitize.py and aade_fhm/__init__.py.
package io.eksoda.engine.providers.aadefhm

import io.eksoda.engine.providers.Provider
import io.eksoda.engine.providers.SanitizeResult
import io.eksoda.engine.providers.Secrets
import io.eksoda.engine.providers.childCells
import io.eksoda.engine.providers.finish
import io.eksoda.engine.providers.keyOf
import io.eksoda.engine.providers.stripActiveContent
import io.eksoda.engine.providers.textOf

// Removes personal data from a saved AADE cash-register check page.
//
// The page has little personal data: the buyer VAT (invoices only) is replaced in place; the
// shop's register id and receipt numbers are business data and stay.

public fun sanitizeHtml(rawHtml: String): SanitizeResult {
    val document = io.eksoda.engine.providers.parseHtml(rawHtml)
    val secrets = Secrets()
    for (tr in document.select("table.info tr")) {
        val cells = childCells(tr, "td")
        if (cells.size == 2 &&
            keyOf(textOf(cells[0])) in DROP
        ) {
            secrets.replaceElement(cells[1], "buyer")
        }
    }
    stripActiveContent(document)
    return finish(document, secrets)
}

/** AADE cash-register (ΦΗΜ) receipt check page (www1.aade.gr). Format: docs/providers/aade.md. */
public val AADE_FHM: Provider = Provider(
    code = PROVIDER_CODE,
    hosts = setOf(HOST),
    layouts = LAYOUTS,
    recognize = ::recognize,
    parseHtml = ::parseHtml,
    sanitize = ::sanitizeHtml,
)
