package io.eksoda.engine.providers.entersoft

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import io.eksoda.engine.FormatChanged
import io.eksoda.engine.ReceiptNotFound
import io.eksoda.engine.TestData
import io.eksoda.engine.fetch.FetchedDocument
import io.eksoda.engine.fetch.Fetcher
import io.eksoda.engine.providers.REGISTRY
import io.eksoda.engine.url.ParsedUrl
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test

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

    private fun fetcher(document: FetchedDocument) = object : Fetcher {
        override suspend fun get(
            url: ParsedUrl,
            allowedHosts: Set<String>,
            acceptStatuses: Set<Int>,
            contentTypes: Set<String>,
        ): FetchedDocument {
            assertThat(allowedHosts).isEqualTo(HOSTS)
            requests += url.geturl() to acceptStatuses
            return if (requests.size == 1) FetchedDocument(shell.toByteArray()) else document
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
