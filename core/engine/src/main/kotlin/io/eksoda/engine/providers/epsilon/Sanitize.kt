package io.eksoda.engine.providers.epsilon

import io.eksoda.engine.fetch.FetchedDocument
import io.eksoda.engine.fetch.Fetcher
import io.eksoda.engine.fetch.XML_TYPES
import io.eksoda.engine.providers.Provider
import io.eksoda.engine.providers.SanitizeResult
import io.eksoda.engine.providers.Secrets
import io.eksoda.engine.providers.UrlIdentity
import io.eksoda.engine.providers.finish
import io.eksoda.engine.url.parseSubmittedUrl

// Removes personal data from a saved Epsilon Digital myDATA XML.
//
// Replaced in place (shape-preserving fakes): the authentication code, payment transaction and
// terminal ids, provider signatures, ECR tokens, free-text payment info and line comments, the
// counterpart (buyer), and the document id wherever a receipt link shows it. The merchant's
// VAT number, UID and MARK are business data and stay.

public val FAKE_HEX_ID: String = "0".repeat(32)
public val FAKE_UUID: String = hexToUuid(FAKE_HEX_ID)

/** Leaf tags whose text is replaced → category. */
private val SECRET_TAGS = mapOf(
    "authenticationCode" to "signature",
    "Signature" to "signature",
    "ECRToken" to "signature",
    "transactionId" to "payment_id",
    "tid" to "terminal_id",
    "paymentMethodInfo" to "free_text",
    "lineComments" to "free_text",
)
private val LINK_PATTERNS = listOf(
    Regex("""/fd/([0-9a-f]{32})""", RegexOption.IGNORE_CASE) to FAKE_HEX_ID,
    Regex("""/DocViewer/($UUID)""", RegexOption.IGNORE_CASE) to FAKE_UUID,
    Regex("""/GetInvoiceDocDetailed/($UUID)""", RegexOption.IGNORE_CASE) to FAKE_UUID,
    Regex("""documentId=($UUID)""", RegexOption.IGNORE_CASE) to FAKE_UUID,
)

public fun sanitizeXml(rawXml: String): SanitizeResult {
    val document = parseXml(rawXml)
    val secrets = Secrets()
    for (element in document.allElements) {
        if (element.childrenSize() != 0) continue
        val category = SECRET_TAGS[element.tagName()]
            ?: "counterpart".takeIf {
                element.parents().any { parent -> parent.tagName() == "counterpart" }
            }
        if (category != null) secrets.replaceElement(element, category)
    }
    for ((pattern, fake) in LINK_PATTERNS) {
        secrets.replacePattern(pattern, { fake }, "url_document_id")
    }
    return finish(document, secrets)
}

/** One request: the detailed XML (the viewer page needs a human check). */
public suspend fun fetchDocument(identity: UrlIdentity, fetcher: Fetcher): FetchedDocument {
    val link = checkNotNull(split(parseSubmittedUrl(identity.canonicalUrl)))
    return fetcher.get(
        parseSubmittedUrl(link.documentUrl),
        allowedHosts = setOf(link.host),
        contentTypes = XML_TYPES,
    )
}

/** Epsilon Digital e-receipt provider (epsilondigital-<merchant>.epsilonnet.gr). */
public val EPSILON: Provider = Provider(
    code = PROVIDER_CODE,
    hosts = HOSTS,
    layouts = LAYOUTS,
    recognize = ::recognize,
    parseHtml = ::parseDocument,
    sanitize = ::sanitizeXml,
    fetchDocument = ::fetchDocument,
)
