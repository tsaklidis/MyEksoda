package io.eksoda.engine.providers.entersoft

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.key
import assertk.assertions.messageContains
import io.eksoda.engine.FormatChanged
import io.eksoda.engine.InconsistentSource
import io.eksoda.engine.ReceiptNotFound
import io.eksoda.engine.TestData
import io.eksoda.engine.canonical.CanonicalReceipt
import io.eksoda.engine.canonical.IssueTime
import io.eksoda.engine.mydata.Unit
import io.eksoda.engine.providers.REGISTRY
import io.eksoda.engine.url.parseSubmittedUrl
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import java.time.LocalTime
import java.time.OffsetDateTime

/**
 * How the Entersoft parser reads a page that differs from the LIDL sample: each case edits one
 * thing on the sample page and checks the parser reads it, or refuses it with the right error.
 */
class EntersoftPageTest {
    private val directory = TestData.fixtures.resolve("entersoft")
    private val shell = directory.resolve("shell.html").readText()
    private val receipt = directory.resolve("lidl_21_lines.html").readText()
    private val identity = REGISTRY.resolveUrl(
        Json.parseToJsonElement(directory.resolve("lidl_21_lines.meta.json").readText())
            .jsonObject.getValue("url").jsonPrimitive.content,
    ).second
    private val link = checkNotNull(split(parseSubmittedUrl(identity.canonicalUrl)))
    private val sample: CanonicalReceipt = parseHtml(receipt, identity)

    /** The sample page with [old] replaced by [new]; fails if [old] is not on the page. */
    private fun edited(old: String, new: String, page: String = receipt): String {
        val result = page.replace(old, new)
        check(result != page) { "'$old' is not on the page" }
        return result
    }

    private fun parse(page: String) = parseHtml(page, identity)

    // --- the shell page's frame ------------------------------------------------------------------

    @Test
    fun `the frame may be named by its source alone, or by an absolute link to the host`() {
        val unnamed = edited("id=\"iframeContent\"", "id=\"viewer\"", shell)
        val absolute = edited(
            "src=\"/api/GetInvoice?",
            "src=\"https://$HOST/api/GetInvoice?",
            shell,
        )
        val expected = documentUrlFromShell(shell, link)
        assertThat(expected.path).isEqualTo(DOCUMENT_PATH)
        for (page in listOf(unnamed, absolute)) {
            assertThat(documentUrlFromShell(page, link).geturl()).isEqualTo(expected.geturl())
        }
    }

