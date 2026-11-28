package io.eksoda.engine.providers.entersoft

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsExactly
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNull
import assertk.assertions.messageContains
import io.eksoda.engine.FormatChanged
import io.eksoda.engine.InconsistentSource
import io.eksoda.engine.TestData
import io.eksoda.engine.canonical.CanonicalReceipt
import io.eksoda.engine.canonical.IssueTime
import io.eksoda.engine.mydata.PaymentMethod
import io.eksoda.engine.providers.REGISTRY
import io.eksoda.engine.validation.validate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneOffset

/**
 * The Entersoft parser at the edges of what a page can hold: no page, no table, several tables,
 * blank or doubled identifiers, totals right at the tolerance, split payments, and times around
 * midnight and the clock changes. Each case changes one thing on the LIDL sample.
 */
class EntersoftBoundaryTest {
    private val directory = TestData.fixtures.resolve("entersoft")
    private val receipt = directory.resolve("lidl_21_lines.html").readText()
    private val identity = REGISTRY.resolveUrl(
        Json.parseToJsonElement(directory.resolve("lidl_21_lines.meta.json").readText())
            .jsonObject.getValue("url").jsonPrimitive.content,
    ).second
    private val sample: CanonicalReceipt = parse(receipt)

    private fun parse(page: String) = parseHtml(page, identity)

    private fun edited(old: String, new: String, page: String = receipt): String {
        val result = page.replace(old, new)
        check(result != page) { "'$old' is not on the page" }
        return result
    }

    private fun edited(pattern: Regex, new: String, page: String = receipt): String {
        val result = page.replace(pattern, new)
        check(result != page) { "$pattern is not on the page" }
        return result
    }

    // --- the page's structure -------------------------------------------------------------------

    @Test
    fun `an empty page, or one without the item table, is a format change`() {
        val table = Regex("""<table[\s\S]*?</table>""")
        val pages = listOf(
            "",
            "<html><body></body></html>",
            edited(table, ""),
            // The table is there but has no rows.
            edited(Regex("""<tbody>[\s\S]*?</tbody>"""), "<tbody></tbody>"),
        )
        for (page in pages) {
            assertFailure { parse(page) }.isInstanceOf<FormatChanged>()
        }
    }

    @Test
    fun `without its header row the table is read by its cells' titles`() {
        val headless = edited(Regex("""<thead[\s\S]*?</thead>"""), "")
        assertThat(parse(headless).toJson()).isEqualTo(sample.toJson())
    }

    @Test
    fun `another table after the items is not read, one before them is never read as items`() {
        val other = "<table><tbody><tr><td data-title=\"Περιγραφή\">Promo</td>" +
            "<td data-title=\"Συνολική Αξία\">5,00</td></tr></tbody></table>"
        val after = edited("</table>", "</table>$other")
        assertThat(parse(after).lines).isEqualTo(sample.lines)
        // Read first, its one line would not add up to the receipt's total: refused, not misread.
        val before = edited("<table class=", "$other<table class=")
        assertFailure { parse(before) }.isInstanceOf<InconsistentSource>()
    }

    // --- identifiers ----------------------------------------------------------------------------

    @Test
    fun `a blank UID or MARK is none, and the receipt needs one of them`() {
        val uidValue = "E2A3E0FBCED3888571173F62A45B81FED079220D"
        val markValue = "400014375081554"
        val blankUid = parse(edited(">$uidValue<", ">   <"))
        assertThat(blankUid.identity.uid).isNull()
        assertThat(blankUid.identity.mark).isEqualTo(markValue)
        val blankMark = parse(edited(">$markValue<", "> <"))
        assertThat(blankMark.identity.mark).isNull()
        assertThat(blankMark.identity.uid).isEqualTo(uidValue)
        val neither = edited(">$markValue<", "><", edited(">$uidValue<", "><"))
        assertFailure { parse(neither) }.isInstanceOf<FormatChanged>().messageContains("UID")
    }

    @Test
    fun `a lowercase UID is the same UID, and a doubled label reads its first value`() {
        val uid = "E2A3E0FBCED3888571173F62A45B81FED079220D"
        assertThat(parse(edited(">$uid<", ">${uid.lowercase()}<")).identity.uid).isEqualTo(uid)
        // A second MARK row further down: the first one printed is the receipt's.
        val markRow = Regex(
            """<div class="row">\s*<div class="col[^"]*">M\.AR\.K: </div>[\s\S]*?</div>\s*</div>""",
        )
        val row = checkNotNull(markRow.find(receipt)).value
        val doubled = edited(row, row + row.replace("400014375081554", "400099999999999"))
        assertThat(parse(doubled).identity.mark).isEqualTo("400014375081554")
    }

    @Test
    fun `a blank document number is no number`() {
        for (blank in listOf("", "   ")) {
            val page = edited("Αρ. Παραστατικού: 006604-789636", "Αρ. Παραστατικού:$blank")
            val document = parse(page).document
            assertThat(document.number, name = "'$blank'").isNull()
            assertThat(document.series).isNull()
            assertThat(document.formattedNumber).isNull()
        }
    }

    // --- the receipt's own arithmetic, at the tolerance -----------------------------------------

    @Test
    fun `each total may be off by two cents, not three`() {
        // gross, discount, net and VAT totals; the line totals are left as printed.
        val totals = listOf(
            Triple("background-color:#f2f2f2;\">75,48EUR", "75,50", "75,51"),
            Triple(">3,80EUR<", "3,82", "3,83"),
            Triple(">66,39EUR<", "66,37", "66,36"),
            Triple(">9,09EUR<", "9,11", "9,12"),
        )
        for ((printed, within, beyond) in totals) {
            val amount = printed.substringAfter('>').substringBefore("EUR")
            assertThat(parse(edited(printed, printed.replace(amount, within))).lines, name = within)
                .isEqualTo(sample.lines)
            assertFailure { parse(edited(printed, printed.replace(amount, beyond))) }
                .isInstanceOf<InconsistentSource>()
        }
    }

