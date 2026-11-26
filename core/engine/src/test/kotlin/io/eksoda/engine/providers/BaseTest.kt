package io.eksoda.engine.providers

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.hasMessage
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isSameInstanceAs
import assertk.assertions.isTrue
import io.eksoda.engine.FormatChanged
import io.eksoda.engine.ProviderUnreachable
import io.eksoda.engine.UnsupportedProvider
import io.eksoda.engine.UnsupportedUrlFormat
import io.eksoda.engine.fetch.FetchedDocument
import io.eksoda.engine.fetch.Fetcher
import io.eksoda.engine.providers.impact.IMPACT
import io.eksoda.engine.text.fingerprint
import io.eksoda.engine.url.ParsedUrl
import io.eksoda.engine.url.parseSubmittedUrl
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

class BaseTest {
    /** A receipt link of the Impact shape with made-up ids. */
    private val receiptUrl =
        "https://einvoice.impact.gr/p/EL000000000/${"A".repeat(40)}/${"B".repeat(32)}"

    private val shops = Regex("""^shop-[a-z]+\.example\.gr$""")

    /** Fetches the link from its own host only, as a provider with a host pattern does. */
    private val ownHostFetch: suspend (UrlIdentity, Fetcher) -> FetchedDocument = { link, f ->
        val url = parseSubmittedUrl(link.canonicalUrl)
        f.get(url, allowedHosts = setOf(url.host))
    }

    private fun provider(
        code: String,
        vararg hosts: String,
        pattern: Regex? = null,
        recognize: (ParsedUrl) -> UrlIdentity? = { null },
    ) = Provider(
        code = code,
        hosts = hosts.toSet(),
        layouts = emptyList(),
        recognize = recognize,
        parseHtml = { _, _ -> error("not parsed") },
        sanitize = { error("not sanitized") },
        fetchDocument = ownHostFetch.takeIf { pattern != null },
        hostPattern = pattern,
    )

    /** A fetcher that fails every request with [failure]. */
    private fun failing(failure: Exception) = object : Fetcher {
        override suspend fun get(
            url: ParsedUrl,
            allowedHosts: Set<String>,
            acceptStatuses: Set<Int>,
            contentTypes: Set<String>,
        ): FetchedDocument = throw failure
    }

    private fun linkOf(code: String): (ParsedUrl) -> UrlIdentity = { url ->
        UrlIdentity(providerCode = code, identity = url.host, canonicalUrl = url.geturl())
    }

    @Test
    fun `masking hides the query, else a long last path segment`() {
        assertThat(maskUrl("https://shop.example.gr/r?id=123&k=abc"))
            .isEqualTo("https://shop.example.gr/r?********")
        val masked = "https://einvoice.impact.gr/p/EL000000000/${"A".repeat(40)}/********"
        assertThat(maskUrl(receiptUrl)).isEqualTo(masked)
        assertThat(maskUrl("$receiptUrl/")).isEqualTo(masked)
    }

    @Test
    fun `masking keeps links without a secret-looking part`() {
        // Short last segment, an empty query, no path at all.
        assertThat(
            maskUrl("https://shop.example.gr/about"),
        ).isEqualTo("https://shop.example.gr/about")
        assertThat(maskUrl("https://shop.example.gr/r?")).isEqualTo("https://shop.example.gr/r?")
        val bare = "x".repeat(20)
        assertThat(maskUrl(bare)).isEqualTo(bare)
    }

    @Test
    fun `a URL fingerprint is the fingerprint of the canonical link`() {
        val identity = REGISTRY.resolveUrl(receiptUrl).second
        assertThat(identity.urlFingerprint).isEqualTo(fingerprint(identity.canonicalUrl))
    }

    @Test
    fun `a page that no layout matches is a format change`() {
        val identity = REGISTRY.resolveUrl(receiptUrl).second
        assertFailure { parseWithLayouts(parseHtml("<html></html>"), identity, emptyList()) }
            .isInstanceOf<FormatChanged>()
    }

    @Test
    fun `a registry refuses a provider code or a host twice`() {
        assertFailure { ProviderRegistry(listOf(provider("a", "a.example"), provider("a"))) }
            .isInstanceOf<IllegalArgumentException>()
            .hasMessage("provider 'a' is already registered")
        assertFailure {
            ProviderRegistry(listOf(provider("a", "x.example"), provider("b", "x.example")))
        }.isInstanceOf<IllegalArgumentException>()
            .hasMessage("host 'x.example' is already registered")
    }

    @Test
    fun `a registry lists its codes sorted and finds providers by code`() {
        val b = provider("b", "b.example")
        val registry = ProviderRegistry(listOf(b, provider("a", "a.example")))
        assertThat(registry.codes).isEqualTo(listOf("a", "b"))
        assertThat(registry.get("b")).isSameInstanceAs(b)
        assertFailure { registry.get("c") }.isInstanceOf<UnsupportedProvider>()
    }

