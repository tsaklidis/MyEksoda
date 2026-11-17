package io.eksoda.engine.providers.entersoft

import io.eksoda.engine.providers.UrlIdentity
import io.eksoda.engine.url.ParsedUrl
import java.util.Locale

// Entersoft receipt URL recognition.

public const val PROVIDER_CODE: String = "entersoft"
public const val HOST: String = "e-invoicing.gr"
public const val DOCUMENT_PATH: String = "/api/GetInvoice"
internal const val UUID = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"

/** /edocuments/ViewInvoice/{ofenm}/{document UUID}_{access token} */
private val PATH_RE = Regex(
    """^/edocuments/ViewInvoice/(-?\d{1,6})/($UUID)_([0-9a-z]{4,32})/?$""",
    RegexOption.IGNORE_CASE,
)

public data class EntersoftLink(
    val ofenm: String,
    /** Lowercase UUID. */
    val documentId: String,
    /** Access token, case preserved. */
    val token: String,
) {
    val canonicalUrl: String
        get() = "https://$HOST/edocuments/ViewInvoice/$ofenm/${documentId}_$token"
}

public fun split(url: ParsedUrl): EntersoftLink? {
    if (url.host != HOST || url.query.isNotEmpty()) return null
    val match = PATH_RE.matchEntire(url.path) ?: return null
    val (ofenm, documentId, token) = match.destructured
    return EntersoftLink(ofenm, documentId.lowercase(Locale.ROOT), token)
}

public fun recognize(url: ParsedUrl): UrlIdentity? {
    val link = split(url) ?: return null
    return UrlIdentity(
        providerCode = PROVIDER_CODE,
        identity = "entersoft:${link.documentId}",
        canonicalUrl = link.canonicalUrl,
    )
}
