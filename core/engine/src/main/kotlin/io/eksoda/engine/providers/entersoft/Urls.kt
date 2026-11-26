package io.eksoda.engine.providers.entersoft

import io.eksoda.engine.providers.UrlIdentity
import io.eksoda.engine.url.ParsedUrl
import io.eksoda.engine.url.parseQs
import java.util.Locale

// Entersoft receipt URL recognition.

public const val PROVIDER_CODE: String = "entersoft"
public const val HOST: String = "e-invoicing.gr"
public const val DOCUMENT_PATH: String = "/api/GetInvoice"
internal const val UUID = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
private const val VIEW_PATH = "/edocuments/ViewInvoice"

/** /edocuments/ViewInvoice/{ofenm}/{document UUID}_{access token} */
private val PATH_RE = Regex(
    """^$VIEW_PATH/(-?\d{1,6})/($UUID)_([0-9a-z]{4,32})/?$""",
    RegexOption.IGNORE_CASE,
)
private val VIEW_PATH_RE = Regex("""^$VIEW_PATH/?$""", RegexOption.IGNORE_CASE)

/** The query of the older link form, ct, id, s and h, each once (printed until 2025). */
private val LEGACY_PARAMS = mapOf(
    "ct" to Regex("""[a-z]{1,16}""", RegexOption.IGNORE_CASE),
    "id" to Regex("""[0-9a-f]{40}""", RegexOption.IGNORE_CASE),
    "s" to Regex("""[a-z0-9]{1,4}""", RegexOption.IGNORE_CASE),
    "h" to Regex("""[0-9a-z]{4,32}""", RegexOption.IGNORE_CASE),
)

/** A receipt link; the shell page it opens frames the receipt document ([framedBy]). */
public sealed interface EntersoftLink {
    public val canonicalUrl: String

    /** Pre-fetch dedup key. */
    public val identity: String

    /** Whether the document frame's query parameters name this receipt. */
    public fun framedBy(params: Map<String, List<String>>): Boolean
}

/** The current form: a document UUID and an access token in the path. */
public data class DocumentLink(
    val ofenm: String,
    /** Lowercase UUID. */
    val documentId: String,
    /** Access token, case preserved. */
    val token: String,
) : EntersoftLink {
    override val canonicalUrl: String
        get() = "https://$HOST$VIEW_PATH/$ofenm/${documentId}_$token"
    override val identity: String get() = "entersoft:$documentId"

    override fun framedBy(params: Map<String, List<String>>): Boolean =
        param(params, "intRefDocID").lowercase(Locale.ROOT) == documentId &&
            param(params, "hashToken") == token
}

/** The older form: a content type, a 40-hex document id, a source and a token in the query. */
public data class LegacyLink(
    val contentType: String,
    /** Uppercase hex. */
    val documentId: String,
    val source: String,
    /** Access token, case preserved. */
    val token: String,
) : EntersoftLink {
    override val canonicalUrl: String
        get() = "https://$HOST$VIEW_PATH?ct=$contentType&id=$documentId&s=$source&h=$token"
    override val identity: String get() = "entersoft:$documentId"

    override fun framedBy(params: Map<String, List<String>>): Boolean =
        param(params, "id").equals(documentId, ignoreCase = true) &&
            param(params, "hashToken") == token &&
            param(params, "source").equals(source, ignoreCase = true) &&
            param(params, "contentType").equals(contentType, ignoreCase = true)
}

private fun param(params: Map<String, List<String>>, name: String): String =
    params[name]?.singleOrNull().orEmpty()

public fun split(url: ParsedUrl): EntersoftLink? {
    if (url.host != HOST) return null
    if (url.query.isNotEmpty()) return legacyLink(url)
    val match = PATH_RE.matchEntire(url.path) ?: return null
    val (ofenm, documentId, token) = match.destructured
    return DocumentLink(ofenm, documentId.lowercase(Locale.ROOT), token)
}

private fun legacyLink(url: ParsedUrl): LegacyLink? {
    val fields = url.query.split('&')
    val params = parseQs(url.query)
    val wellFormed = VIEW_PATH_RE.matches(url.path) &&
        fields.size == LEGACY_PARAMS.size &&
        params.keys == LEGACY_PARAMS.keys &&
        LEGACY_PARAMS.all { (name, shape) -> shape.matches(param(params, name)) }
    if (!wellFormed) return null
    return LegacyLink(
        contentType = param(params, "ct"),
        documentId = param(params, "id").uppercase(Locale.ROOT),
        source = param(params, "s"),
        token = param(params, "h"),
    )
}

public fun recognize(url: ParsedUrl): UrlIdentity? {
    val link = split(url) ?: return null
    return UrlIdentity(
        providerCode = PROVIDER_CODE,
        identity = link.identity,
        canonicalUrl = link.canonicalUrl,
    )
}
