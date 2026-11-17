package io.eksoda.engine.providers

import io.eksoda.engine.FormatChanged
import io.eksoda.engine.UnsupportedProvider
import io.eksoda.engine.UnsupportedUrlFormat
import io.eksoda.engine.canonical.CanonicalReceipt
import io.eksoda.engine.fetch.FetchedDocument
import io.eksoda.engine.fetch.Fetcher
import io.eksoda.engine.text.fingerprint
import io.eksoda.engine.url.ParsedUrl
import io.eksoda.engine.url.parseSubmittedUrl
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

// Provider contract and shared building blocks.
//
// A provider recognizes its URLs, fetches its pages through the fetcher, parses them with one of
// its *layouts*, and sanitizes saved pages for bug reports. Layouts make format changes cheap:
// when a provider changes its page, add a new layout next to the old one (old fixtures keep
// passing) and register it first.

/** What a provider learns from a URL before fetching (pre-fetch dedup). */
public data class UrlIdentity(
    val providerCode: String,
    /** E.g. "GR:094062259:<UID>", "entersoft:<uuid>", "fhm:<register>:<seq>". */
    val identity: String,
    /** The receipt link: a credential. Never log it. */
    val canonicalUrl: String,
    /** Known before fetching only for some providers. */
    val issuerCountry: String? = null,
    val issuerVat: String? = null,
    val uid: String? = null,
) {
    val urlFingerprint: String get() = fingerprint(canonicalUrl)
}

private const val MASK = "********"
private const val MIN_SECRET_LENGTH = 16

/** Hides the secret part of a receipt URL: the query, else the last path segment. */
public fun maskUrl(url: String): String {
    if ('?' in url &&
        url.substringAfter('?').isNotEmpty()
    ) {
        return "${url.substringBefore('?')}?$MASK"
    }
    val trimmed = url.trimEnd('/')
    val head = if ('/' in trimmed) trimmed.substringBeforeLast('/') else ""
    val last = trimmed.substringAfterLast('/')
    return if (head.isNotEmpty() && last.length >= MIN_SECRET_LENGTH) "$head/$MASK" else url
}

/** One known page format of a provider. */
public interface Layout {
    /** Also the parser version stored with each receipt, e.g. "impact/1". */
    public val name: String

    public fun matches(document: Document): Boolean

    public fun parse(document: Document, identity: UrlIdentity): CanonicalReceipt
}

public fun parseWithLayouts(
    document: Document,
    identity: UrlIdentity,
    layouts: List<Layout>,
): CanonicalReceipt {
    val layout =
        layouts.firstOrNull { it.matches(document) }
            ?: throw FormatChanged("no known layout matches the page")
    return layout.parse(document, identity)
}

/** Parses a page the way BeautifulSoup(html, "lxml") is used by the parsers. */
public fun parseHtml(html: String): Document = Jsoup.parse(html)

/** A receipt source: its hosts plus the functions of its urls/parser/sanitize files. */
public class Provider(
    public val code: String,
    public val hosts: Set<String>,
    /** Newest first. */
    public val layouts: List<Layout>,
    public val recognize: (ParsedUrl) -> UrlIdentity?,
    public val parseHtml: (String, UrlIdentity) -> CanonicalReceipt,
    public val sanitize: (String) -> SanitizeResult,
    /** Only for sources that need more than one GET of the canonical URL (Entersoft). */
    public val fetchDocument: (suspend (UrlIdentity, Fetcher) -> FetchedDocument)? = null,
) {
    public suspend fun fetch(identity: UrlIdentity, fetcher: Fetcher): FetchedDocument =
        fetchDocument?.invoke(identity, fetcher)
            ?: fetcher.get(parseSubmittedUrl(identity.canonicalUrl), allowedHosts = hosts)

    public fun parse(document: FetchedDocument, identity: UrlIdentity): CanonicalReceipt =
        parseHtml(document.text(), identity)
}

/** Maps hosts to providers. */
public class ProviderRegistry(providers: Iterable<Provider> = emptyList()) {
    private val byCode = LinkedHashMap<String, Provider>()
    private val byHost = LinkedHashMap<String, Provider>()

    init {
        for (provider in providers) {
            require(provider.code !in byCode) {
                "provider '${provider.code}' is already registered"
            }
            byCode[provider.code] = provider
            for (host in provider.hosts) {
                require(host !in byHost) { "host '$host' is already registered" }
                byHost[host] = provider
            }
        }
    }

    public val codes: List<String> get() = byCode.keys.sorted()

    public fun get(code: String): Provider =
        byCode[code] ?: throw UnsupportedProvider("no provider with code '$code'")

    /**
     * Validates a submitted URL and finds its provider ([io.eksoda.engine.InvalidUrl],
     * [UnsupportedProvider] or [UnsupportedUrlFormat] otherwise).
     */
    public fun resolveUrl(raw: Any?): Pair<Provider, UrlIdentity> {
        val url = parseSubmittedUrl(raw)
        val provider = byHost[url.host] ?: throw UnsupportedProvider("host ${url.host}")
        val identity =
            provider.recognize(url)
                ?: throw UnsupportedUrlFormat("unrecognized ${provider.code} URL")
        return provider to identity
    }
}
