package io.eksoda.engine.providers.epsilon

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import io.eksoda.engine.Engine
import io.eksoda.engine.FormatChanged
import io.eksoda.engine.InconsistentSource
import io.eksoda.engine.ReceiptNotFound
import io.eksoda.engine.TestData
import io.eksoda.engine.UnsupportedProvider
import io.eksoda.engine.fetch.FetchedDocument
import io.eksoda.engine.fetch.Fetcher
import io.eksoda.engine.fetch.XML_TYPES
import io.eksoda.engine.providers.REGISTRY
import io.eksoda.engine.providers.UrlIdentity
import io.eksoda.engine.url.ParsedUrl
import io.eksoda.engine.url.parseSubmittedUrl
import io.eksoda.engine.validation.validate
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import java.io.File

class EpsilonTest {
    private val host = "epsilondigital-sklavenitis.epsilonnet.gr"
    private val id = "2347fb74-0c1b-47b5-bde2-08df39f03c44"
    private val fixtureDir = TestData.fixtures.resolve("epsilon")
    private val fixtureName = "sklavenitis_19_lines"
    private val fixture = fixtureDir.resolve("$fixtureName.xml").readText()
    private val meta = Json.parseToJsonElement(
        fixtureDir.resolve("$fixtureName.meta.json").readText(),
    ).jsonObject
    private val fixtureIdentity = identity(meta.getValue("url").jsonPrimitive.content)

    private fun identity(url: String): UrlIdentity = REGISTRY.resolveUrl(url).second

    private fun parse(xml: String = fixture) = parseDocument(xml, fixtureIdentity)

    // --- URLs -----------------------------------------------------------------------------------

    @Test
    fun `the QR link and the viewer link are the same receipt`() {
        val short = identity("https://$host/fd/2347FB740C1B47B5BDE208DF39F03C44:155")
        val viewer = identity("https://$host/DocViewer/$id")
        assertThat(short.providerCode).isEqualTo(PROVIDER_CODE)
        assertThat(short.identity).isEqualTo("epsilon:$id")
        assertThat(viewer.identity).isEqualTo(short.identity)
        assertThat(short.canonicalUrl)
            .isEqualTo("https://$host/fd/2347fb740c1b47b5bde208df39f03c44:155")
        assertThat(viewer.canonicalUrl).isEqualTo("https://$host/DocViewer/$id")
        assertThat(identity("https://$host/fd/2347fb740c1b47b5bde208df39f03c44").canonicalUrl)
            .isEqualTo("https://$host/fd/2347fb740c1b47b5bde208df39f03c44")
    }

    @Test
    fun `other addresses are not receipt links`() {
        fun recognized(url: String) = recognize(parseSubmittedUrl(url))
        assertThat(recognized("https://$host/fd/2347fb740c1b47b5bde208df39f03c44:155?x=1")).isNull()
        assertThat(recognized("https://$host/fd/2347fb740c1b47b5:155")).isNull()
        assertThat(recognized("https://$host/filedocument/GetInvoiceDocDetailed/$id")).isNull()
        assertFailure { identity("https://epsilondigital-other.epsilonnet.gr/DocViewer/$id") }
            .isInstanceOf<UnsupportedProvider>()
    }

    @Test
    fun `the receipt is read from its detailed myDATA XML`() {
        val link = checkNotNull(split(parseSubmittedUrl(fixtureIdentity.canonicalUrl)))
        assertThat(link.documentUrl)
            .isEqualTo("https://$host/filedocument/GetInvoiceDocDetailed/$FAKE_UUID")
    }

    // --- Parsing --------------------------------------------------------------------------------

    @Test
    fun `the fixture parses to its golden file with the expected warnings`() {
        val receipt = parse()
        val actual = receipt.toJson()
        val goldenFile = fixtureDir.resolve("expected/$fixtureName.json")
        val expected = goldenFile.takeIf { it.exists() }?.let {
            Json.parseToJsonElement(it.readText())
        }
        if (expected != actual) {
            // Written for review; copy it to testdata/ when the change is intended.
            val written = File("build/golden/epsilon/$fixtureName.json")
            written.parentFile.mkdirs()
            val text = pretty.encodeToString(JsonElement.serializer(), sorted(actual))
            written.writeText("$text\n")
            assertThat(actual, name = "parsed receipt (actual in ${written.path})")
                .isEqualTo(expected)
        }
        val warnings = meta.getValue("expect").jsonObject.getValue("warnings").jsonArray
            .map { it.jsonPrimitive.content }
        assertThat(validate(receipt).map { it.code }).isEqualTo(warnings)
    }

    @Test
    fun `lines get their gross from net plus VAT and keep the myDATA codes`() {
        val receipt = parse()
        assertThat(receipt.issuer.legalName)
            .isEqualTo("ΕΛΛΗΝΙΚΕΣ ΥΠΕΡΑΓΟΡΕΣ ΣΚΛΑΒΕΝΙΤΗΣ ΑΝΩΝΥΜΗ ΕΜΠΟΡΙΚΗ ΕΤΑΙΡΕΙΑ")
        val fish = receipt.lines[2]
        assertThat(fish.gross.toPlainString()).isEqualTo("16.55")
        assertThat(fish.unitCode).isEqualTo("2")
        val fee = receipt.lines[16]
        assertThat(fee.description).isEqualTo("Περιβαλλοντικό Τέλος&Πλασ.σακ.")
        assertThat(fee.lineTypeCode).isEqualTo("2")
        assertThat(receipt.lines.map { it.raw.keys }.flatten()).doesNotContain("line.lineComments")
    }

