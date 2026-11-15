// Port of backend/apps/receipts/providers/entersoft/sanitize.py and entersoft/__init__.py.
package io.eksoda.engine.providers.entersoft

import io.eksoda.engine.FormatChanged
import io.eksoda.engine.ReceiptNotFound
import io.eksoda.engine.fetch.FetchedDocument
import io.eksoda.engine.fetch.Fetcher
import io.eksoda.engine.providers.Provider
import io.eksoda.engine.providers.SanitizeResult
import io.eksoda.engine.providers.Secrets
import io.eksoda.engine.providers.UrlIdentity
import io.eksoda.engine.providers.childCells
import io.eksoda.engine.providers.fakeLike
import io.eksoda.engine.providers.finish
import io.eksoda.engine.providers.impact.MYDATA_QR_REDACTED
import io.eksoda.engine.providers.keyOf
import io.eksoda.engine.providers.stripActiveContent
import io.eksoda.engine.providers.textOf
import io.eksoda.engine.url.parseSubmittedUrl

// Removes personal data from saved Entersoft pages (shell or receipt document).
//
// Replaced in place: document UUIDs and access tokens inside receipt links and frame parameters,
// customer block values, the provider AuthenticationCode, download tokens. Removed:
// scripts/styles, the QR image URL (it encodes the receipt link), the myDATA QR token.

public const val FAKE_UUID: String = "00000000-0000-4000-8000-000000000000"
private val LINK_ID = Regex("""($UUID)_[0-9a-z]{4,32}""", RegexOption.IGNORE_CASE)
private val LINK_TOKEN = Regex("""${UUID}_([0-9a-z]{4,32})""", RegexOption.IGNORE_CASE)
private val PARAM_ID = Regex("""intRefDocID=([0-9a-z-]+)""", RegexOption.IGNORE_CASE)
private val PARAM_TOKEN = Regex("""hashToken=([0-9a-z]+)""", RegexOption.IGNORE_CASE)

private fun fakeToken(token: String): String = fakeLike(token, letter = 'x')

public fun sanitizeHtml(rawHtml: String): SanitizeResult {
    val document = io.eksoda.engine.providers.parseHtml(rawHtml)
    val secrets = Secrets()

    for (block in document.select(".backgrey")) {
        val title = block.selectFirst(".BoldBlueHeader")
        if (title != null && keyOf(textOf(title)) == CUSTOMER_BLOCK) {
            for (row in block.select("div.row")) {
                val cells = childCells(row)
                if (cells.size == 2) secrets.replaceElement(cells[1], "customer")
            }
        }
    }
    for (row in document.select("div.row")) {
        val cells = childCells(row)
        if (cells.size == 2 &&
            keyOf(textOf(cells[0])) in REMARK_DROP
        ) {
            secrets.replaceElement(cells[1], "signature")
        }
    }

    for (element in document.select("[data-download-token]")) {
        secrets.replaceAttribute(element, "data-download-token", "download_token")
    }
    document.select("img[src*=CreateBarcode]").attr("src", "/api/CreateBarcode?barcode=REDACTED")
    document.select("a[href*=mydatapi.aade.gr]").attr("href", MYDATA_QR_REDACTED)
    stripActiveContent(document)

    secrets.replacePattern(LINK_ID, { FAKE_UUID }, "url_access_token")
    secrets.replacePattern(LINK_TOKEN, ::fakeToken, "url_access_token")
    secrets.replacePattern(PARAM_ID, { FAKE_UUID }, "url_access_token")
    secrets.replacePattern(PARAM_TOKEN, ::fakeToken, "url_access_token")
    return finish(document, secrets)
}

public val HOSTS: Set<String> = setOf(HOST)

/** The document endpoint answers 418 (with an "Error 404" page) for unknown documents. */
private val NOT_FOUND_STATUSES = setOf(418)
private const val STATUS_OK = 200

/** Two requests on the same host: the shell page, then the document it frames. */
public suspend fun fetchDocument(identity: UrlIdentity, fetcher: Fetcher): FetchedDocument {
    val pageUrl = parseSubmittedUrl(identity.canonicalUrl)
    val shell = fetcher.get(pageUrl, allowedHosts = HOSTS)
    val documentUrl = documentUrlFromShell(shell.text(), checkNotNull(split(pageUrl)))
    val document = fetcher.get(
        documentUrl,
        allowedHosts = HOSTS,
        acceptStatuses = NOT_FOUND_STATUSES,
    )
    if (document.status != STATUS_OK) {
        if (isNotFound(
                document.text(),
            )
        ) {
            throw ReceiptNotFound("HTTP ${document.status} with a not-found page")
        }
        throw FormatChanged("unexpected HTTP status ${document.status}")
    }
    return document
}

/** Entersoft e-receipt provider (e-invoicing.gr). Format: docs/providers/entersoft.md. */
public val ENTERSOFT: Provider = Provider(
    code = PROVIDER_CODE,
    hosts = HOSTS,
    layouts = LAYOUTS,
    recognize = ::recognize,
    parseHtml = ::parseHtml,
    sanitize = ::sanitizeHtml,
    fetchDocument = ::fetchDocument,
)
