package io.eksoda.engine.providers

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.hasMessage
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isSameInstanceAs
import io.eksoda.engine.FormatChanged
import io.eksoda.engine.UnsupportedProvider
import io.eksoda.engine.UnsupportedUrlFormat
import io.eksoda.engine.fetch.FetchedDocument
import io.eksoda.engine.fetch.Fetcher
import io.eksoda.engine.providers.impact.IMPACT
import io.eksoda.engine.text.fingerprint
import io.eksoda.engine.url.ParsedUrl
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

class BaseTest {
    /** A receipt link of the Impact shape with made-up ids. */
    private val receiptUrl =
        "https://einvoice.impact.gr/p/EL000000000/${"A".repeat(40)}/${"B".repeat(32)}"

    private fun provider(code: String, vararg hosts: String) = Provider(
        code = code,
        hosts = hosts.toSet(),
        layouts = emptyList(),
        recognize = { null },
        parseHtml = { _, _ -> error("not parsed") },
        sanitize = { error("not sanitized") },
    )

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