    @Test
    fun `a shell page without a document frame is a format change`() {
        val noFrame = shell.replace("<iframe", "<div").replace("</iframe>", "</div>")
        val emptySource = shell.replace(Regex("""src="/api/GetInvoice\?[^"]*""""), "src=\"\"")
        assertThat(emptySource).isNotEqualTo(shell)
        for (page in listOf(noFrame, emptySource)) {
            assertFailure { documentUrlFromShell(page, link) }
                .isInstanceOf<FormatChanged>()
                .messageContains("no document frame")
        }
    }

    @Test
    fun `a frame anywhere but the provider's document address is refused`() {
        val elsewhere = listOf(
            "src=\"https://example.com/api/GetInvoice?",
            "src=\"http://$HOST/api/GetInvoice?",
            "src=\"//$HOST/api/GetInvoice?",
            "src=\"/api/GetOtherInvoice?",
        )
        for (source in elsewhere) {
            val page = edited("src=\"/api/GetInvoice?", source, shell)
            assertFailure { documentUrlFromShell(page, link) }
                .isInstanceOf<FormatChanged>()
                .messageContains("unexpected address")
        }
    }

    @Test
    fun `the provider's not-found page is a missing receipt`() {
        assertFailure { parse("<html><body><h1>Page not found</h1></body></html>") }
            .isInstanceOf<ReceiptNotFound>()
    }

    // --- item columns ---------------------------------------------------------------------------

    @Test
    fun `cells without their title are read by the column header above them`() {
        val untitled = receipt
            .replace(" data-title=\"Κωδικός\"", "")
            .replace(" data-title=\"Περιγραφή\"", "")
        assertThat(untitled).isNotEqualTo(receipt)
        assertThat(parse(untitled).toJson()).isEqualTo(sample.toJson())
    }

    @Test
    fun `cells no column names are kept as unknown fields, not read`() {
        val page = edited(
            "<td class=\"text-right\" data-title=\"Συνολική Αξία\">1,36</td>",
            "<td class=\"text-right\" data-title=\"Συνολική Αξία\">1,36</td>" +
                "<td data-title=\"Σημείωση\">gift</td><td>extra</td>",
        )
        val parsed = parse(page)
        assertThat(parsed.unknownFields).key("item.Σημείωση").isEqualTo("gift")
        // Untitled and past the last header: named by its position.
        assertThat(parsed.unknownFields).key("item.10").isEqualTo("extra")
        assertThat(parsed.lines).isEqualTo(sample.lines)
    }

    @Test
    fun `an unknown unit is other, its label kept`() {
        val page = receipt.replaceFirst(
            "<td data-title=\"Μ.Μ.\">TEMAXIO</td>",
            "<td data-title=\"Μ.Μ.\">ΣΑΚΟΥΛΑ</td>",
        )
        assertThat(page).isNotEqualTo(receipt)
        val parsed = parse(page)
        val first = parsed.lines.first()
        assertThat(first.unit).isEqualTo(Unit.OTHER)
        assertThat(first.unitLabel).isEqualTo("ΣΑΚΟΥΛΑ")
        assertThat(parsed.unknownFields).key("unit_label.ΣΑΚΟΥΛΑ").isEqualTo("ΣΑΚΟΥΛΑ")
        // The next line, still a known unit.
        assertThat(parsed.lines[1].unit).isEqualTo(sample.lines[1].unit)
    }

    @Test
    fun `a line without its total is a format change`() {
        val page = receipt.replaceFirst(
            "<td class=\"text-right\" data-title=\"Συνολική Αξία\">1,36</td>",
            "<td class=\"text-right\" data-title=\"Συνολική Αξία\"></td>",
        )
        assertThat(page).isNotEqualTo(receipt)
        assertFailure { parse(page) }.isInstanceOf<FormatChanged>()
    }

    // --- the receipt's own arithmetic ------------------------------------------------------------

    @Test
    fun `totals that do not add up refuse the receipt`() {
        val cases = mapOf(
            "background-color:#f2f2f2;\">75,48EUR" to "background-color:#f2f2f2;\">76,48EUR"
                to "line totals",
            ">3,80EUR<" to ">4,80EUR<" to "line discounts",
            ">66,39EUR<" to ">67,39EUR<" to "net total",
            ">9,09EUR<" to ">9,59EUR<" to "VAT total",
        )
        for ((change, message) in cases) {
            val (old, new) = change
            assertFailure { parse(edited(old, new)) }
                .isInstanceOf<InconsistentSource>()
                .messageContains(message)
        }
    }

    @Test
    fun `totals within two cents are read`() {
        assertThat(parse(edited(">66,39EUR<", ">66,41EUR<")).totals.net.toString())
            .isEqualTo("66.41")
        assertFailure { parse(edited(">66,39EUR<", ">66,42EUR<")) }
            .isInstanceOf<InconsistentSource>()
    }

    @Test
    fun `a total without its currency is a format change`() {
        val page =
            edited("background-color:#f2f2f2;\">75,48EUR", "background-color:#f2f2f2;\">75,48")
        assertFailure { parse(page) }.isInstanceOf<FormatChanged>().messageContains("currency")
    }

    // --- dates ----------------------------------------------------------------------------------

    @Test
    fun `a transmission on the issue day is the time of purchase`() {
        assertThat(sample.coverage.issueTime).isEqualTo(IssueTime.APPROXIMATE)
        assertThat(sample.document.issuedAt)
            .isEqualTo(OffsetDateTime.parse("2026-07-13T15:17:11+03:00"))
    }

    @Test
    fun `without a same-day transmission only the date is known`() {
        val nextDay = edited("13-07-2026 15:17:11", "14-07-2026 00:10:00")
        val noTransmission = edited("Ημ/νία διαβίβασης: ", "Κάτι άλλο: ")
        for (page in listOf(nextDay, noTransmission)) {
            val parsed = parse(page)
            assertThat(parsed.coverage.issueTime).isEqualTo(IssueTime.DATE_ONLY)
            assertThat(parsed.document.issuedAt.toLocalTime()).isEqualTo(LocalTime.MIDNIGHT)
            assertThat(parsed.document.issuedDate.toString()).isEqualTo("2026-07-13")
        }
    }

    @Test
    fun `dates that are not dates are a format change`() {
        val pages = listOf(
            edited("Ημ/νία έκδοσης: 13-07-2026", "Ημ/νία έκδοσης: 31-02-2026"),
            edited("Ημ/νία έκδοσης: 13-07-2026", "Ημ/νία έκδοσης: σήμερα"),
            edited("13-07-2026 15:17:11", "13-07-2026 25:99:00"),
        )
        for (page in pages) {
            assertFailure { parse(page) }.isInstanceOf<FormatChanged>()
        }
        val noDate = edited("<div class=\"fontSize8pt\">Ημ/νία έκδοσης: 13-07-2026</div>", "")
        assertFailure { parse(noDate) }.isInstanceOf<FormatChanged>()
    }

    // --- document and identity -------------------------------------------------------------------

    @Test
    fun `the document number is split into series and number when it has both`() {
        assertThat(sample.document.series).isEqualTo("006604")
        assertThat(sample.document.number).isEqualTo("789636")
        val bare = parse(edited("Αρ. Παραστατικού: 006604-789636", "Αρ. Παραστατικού: 789636"))
        assertThat(bare.document.series).isNull()
        assertThat(bare.document.number).isEqualTo("789636")
        assertThat(bare.document.formattedNumber).isEqualTo("789636")
        val none =
            parse(edited("<div class=\"fontSize8pt\">Αρ. Παραστατικού: 006604-789636</div>", ""))
        assertThat(none.document.number).isNull()
        assertThat(none.document.formattedNumber).isNull()
    }

    @Test
    fun `an unknown document title is kept for review`() {
        val parsed = parse(edited(">Απόδειξη Λιανικής Πώλησης<", ">Δελτίο Κάτι<"))
        assertThat(parsed.document.typeCode).isNull()
        assertThat(parsed.document.typeLabel).isEqualTo("Δελτίο Κάτι")
        assertThat(parsed.unknownFields).key("document.type_label").isEqualTo("Δελτίο Κάτι")
    }

    @Test
    fun `a receipt needs its UID or its MARK`() {
        val noUid = parse(edited(">UID: <", ">Κωδικός: <"))
        assertThat(noUid.identity.uid).isNull()
        assertThat(noUid.identity.mark).isEqualTo("400014375081554")
        val noMark = parse(edited(">M.AR.K: <", ">Αριθμός: <"))
        assertThat(noMark.identity.mark).isNull()
        assertThat(noMark.identity.uid).isNotNull()
        val neither = edited(">M.AR.K: <", ">Αριθμός: <", edited(">UID: <", ">Κωδικός: <"))
        assertFailure { parse(neither) }.isInstanceOf<FormatChanged>().messageContains("UID")
    }

    // --- branch and payments --------------------------------------------------------------------

    @Test
    fun `an address in another shape is kept whole as the street`() {
        val address = sample.branch?.address
        assertThat(address?.street).isEqualTo("2 ΧΛΜ Ε.Ο. ΚΟΖΑΝΗΣ - ΘΕΣ/ΝΙΚΗΣ")
        // "-" for the street number is no number.
        assertThat(address?.number).isNull()
        assertThat(address?.postalCode).isEqualTo("50100")
        val free = parse(
            edited("2 ΧΛΜ Ε.Ο. ΚΟΖΑΝΗΣ - ΘΕΣ/ΝΙΚΗΣ, -, 50100, ΚΟΖΑΝΗΣ", "ΚΟΖΑΝΗ ΚΕΝΤΡΟ"),
        )
        assertThat(free.branch?.address?.street).isEqualTo("ΚΟΖΑΝΗ ΚΕΝΤΡΟ")
        assertThat(free.branch?.address?.postalCode).isNull()
        val numbered = parse(
            edited("ΘΕΣ/ΝΙΚΗΣ, -, 50100", "ΘΕΣ/ΝΙΚΗΣ, 12, 50100"),
        )
        assertThat(numbered.branch?.address?.number).isEqualTo("12")
    }

    @Test
    fun `a page without an address has no branch`() {
        val page = edited(
            "<div class=\"fontSize8pt\">2 ΧΛΜ Ε.Ο. ΚΟΖΑΝΗΣ - ΘΕΣ/ΝΙΚΗΣ, -, 50100, ΚΟΖΑΝΗΣ</div>",
            "",
        )
        assertThat(parse(page).branch).isNull()
    }

    @Test
    fun `an unknown payment label is kept, with no method`() {
        val page = receipt.replace(
            Regex("""(background-color:white;">\s*)POS / e-POS"""),
            "$1ΔΩΡΟΚΑΡΤΑ",
        )
        assertThat(page).isNotEqualTo(receipt)
        val payment = parse(page).payments.single()
        assertThat(payment.method).isNull()
        assertThat(payment.methodLabel).isEqualTo("ΔΩΡΟΚΑΡΤΑ")
        assertThat(parse(page).unknownFields).key("payment_label.ΔΩΡΟΚΑΡΤΑ")
            .isEqualTo("ΔΩΡΟΚΑΡΤΑ")
    }
}
