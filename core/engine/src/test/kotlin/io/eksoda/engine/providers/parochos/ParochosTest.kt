package io.eksoda.engine.providers.parochos

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import io.eksoda.engine.Engine
import io.eksoda.engine.InconsistentSource
import io.eksoda.engine.ReceiptNotFound
import io.eksoda.engine.TestData
import io.eksoda.engine.UnsupportedProvider
import io.eksoda.engine.UnsupportedUrlFormat
import io.eksoda.engine.fetch.FetchedDocument
import io.eksoda.engine.fetch.Fetcher
import io.eksoda.engine.fetch.XML_TYPES
import io.eksoda.engine.providers.REGISTRY
import io.eksoda.engine.providers.UrlIdentity
import io.eksoda.engine.providers.epsilon.FAKE_HEX_ID
import io.eksoda.engine.providers.epsilon.FAKE_UUID
import io.eksoda.engine.url.ParsedUrl
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import io.eksoda.engine.providers.epsilon.PROVIDER_CODE as EPSILON_CODE

class ParochosTest {
    private val host = "srv2.parochos.gr"
    private val link = "https://$host/fd/$FAKE_HEX_ID:2"
    private val fixture = TestData.fixtures.resolve("parochos/pharmacy_2_lines.xml").readText()

    private fun identity(url: String): UrlIdentity = REGISTRY.resolveUrl(url).second

    private fun parse(xml: String = fixture, url: String = link) =
        REGISTRY.resolveUrl(url).let { (provider, identity) -> provider.parseHtml(xml, identity) }

    /** A fetcher that answers every request with [body] and records what was asked. */
    private class RecordingFetcher(private val body: String) : Fetcher {
        val requests = mutableListOf<Triple<String, Set<String>, Set<String>>>()

        override suspend fun get(
            url: ParsedUrl,
            allowedHosts: Set<String>,
            acceptStatuses: Set<Int>,
            contentTypes: Set<String>,
        ): FetchedDocument {
            requests += Triple(url.geturl(), allowedHosts, contentTypes)
            return FetchedDocument(body.toByteArray())
        }
    }

    // --- URLs -----------------------------------------------------------------------------------

    @Test
    fun `the QR link and the viewer link are the same receipt on every Parochos host`() {
        for (server in HOSTS) {
            val (provider, short) = REGISTRY.resolveUrl(
                "https://${server.uppercase()}:443/fd/${"AB".repeat(16)}:2/",
            )
            assertThat(provider.code).isEqualTo(PROVIDER_CODE)
            assertThat(short.canonicalUrl).isEqualTo("https://$server/fd/${"ab".repeat(16)}:2")
            assertThat(short.identity)
                .isEqualTo("parochos:abababab-abab-abab-abab-abababababab")
            val viewer = identity("https://$server/DocViewer/ABABABAB-ABAB-ABAB-ABAB-ABABABABABAB")
            assertThat(viewer.identity).isEqualTo(short.identity)
            assertThat(viewer.canonicalUrl)
                .isEqualTo("https://$server/DocViewer/abababab-abab-abab-abab-abababababab")
            // Neither id is the myDATA UID, and the link names no issuer.
            assertThat(short.uid).isNull()
            assertThat(short.issuerVat).isNull()
        }
        assertThat(identity("https://$host/fd/$FAKE_HEX_ID").canonicalUrl)
            .isEqualTo("https://$host/fd/$FAKE_HEX_ID")
    }

    @Test
    fun `other addresses are not receipt links`() {
        for (url in listOf(
            "$link?x=1",
            "https://$host/fd/${"0".repeat(31)}:2",
            "https://$host/fd/$FAKE_HEX_ID:2/extra",
            "https://$host/DocViewer/${FAKE_UUID.dropLast(1)}",
            "https://$host/filedocument/GetInvoiceDocDetailed/$FAKE_UUID",
            "https://$host/",
        )) {
            assertFailure { REGISTRY.resolveUrl(url) }.isInstanceOf<UnsupportedUrlFormat>()
        }
        // Only the receipt servers: not the company's site, nor a lookalike host.
        val others = listOf(
            "parochos.gr",
            "www.parochos.gr",
            "srv4.parochos.gr",
            "srv2.parochos.gr.example.com",
        )
        for (other in others) {
            assertFailure { REGISTRY.resolveUrl("https://$other/fd/$FAKE_HEX_ID:2") }
                .isInstanceOf<UnsupportedProvider>()
        }
    }

    // --- Parsing --------------------------------------------------------------------------------

