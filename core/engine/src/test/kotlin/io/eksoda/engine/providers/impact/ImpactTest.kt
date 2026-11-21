package io.eksoda.engine.providers.impact

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsExactly
import assertk.assertions.doesNotContain
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import io.eksoda.engine.InconsistentSource
import io.eksoda.engine.TestData
import io.eksoda.engine.providers.REGISTRY
import io.eksoda.engine.validation.validate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test

class ImpactTest {
    private val directory = TestData.fixtures.resolve("impact")

    /** The identity of the link in fixture [name]'s meta file. */
    private fun identityOf(name: String) = REGISTRY.resolveUrl(
        Json.parseToJsonElement(directory.resolve("$name.meta.json").readText())
            .jsonObject.getValue("url").jsonPrimitive.content,
    ).second

    /** A receipt whose link id (2755C3…) is not its myDATA UID (7E7DCA…). */
    private val ownId = directory.resolve("kogimou_3_lines.html").readText()
    private val identity = identityOf("kogimou_3_lines")

    /** Line 7 is 1.918 kg × 7.69 = 14.75 with 7.38 off (50%), paid 7.37. */
    private val discounted = directory.resolve("mymarket_7_lines_discount.html").readText()
    private val discountedIdentity = identityOf("mymarket_7_lines_discount")

    /** Line 7's discount cell. */
    private val discountCell = "<span class=\"value\"> 7,38 </span> </span> </span></td>"

    @Test
    fun `a line discount is read, and the value before it is the total plus the discount`() {
        val receipt = parseHtml(discounted, discountedIdentity)
        val line = receipt.lines.single { it.lineNumber == 7 }
        assertThat(line.discount?.toPlainString()).isEqualTo("7.38")
        assertThat(line.grossBeforeDiscount?.toPlainString()).isEqualTo("14.75")
        assertThat(line.gross.toPlainString()).isEqualTo("7.37")
        // A line without a discount prints an empty cell.
        assertThat(receipt.lines.first().discount).isNull()
        assertThat(receipt.lines.first().grossBeforeDiscount).isNull()
        assertThat(receipt.totals.discount?.toPlainString()).isEqualTo("7.38")
        // Quantity × unit price now meets the value before the discount.
        assertThat(validate(receipt)).isEmpty()
    }

    @Test
    fun `a line discount that explains neither the unit price nor the total is a warning`() {
        assertThat(discounted).contains(discountCell)
        val page = discounted.replace(discountCell, discountCell.replace("7,38", "3,00"))
        val issues = validate(parseHtml(page, discountedIdentity))
        assertThat(issues.map { listOf(it.code, it.lineNumber, it.expected, it.actual) })
            .containsExactly(
                // 14.75 before the discount, but 7.37 + 3.00 = 10.37.
                listOf("unit_price_mismatch", 7, "14.75", "10.37"),
                // The summary still prints 7.38 off.
                listOf("line_discounts_mismatch", null, "3.00", "7.38"),
            )
    }

    @Test
    fun `a line discount that is not an amount in euros is reported, and the import goes on`() {
        // Negative, a percentage, text: a layout not seen yet. Zero: no discount.
        val cases = listOf("-7,38" to "-7,38", "50%" to "50%", "-" to "-", "0,00" to null)
        for ((printed, drift) in cases) {
            val page = discounted.replace(discountCell, discountCell.replace("7,38", printed))
            val receipt = parseHtml(page, discountedIdentity)
            val line = receipt.lines.single { it.lineNumber == 7 }
            assertThat(line.discount, name = printed).isNull()
            assertThat(line.grossBeforeDiscount, name = printed).isNull()
            assertThat(receipt.unknownFields["commercial.line.Amount"], name = printed)
                .isEqualTo(drift)
        }
    }

    @Test
    fun `a zero discount total is no discount, and an unreadable one is reported`() {
        val total = "<td class=\"value\">7,38</td>"
        assertThat(discounted).contains(total)
        fun printing(value: String) =
            parseHtml(discounted.replace(total, total.replace("7,38", value)), discountedIdentity)
        val zero = printing("0,00")
        assertThat(zero.totals.discount).isNull()
        assertThat(zero.unknownFields).isEmpty()
        val text = printing("n/a")
        assertThat(text.totals.discount).isNull()
        assertThat(text.unknownFields)
            .isEqualTo(mapOf("commercial.summary.TotalAllowances" to "n/a"))
    }

