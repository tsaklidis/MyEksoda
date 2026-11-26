package io.eksoda.engine.providers.entersoft

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsExactly
import assertk.assertions.doesNotContain
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNull
import io.eksoda.engine.FormatChanged
import io.eksoda.engine.InconsistentSource
import io.eksoda.engine.ReceiptNotFound
import io.eksoda.engine.TestData
import io.eksoda.engine.UnsupportedUrlFormat
import io.eksoda.engine.canonical.Line
import io.eksoda.engine.canonical.PriceBasis
import io.eksoda.engine.fetch.FetchedDocument
import io.eksoda.engine.fetch.Fetcher
import io.eksoda.engine.providers.REGISTRY
import io.eksoda.engine.url.ParsedUrl
import io.eksoda.engine.url.parseSubmittedUrl
import io.eksoda.engine.validation.validate
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class EntersoftTest {
    private val directory = TestData.fixtures.resolve("entersoft")
    private val shell = directory.resolve("shell.html").readText()
    private val receipt = directory.resolve("lidl_21_lines.html").readText()
    private val identity = REGISTRY.resolveUrl(
        Json.parseToJsonElement(directory.resolve("lidl_21_lines.meta.json").readText())
            .jsonObject.getValue("url").jsonPrimitive.content,
    ).second

    /** Made-up secrets in the shapes the sanitizer looks for. */
    private val documentId = "1b4e28ba-2fa1-41d2-883f-0016d3cca427"
    private val token = "Ab12Cd34ef"

    /** (URL, accepted statuses) of each request: the shell page, then the framed document. */
    private val requests = mutableListOf<Pair<String, Set<Int>>>()

    private fun fetcher(document: FetchedDocument, shell: String = this.shell) = object : Fetcher {
        override suspend fun get(
            url: ParsedUrl,
            allowedHosts: Set<String>,
            acceptStatuses: Set<Int>,
            contentTypes: Set<String>,
        ): FetchedDocument {
            assertThat(allowedHosts).isEqualTo(HOSTS)
            requests += url.geturl() to acceptStatuses
            val page = url.path.startsWith("/edocuments/")
            return if (page) FetchedDocument(shell.toByteArray()) else document
        }
    }

    @Test
    fun `the receipt is the document the shell page frames`(): Unit = runBlocking {
        val fetched = ENTERSOFT.fetch(identity, fetcher(FetchedDocument(receipt.toByteArray())))
        assertThat(fetched.text()).isEqualTo(receipt)
        assertThat(requests.map { it.second }).isEqualTo(listOf(emptySet(), setOf(418)))
        assertThat(requests.first().first).isEqualTo(identity.canonicalUrl)
        assertThat(requests.last().first).contains("https://e-invoicing.gr/api/GetInvoice?")
    }

    @Test
    fun `older links are read from the document their shell page frames`(): Unit = runBlocking {
        val legacyShell = directory.resolve("shell_legacy.html").readText()
        val link = "https://E-Invoicing.gr:443/edocuments/ViewInvoice/?ct=PEPPOL" +
            "&id=${FAKE_LEGACY_ID.lowercase()}&s=A&h=0x0xxx00"
        val (provider, legacy) = REGISTRY.resolveUrl(link)
        assertThat(provider).isEqualTo(ENTERSOFT)
        assertThat(legacy.identity).isEqualTo("entersoft:$FAKE_LEGACY_ID")
        assertThat(legacy.canonicalUrl).isEqualTo(
            "https://e-invoicing.gr/edocuments/ViewInvoice?ct=PEPPOL&id=$FAKE_LEGACY_ID&s=A&h=0x0xxx00",
        )
        val document = FetchedDocument(receipt.toByteArray())
        assertThat(ENTERSOFT.fetch(legacy, fetcher(document, legacyShell)).text())
            .isEqualTo(receipt)
        assertThat(requests.last().first).contains("id=$FAKE_LEGACY_ID&source=A")

        // The content type and source in another case are the same receipt.
        val lower = REGISTRY.resolveUrl(
            legacy.canonicalUrl.replace("ct=PEPPOL", "ct=peppol").replace("s=A", "s=a"),
        ).second
        assertThat(fetchDocument(lower, fetcher(document, legacyShell)).text()).isEqualTo(receipt)

        // The shell frames another id, token, source or content type: not this receipt.
        for (other in listOf("id=${"1".repeat(40)}", "h=0x0xxx01", "s=B", "ct=UBL")) {
            val (key, _) = other.split('=')
            val otherLink = legacy.canonicalUrl.replace(Regex("""(?<=[?&])$key=[^&]+"""), other)
            val otherIdentity = REGISTRY.resolveUrl(otherLink).second
            assertFailure { fetchDocument(otherIdentity, fetcher(document, legacyShell)) }
                .isInstanceOf<InconsistentSource>()
        }

        // Current links: another id or token, or the older frame, which names neither.
        val base = "https://e-invoicing.gr/edocuments/ViewInvoice/-1"
        val cases = listOf(
            "$base/1b4e28ba-2fa1-41d2-883f-0016d3cca427_0xxx0000" to shell,
            "$base/${FAKE_UUID}_0xxx0001" to shell,
            identity.canonicalUrl to legacyShell,
        )
        for ((link, page) in cases) {
            val other = REGISTRY.resolveUrl(link).second
            assertFailure { fetchDocument(other, fetcher(document, page)) }
                .isInstanceOf<InconsistentSource>()
        }
        val elsewhere = "https://example.com/edocuments/ViewInvoice/-1/${FAKE_UUID}_0xxx0000"
        assertThat(split(parseSubmittedUrl(elsewhere))).isNull()
    }

    @Test
    fun `only well-formed older links are receipts`() {
        val base = "https://e-invoicing.gr/edocuments/ViewInvoice"
        val id = FAKE_LEGACY_ID
        val refused = listOf(
            "$base?ct=PEPPOL&id=$id&s=A",
            "$base?ct=PEPPOL&id=$id&s=A&x=1",
            "$base/-1/not-a-document",
            "$base?ct=PEPPOL&id=$id&s=A&h=0x0xxx00&x=1",
            "$base?ct=PEPPOL&id=$id&s=A&h=0x0xxx00&h=0x0xxx00",
            "$base?ct=PEPPOL&id=$id&s=A&&h=0x0xxx00",
            "$base?ct=PEPPOL&id=${id.drop(1)}&s=A&h=0x0xxx00",
            "$base?ct=PEPPOL&id=$id&s=A&h=0x0",
            "$base/-1?ct=PEPPOL&id=$id&s=A&h=0x0xxx00",
            "$base/-1/${FAKE_UUID}_0xxx0000?ct=PEPPOL",
        )
        for (link in refused) {
            assertFailure { REGISTRY.resolveUrl(link) }.isInstanceOf<UnsupportedUrlFormat>()
        }
    }

    @Test
    fun `the issuer VAT is the nine digits that start its line`() {
        val sklavenitis = parseHtml(
            directory.resolve("sklavenitis_12_lines.html").readText(),
            identity,
        )
        assertThat(sklavenitis.issuer.vatNumber).isEqualTo("800764388")
        // Prefixed, grouped, or followed by more digits after a space.
        for (printed in listOf("EL 093 683.423", "ΕΛ - 093683423", "093683423 2141009999")) {
            val line = receipt.replace("Α.Φ.Μ: 093683423", "Α.Φ.Μ: $printed")
            assertThat(parseHtml(line, identity).issuer.vatNumber, name = printed)
                .isEqualTo("093683423")
        }
        // Ten digits, grouped or not, are not a VAT followed by something else.
        for (printed in listOf("0936834231", "093 683 423 1")) {
            val line = receipt.replace("Α.Φ.Μ: 093683423", "Α.Φ.Μ: $printed")
            assertFailure { parseHtml(line, identity) }.isInstanceOf<FormatChanged>()
        }
        val notAVat = receipt.replace("Α.Φ.Μ: 093683423", "Α.Φ.Μ: 0936834")
        assertThat(notAVat).isNotEqualTo(receipt)
        assertFailure { parseHtml(notAVat, identity) }.isInstanceOf<FormatChanged>()
    }

    @Test
    fun `a 418 with the not-found page is a missing receipt`(): Unit = runBlocking {
        val notFound = directory.resolve("not_found.html").readBytes()
        assertFailure { fetchDocument(identity, fetcher(FetchedDocument(notFound, status = 418))) }
            .isInstanceOf<ReceiptNotFound>()
    }

    @Test
    fun `a 418 with another page is a format change`(): Unit = runBlocking {
        val other = FetchedDocument("<html>maintenance</html>".toByteArray(), status = 418)
        assertFailure { fetchDocument(identity, fetcher(other)) }.isInstanceOf<FormatChanged>()
    }

    @Test
    fun `sanitizing replaces the document id and token in links and frame parameters`() {
        val link = "https://e-invoicing.gr/edocuments/ViewInvoice/-1/${documentId}_$token"
        val raw = shell
            .replace(FAKE_UUID, documentId)
            .replace("hashToken=0xxx0000", "hashToken=$token")
            .replace("data-download-token=\"\"", "data-download-token=\"download-secret\"")
            .replace("<body ", "<a href=\"$link\">receipt</a><body ")
        val result = sanitizeHtml(raw)
        for (secret in listOf(documentId, token, "download-secret")) {
            assertThat(result.html).doesNotContain(secret)
        }
        // Same shapes: the fake id, and digits → 0, letters → x.
        assertThat(result.html).contains("${FAKE_UUID}_xx00xx00xx")
        assertThat(result.html).contains("intRefDocID=$FAKE_UUID")
        assertThat(result.html).contains("hashToken=xx00xx00xx")
        assertThat(result.replaced)
            .isEqualTo(mapOf("download_token" to 1, "url_access_token" to 2))
    }

    @Test
    fun `sanitizing replaces the id and token of older links`() {
        val id = "A1B2C3D4E5F60718293A4B5C6D7E8F9012345678"
        val token = "Ab12Cd34"
        val link = "https://e-invoicing.gr/edocuments/ViewInvoice?ct=PEPPOL&id=$id&s=A&h=$token"
        val returnUrl = "/login?ReturnUrl=%2Fedocuments%2FViewInvoice%3Fct%3DPEPPOL%26id%3D$id" +
            "%26s%3DA%26h%3D$token"
        val extras = "<a href=\"$link\">receipt</a><a href=\"$returnUrl\">sign in</a>" +
            "<img src=\"/logo.png?w=120&h=40\">"
        val raw = directory.resolve("shell_legacy.html").readText()
            .replace("id=$FAKE_LEGACY_ID", "id=$id")
            .replace("hashToken=0x0xxx00", "hashToken=$token")
            .replace("<body ", "$extras<body ")
        val result = sanitizeHtml(raw)
        assertThat(result.html).doesNotContain(id)
        assertThat(result.html).doesNotContain(token)
        assertThat(result.html).contains("id=$FAKE_LEGACY_ID&amp;s=A&amp;h=xx00xx00")
        assertThat(result.html).contains("id%3D$FAKE_LEGACY_ID%26s%3DA%26h%3Dxx00xx00")
        assertThat(result.html).contains("id=$FAKE_LEGACY_ID&amp;source=A&amp;hashToken=xx00xx00")
        // Other addresses' parameters are not tokens.
        assertThat(result.html).contains("logo.png?w=120&amp;h=40")
        assertThat(result.replaced).isEqualTo(mapOf("url_access_token" to 2))
    }

    @Test
    fun `unit prices are read before VAT when that is how the receipt prints them`() {
        val masoutis = parseHtml(directory.resolve("masoutis_3_lines.html").readText(), identity)
        assertThat(masoutis.lines.map { it.unitPriceBasis }.distinct())
            .containsExactly(PriceBasis.NET)
        assertThat(validate(masoutis)).isEmpty()
        // LIDL prints them with VAT.
        assertThat(parseHtml(receipt, identity).lines.map { it.unitPriceBasis }.distinct())
            .containsExactly(PriceBasis.GROSS)
    }

    @Test
    fun `a known issuer's basis comes first, then the receipt's lines`() {
        // 3,90 for 4,40 at 13% fits before VAT; 4,40 for 4,40 fits with VAT.
        fun priced(price: String) = listOf(
            Line(
                lineNumber = 1,
                description = "Item",
                gross = BigDecimal("4.40"),
                quantity = BigDecimal.ONE,
                unitPrice = BigDecimal(price),
                vatRate = BigDecimal("13"),
            ),
        )
        val masoutis = NET_PRICED_ISSUERS.single()
        assertThat(unitPriceBasis(masoutis, priced("3.90"))).isEqualTo(PriceBasis.NET)
        // Known: its prices are read before VAT even where a line would fit with VAT.
        assertThat(unitPriceBasis(masoutis, priced("4.40"))).isEqualTo(PriceBasis.NET)
        // Unknown issuers: the lines decide, with VAT unless they show otherwise.
        assertThat(unitPriceBasis("000000000", priced("3.90"))).isEqualTo(PriceBasis.NET)
        assertThat(unitPriceBasis("000000000", priced("4.40"))).isEqualTo(PriceBasis.GROSS)
        assertThat(unitPriceBasis("000000000", emptyList())).isEqualTo(PriceBasis.GROSS)
    }

    @Test
    fun `sanitizing replaces the customer and the provider signature`() {
        val name = "Γιάννης Παπαδόπουλος"
        val signature = "A1B2C3D4E5F6A7B8C9D0"
        val raw = receipt
            .replace("XXXXXXX XXXXXXXX", name)
            .replace("0000XX00XXX0X00XXXXX000000XX0X00XX00XXX0", signature)
        val result = sanitizeHtml(raw)
        assertThat(result.html).doesNotContain(name)
        assertThat(result.html).doesNotContain(signature)
        assertThat(result.replaced).isEqualTo(mapOf("customer" to 1, "signature" to 1))
        // The receipt still parses to the same values.
        assertThat(parseHtml(result.html, identity).toJson())
            .isEqualTo(parseHtml(receipt, identity).toJson())
    }
}