    @Test
    fun `the pharmacy receipt is read from Epsilon's XML under this provider`() {
        val receipt = parse()
        assertThat(receipt.providerCode).isEqualTo(PROVIDER_CODE)
        assertThat(receipt.parserVersion).isEqualTo(PARSER_VERSION)
        assertThat(receipt.identity.uid).isEqualTo("4F65400F9F69EBF79C7C1EC810D21793AE25D7DB")
        assertThat(receipt.identity.mark).isEqualTo("400016183808125")
        assertThat(receipt.totals.gross.toPlainString()).isEqualTo("6.15")
        assertThat(receipt.lines.map { it.gross.toPlainString() }).containsExactly("4.01", "2.14")
        assertThat(receipt.lines.map { it.vatRate?.toPlainString() }).containsExactly("6", "6")
        // The XML has no name: the VAT number's entry names the pharmacy.
        assertThat(receipt.issuer.vatNumber).isEqualTo("081258114")
        assertThat(receipt.issuer.legalName).isEqualTo(ISSUER_NAMES.getValue("081258114"))
    }

    @Test
    fun `an issuer without a known name is read with an empty name`() {
        val other = fixture.replace("<vatNumber>081258114<", "<vatNumber>999999999<")
        assertThat(parse(other).issuer.legalName).isEqualTo("")
        val named = other.replace("<country>GR</country>", "<country>GR</country><name>SHOP</name>")
        assertThat(parse(named).issuer.legalName).isEqualTo("SHOP")
    }

    @Test
    fun `an XML for another document or another site is refused`() {
        val other = fixture.replace("/fd/$FAKE_HEX_ID", "/fd/" + "1".repeat(32))
        assertFailure { parse(other) }.isInstanceOf<InconsistentSource>()
        // Another Parochos server is not where this receipt lives.
        assertFailure { parse(url = "https://srv3.parochos.gr/fd/$FAKE_HEX_ID:2") }
            .isInstanceOf<InconsistentSource>()
        // The same platform's XML printing an Epsilon Digital link is not this receipt either.
        val epsilonHost = "https://epsilondigital-3rd.epsilonnet.gr/"
        val epsilon = fixture.replace("https://$host/", epsilonHost)
        assertFailure { parse(epsilon) }.isInstanceOf<InconsistentSource>()
        // And an Epsilon Digital link is Epsilon's, which refuses this XML.
        val epsilonLink = "${epsilonHost}fd/$FAKE_HEX_ID:2"
        assertThat(REGISTRY.resolveUrl(epsilonLink).first.code).isEqualTo(EPSILON_CODE)
        assertFailure { parse(fixture, epsilonLink) }.isInstanceOf<InconsistentSource>()
    }

    @Test
    fun `an unknown document is not found`() {
        assertFailure { parse("Document not found.") }.isInstanceOf<ReceiptNotFound>()
    }

    // --- Fetching -------------------------------------------------------------------------------

    @Test
    fun `the engine fetches the XML from the link's own Parochos host only`(): Unit = runBlocking {
        val fetcher = RecordingFetcher(fixture)
        val engine = Engine(fetcher)
        val parsed = engine.read(engine.resolve(link))
        assertThat(fetcher.requests).containsExactly(
            Triple(
                "https://$host/filedocument/GetInvoiceDocDetailed/$FAKE_UUID",
                setOf(host),
                XML_TYPES,
            ),
        )
        assertThat(parsed.receipt.providerCode).isEqualTo(PROVIDER_CODE)
        assertThat(parsed.receipt.totals.gross.toPlainString()).isEqualTo("6.15")
    }

    // --- Sanitizing -----------------------------------------------------------------------------

    @Test
    fun `sanitizing replaces the authentication code and the document id`() {
        val raw = fixture
            .replace(FAKE_HEX_ID, "9f3c2a1b8e7d6c5b4a39281706f5e4d3")
            .replace(
                "0XX00000XXX000000000XX0XX000000XX0X0000X",
                "8AB12CD34EF56AB12CD34EF56AB12CD34EF56AB1",
            )
        val result = sanitizeXml(raw)
        assertThat(result.html.contains("9f3c2a1b8e7d6c5b4a39281706f5e4d3")).isEqualTo(false)
        assertThat(result.html.contains("8AB12CD34EF56")).isEqualTo(false)
        assertThat(result.replaced).isEqualTo(mapOf("signature" to 1, "url_document_id" to 1))
        assertThat(parse(result.html).identity.mark).isEqualTo("400016183808125")
    }
}