    @Test
    fun `a link id that is not the UID is accepted when the page prints that link`() {
        val receipt = parseHtml(ownId, identity)
        assertThat(identity.uid).isEqualTo("2755C3A08459698199D6E9ED8417127F681845AE")
        assertThat(receipt.identity.uid).isEqualTo("7E7DCAFD80708F40AD96C286483E14849B9DC564")
    }

    @Test
    fun `a link id that is not the UID is refused when the page does not print the link`() {
        val unlinked = ownId.replace(
            "Url με το οποίο ο λήπτης του παραστατικού θα μπορεί να το λαμβάνει",
            "Άλλο πεδίο",
        )
        assertFailure { parseHtml(unlinked, identity) }.isInstanceOf<InconsistentSource>()
    }

    @Test
    fun `a page that prints another link is refused`() {
        val other = ownId.replace("/" + FAKE_GUID, "/" + "1".repeat(32))
        assertFailure { parseHtml(other, identity) }.isInstanceOf<InconsistentSource>()
    }

    @Test
    fun `the printed link is compared in its canonical form`() {
        val printed = "https://einvoice.impact.gr/p/EL801810254/" +
            "2755C3A08459698199D6E9ED8417127F681845AE/$FAKE_GUID"
        assertThat(ownId).contains(printed)
        // Host case, the default port, hex case and a trailing slash are the same link.
        val equivalent = "https://EINVOICE.impact.gr:443/p/el801810254/" +
            "2755c3a08459698199d6e9ed8417127f681845ae/$FAKE_GUID/"
        assertThat(parseHtml(ownId.replace(printed, equivalent), identity).identity.mark)
            .isEqualTo("400016110662608")
        // A link that does not resolve (another port, another host) is another receipt's.
        for (other in listOf(
            printed.replace("impact.gr/", "impact.gr:8443/"),
            printed.replace("einvoice.impact.gr", "einvoice.impact.gr.example"),
            printed.replace("/p/", "/p%2F"),
        )) {
            assertFailure { parseHtml(ownId.replace(printed, other), identity) }
                .isInstanceOf<InconsistentSource>()
        }
    }

    @Test
    fun `a tip printed before the payment method belongs to that payment`() {
        val payment = parseHtml(ownId, identity).payments.single()
        assertThat(payment.methodLabel).isEqualTo("EPAY A80")
        assertThat(payment.amount.toPlainString()).isEqualTo("22.90")
        // The commercial amount is cross-checked again.
        val amount = "Ποσό Πληρωμής</td>\n               <td class=\"value\">22,90</td>"
        assertThat(ownId).contains(amount)
        val wrongAmount = ownId.replace(amount, amount.replace("22,90", "29,90"))
        assertFailure { parseHtml(wrongAmount, identity) }.isInstanceOf<InconsistentSource>()
    }

    @Test
    fun `a commercial tip that differs from the tax view is refused`() {
        val tip = "<td class=\"label\">Φιλοδώρημα</td>\n" +
            "               <td class=\"value\">0,00</td>"
        assertThat(ownId).contains(tip)
        val wrongTip = ownId.replace(tip, tip.replace("0,00", "2,00"))
        assertFailure { parseHtml(wrongTip, identity) }.isInstanceOf<InconsistentSource>()
    }

    @Test
    fun `sanitizing redacts the myDATA QR token wherever it is`() {
        val raw = ownId
            .replace("QRInfo?q=REDACTED", "QRInfo?q=FakeQrTokenXyz%2b456")
            .replace(
                "<fluent-anchor id=\"erpQrBtn\"",
                "<span data-url=\"https://mydatapi.aade.gr/myDATA/TimologioQR/QRInfo?q=" +
                    "FakeQrTokenAbc\">https://mydatapi.aade.gr/myDATA/TimologioQR/QRInfo?q=" +
                    "FakeQrTokenDef</span><fluent-anchor id=\"erpQrBtn\"",
            )
        val result = sanitizeHtml(raw)
        assertThat(result.html).doesNotContain("FakeQrToken")
        assertThat(result.html).contains(
            "<fluent-anchor id=\"erpQrBtn\" href=\"https://mydatapi.aade.gr/myDATA/" +
                "TimologioQR/QRInfo?q=REDACTED\"",
        )
        assertThat(result.replaced["mydata_qr_token"]).isEqualTo(3)
    }
}