    @Test
    fun `the lines' rounding adds up across lines, within the same two cents`() {
        val cell = "data-title=\"Συνολική Αξία\">"
        fun bump(cents: List<Pair<String, String>>): String =
            cents.fold(receipt) { page, (old, new) -> edited("$cell$old<", "$cell$new<", page) }
        // A cent up on two lines: 0.02 over the total, read.
        val two = bump(listOf("1,36" to "1,37", "2,68" to "2,69"))
        assertThat(parse(two).totals.gross).isEqualTo(BigDecimal("75.48"))
        // On three: 0.03 over, refused.
        val three = bump(listOf("1,36" to "1,37", "2,68" to "2,69", "1,70" to "1,71"))
        assertFailure { parse(three) }.isInstanceOf<InconsistentSource>()
    }

    // --- payments -------------------------------------------------------------------------------

    private val paymentRow = Regex(
        """<div class="row">\s*<div class="col-6 fontSize6pt"[^>]*>\s*POS / e-POS\s*</div>\s*""" +
            """<div class="col-6 fontSize6pt"[^>]*>75,48EUR</div>\s*</div>""",
    )

    private fun payments(vararg rows: Pair<String, String>): String = edited(
        paymentRow,
        rows.joinToString("") { (label, amount) ->
            "<div class=\"row\"><div class=\"col-6 fontSize6pt\">$label</div>" +
                "<div class=\"col-6 fontSize6pt\">$amount</div></div>"
        },
    )

    @Test
    fun `a receipt paid in two ways lists both, and they add up`() {
        val split = parse(payments("POS / e-POS" to "70,00EUR", "ΜΕΤΡΗΤΑ" to "5,48EUR"))
        assertThat(split.payments.map { it.method })
            .containsExactly(PaymentMethod.CARD, PaymentMethod.CASH)
        assertThat(split.payments.map { it.amount })
            .containsExactly(BigDecimal("70.00"), BigDecimal("5.48"))
        assertThat(validate(split).map { it.code }).doesNotContain("payment_mismatch")
    }

    @Test
    fun `a payment without its amount is not read, and the rest are reported short`() {
        val partial = parse(payments("POS / e-POS" to "70,00EUR", "ΜΕΤΡΗΤΑ" to ""))
        assertThat(partial.payments.map { it.method }).containsExactly(PaymentMethod.CARD)
        assertThat(validate(partial).map { it.code }).contains("payment_mismatch")
        // A payment without its label is kept, its way of paying unknown.
        val unlabeled = parse(payments("" to "75,48EUR"))
        assertThat(unlabeled.payments.single().method).isNull()
        assertThat(unlabeled.payments.single().amount).isEqualTo(BigDecimal("75.48"))
    }

    // --- times ----------------------------------------------------------------------------------

    /** The sample issued on [date] (dd-MM-yyyy) and sent to myDATA that day at [time]. */
    private fun transmitted(date: String, time: String): CanonicalReceipt {
        val page = if (date == SAMPLE_DATE) {
            receipt
        } else {
            edited("Ημ/νία έκδοσης: $SAMPLE_DATE", "Ημ/νία έκδοσης: $date")
        }
        return parse(edited("13-07-2026 15:17:11", "$date $time", page))
    }

    @Test
    fun `a transmission up to midnight is that day's time, after it only the date is known`() {
        val lastSecond = transmitted("13-07-2026", "23:59:59")
        assertThat(lastSecond.coverage.issueTime).isEqualTo(IssueTime.APPROXIMATE)
        assertThat(lastSecond.document.issuedAt.toString()).isEqualTo("2026-07-13T23:59:59+03:00")
        val page = edited("13-07-2026 15:17:11", "14-07-2026 00:00:01")
        val nextDay = parse(page)
        assertThat(nextDay.coverage.issueTime).isEqualTo(IssueTime.DATE_ONLY)
        assertThat(nextDay.document.issuedAt.toString()).isEqualTo("2026-07-13T00:00+03:00")
    }

    @Test
    fun `times are Athens time, in winter and in summer`() {
        assertThat(transmitted("15-01-2026", "10:00:00").document.issuedAt.offset)
            .isEqualTo(ZoneOffset.ofHours(2))
        assertThat(transmitted("15-07-2026", "10:00:00").document.issuedAt.offset)
            .isEqualTo(ZoneOffset.ofHours(3))
    }

    @Test
    fun `times the clock change skips or repeats are read as one instant`() {
        // 29 March 2026: 03:00 becomes 04:00, so 03:30 is not a time in Athens that day.
        val skipped = transmitted("29-03-2026", "03:30:00").document.issuedAt
        assertThat(skipped.toInstant()).isEqualTo(Instant.parse("2026-03-29T01:30:00Z"))
        // 25 October 2026: 04:00 becomes 03:00, so 03:30 happens twice; the first one is read.
        val repeated = transmitted("25-10-2026", "03:30:00").document.issuedAt
        assertThat(repeated.toInstant()).isEqualTo(Instant.parse("2026-10-25T00:30:00Z"))
        // The day the clocks change still starts at its own midnight.
        val page = edited("Ημ/νία έκδοσης: 13-07-2026", "Ημ/νία έκδοσης: 29-03-2026")
        val dateOnly = parse(page).document.issuedAt
        assertThat(dateOnly.toString()).isEqualTo("2026-03-29T00:00+02:00")
        assertThat(dateOnly).isNotEqualTo(sample.document.issuedAt)
    }

    private companion object {
        const val SAMPLE_DATE = "13-07-2026"
    }
}