    @Test
    fun `the payment keeps no transaction or terminal id`() {
        val text = parse().toJson().toString()
        assertThat(text).doesNotContain("00X00XX0000X0X00X0X0XXXXXX0X0XX0000XXX00")
        assertThat(text).doesNotContain("000X00X00000XXXX000X00X000XXX00X00000X00")
    }

    @Test
    fun `the not-found answer is a missing receipt`() {
        assertFailure { parse("Document not found.") }.isInstanceOf<ReceiptNotFound>()
    }

    @Test
    fun `an XML for another document is refused`() {
        val other = fixture.replace("/fd/" + FAKE_HEX_ID, "/fd/" + "1".repeat(32))
        assertFailure { parse(other) }.isInstanceOf<InconsistentSource>()
    }

    @Test
    fun `an issuer without a known name is a format change`() {
        val other = fixture.replace("<vatNumber>800764388<", "<vatNumber>999999999<")
        assertFailure { parse(other) }.isInstanceOf<FormatChanged>()
        // A name in the XML is used as it is.
        val named = other.replace("<country>GR</country>", "<country>GR</country><name>SHOP</name>")
        assertThat(parse(named).issuer.legalName).isEqualTo("SHOP")
    }

    @Test
    fun `lines that do not add up to the summary are refused`() {
        val wrong = fixture.replace("<netValue>5.96<", "<netValue>9.96<")
        assertFailure { parse(wrong) }.isInstanceOf<InconsistentSource>()
    }

    @Test
    fun `malformed documents are format changes`() {
        val noLines = fixture.replace(Regex("""(?s)<invoiceDetails>.*</invoiceDetails>"""), "")
        assertFailure { parse(noLines) }.isInstanceOf<FormatChanged>()
        val badNumber = fixture.replace("<netValue>5.96<", "<netValue>5,96<")
        assertFailure { parse(badNumber) }.isInstanceOf<FormatChanged>()
        assertFailure { parse("<html><body>Checking your browser</body></html>") }
            .isInstanceOf<FormatChanged>()
    }

    // --- Sanitizing -----------------------------------------------------------------------------

    @Test
    fun `sanitizing replaces payment ids, signatures, the buyer and the document id`() {
        val raw = fixture
            .replace(FAKE_HEX_ID, "9f3c2a1b8e7d6c5b4a39281706f5e4d3")
            .replace("<tid>00000000<", "<tid>71234567<")
            .replace(
                "00X00XX0000X0X00X0X0XXXXXX0X0XX0000XXX00",
                "AB12CD34EF56AB12CD34EF56AB12CD34EF56AB12",
            )
            .replace(
                "</issuer>",
                "</issuer><counterpart><vatNumber>123456789</vatNumber></counterpart>",
            )
        val result = sanitizeXml(raw)
        assertThat(result.html).doesNotContain("9f3c2a1b8e7d6c5b4a39281706f5e4d3")
        assertThat(result.html).doesNotContain("71234567")
        assertThat(result.html).doesNotContain("AB12CD34EF56")
        assertThat(result.html).doesNotContain("123456789")
        assertThat(result.replaced["counterpart"]).isEqualTo(1)
        // The sanitized XML is still the same receipt.
        assertThat(parse(result.html).identity.mark).isEqualTo("400016074087707")
    }

    @Test
    fun `the fixture is already sanitized`() {
        assertThat(sanitizeXml(fixture).replaced.isEmpty()).isTrue()
    }

    // --- Fetching -------------------------------------------------------------------------------

    @Test
    fun `the engine fetches the XML from the merchant host only`(): Unit = runBlocking {
        val requests = mutableListOf<Triple<String, Set<String>, Set<String>>>()
        val fetcher = object : Fetcher {
            override suspend fun get(
                url: ParsedUrl,
                allowedHosts: Set<String>,
                acceptStatuses: Set<Int>,
                contentTypes: Set<String>,
            ): FetchedDocument {
                requests += Triple(url.geturl(), allowedHosts, contentTypes)
                return FetchedDocument(fixture.toByteArray())
            }
        }
        val engine = Engine(fetcher)
        val parsed = engine.read(engine.resolve(meta.getValue("url").jsonPrimitive.content))
        assertThat(requests).containsExactly(
            Triple(
                "https://$host/filedocument/GetInvoiceDocDetailed/$FAKE_UUID",
                setOf(host),
                XML_TYPES,
            ),
        )
        assertThat(parsed.receipt.totals.gross.toPlainString()).isEqualTo("51.07")
        assertThat(parsed.receipt.payments.single().methodCode).isNotNull()
    }

    @OptIn(ExperimentalSerializationApi::class)
    private val pretty = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
    }

    private fun sorted(element: JsonElement): JsonElement = when (element) {
        is JsonObject -> JsonObject(element.toSortedMap().mapValues { (_, value) -> sorted(value) })
        is JsonArray -> JsonArray(element.map(::sorted))
        else -> element
    }
}