    @Test
    fun `a URL resolves only on a registered host and in a recognized form`() {
        assertFailure { REGISTRY.resolveUrl("https://shop.example.gr/r/1") }
            .isInstanceOf<UnsupportedProvider>()
        assertFailure { REGISTRY.resolveUrl("https://einvoice.impact.gr/about") }
            .isInstanceOf<UnsupportedUrlFormat>()
    }

    @Test
    fun `a host matching a provider's pattern resolves to it`() {
        val listed = provider("listed", "receipts.example.gr", recognize = linkOf("listed"))
        val patterned = provider(
            "patterned",
            "shop-b.example.gr",
            pattern = shops,
            recognize = linkOf("patterned"),
        )
        val registry = ProviderRegistry(listOf(patterned, listed))
        assertThat(registry.resolveUrl("https://receipts.example.gr/r").first)
            .isSameInstanceAs(listed)
        assertThat(registry.resolveUrl("https://shop-b.example.gr/r").first)
            .isSameInstanceAs(patterned)
        assertThat(registry.resolveUrl("https://shop-new.example.gr/r").first)
            .isSameInstanceAs(patterned)
        assertFailure { registry.resolveUrl("https://shop-1.example.gr/r") }
            .isInstanceOf<UnsupportedProvider>()
        assertThat(patterned.servesHost("shop-new.example.gr")).isTrue()
        assertThat(listed.servesHost("shop-new.example.gr")).isFalse()
    }

    @Test
    fun `one host belongs to one provider, patterns included`() {
        // A listed host that another provider's pattern matches is refused at startup.
        val listed = provider("l", "shop-x.example.gr")
        assertFailure { ProviderRegistry(listOf(provider("p", pattern = shops), listed)) }
            .isInstanceOf<IllegalArgumentException>()
            .hasMessage("host 'shop-x.example.gr' of 'l' matches the pattern of 'p'")
        // A host two patterns match goes to neither.
        val wide = provider("wide", pattern = Regex("""^shop-.+\.example\.gr$"""))
        val registry = ProviderRegistry(listOf(provider("p", pattern = shops), wide))
        assertFailure { registry.resolveUrl("https://shop-x.example.gr/r") }
            .isInstanceOf<UnsupportedProvider>()
            .hasMessage("unsupported_provider: host shop-x.example.gr matches several patterns")
    }

    @Test
    fun `a provider with a host pattern fetches its documents itself`() {
        assertFailure {
            Provider(
                code = "p",
                hosts = emptySet(),
                layouts = emptyList(),
                recognize = { null },
                parseHtml = { _, _ -> error("not parsed") },
                sanitize = { error("not sanitized") },
                hostPattern = shops,
            )
        }.isInstanceOf<IllegalArgumentException>()
    }

    @Test
    fun `a pattern host that does not exist is refused, a listed one is retried`(): Unit =
        runBlocking {
            val patterned = provider("p", "shop-b.example.gr", pattern = shops)
            fun link(host: String) = linkOf("p")(parseSubmittedUrl("https://$host/r"))
            val noSuchHost = ProviderUnreachable("dns", unknownHost = true)
            val broken = ProviderUnreachable("reset")
            assertFailure { patterned.fetch(link("shop-new.example.gr"), failing(noSuchHost)) }
                .isInstanceOf<UnsupportedProvider>()
            // A host known to serve receipts, or a connection that broke, is a network failure.
            assertFailure { patterned.fetch(link("shop-b.example.gr"), failing(noSuchHost)) }
                .isInstanceOf<ProviderUnreachable>()
            assertFailure { patterned.fetch(link("shop-new.example.gr"), failing(broken)) }
                .isInstanceOf<ProviderUnreachable>()
        }

    @Test
    fun `a provider without its own fetch gets the canonical link from its hosts`(): Unit =
        runBlocking {
            val requests = mutableListOf<Pair<String, Set<String>>>()
            val fetcher = object : Fetcher {
                override suspend fun get(
                    url: ParsedUrl,
                    allowedHosts: Set<String>,
                    acceptStatuses: Set<Int>,
                    contentTypes: Set<String>,
                ): FetchedDocument {
                    requests += url.geturl() to allowedHosts
                    return FetchedDocument("<html></html>".toByteArray())
                }
            }
            val identity = REGISTRY.resolveUrl(receiptUrl).second
            IMPACT.fetch(identity, fetcher)
            assertThat(requests).isEqualTo(listOf(identity.canonicalUrl to IMPACT.hosts))
        }
}
