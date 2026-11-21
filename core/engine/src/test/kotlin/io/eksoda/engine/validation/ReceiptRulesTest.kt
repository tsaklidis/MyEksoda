package io.eksoda.engine.validation

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import io.eksoda.engine.canonical.Coverage
import io.eksoda.engine.canonical.Identity
import io.eksoda.engine.canonical.Notice
import io.eksoda.engine.canonical.Payment
import io.eksoda.engine.canonical.VatEntry
import io.eksoda.engine.mydata.DocumentKind
import org.junit.jupiter.api.Test

/** Receipt-level rules: R8–R12, R15, R16 and provider notices. */
class ReceiptRulesTest {
    @Test
    fun `receipt totals that do not add up to the gross are flagged`() {
        val withFee = CLEAN.copy(totals = CLEAN.totals.copy(fees = d("1.00")))
        assertThat(found(withFee))
            .containsExactly(listOf("receipt_amount_mismatch", null, "16.94", "15.94"))
    }

    @Test
    fun `a VAT breakdown that does not add up to the net total is flagged`() {
        val breakdown = listOf(VatEntry(net = d("14.00"), vat = d("1.82"), rate = RATE))
        assertThat(found(CLEAN.copy(vatBreakdown = breakdown)))
            .containsExactly(listOf("vat_breakdown_mismatch", null, "14.00", "14.12"))
    }

    @Test
    fun `payments are compared with the gross total`() {
        assertThat(found(CLEAN.copy(payments = listOf(Payment(amount = d("15.94")))))).isEmpty()
        assertThat(found(CLEAN.copy(payments = listOf(Payment(amount = d("10.00"))))))
            .containsExactly(listOf("payment_mismatch", null, "15.94", "10.00"))
    }

    @Test
    fun `a missing MARK is flagged only when one is expected`() {
        val unmarked = CLEAN.copy(identity = Identity(mark = null))
        assertThat(codes(unmarked)).containsExactly("mark_missing")
        val failed = CLEAN.copy(identity = Identity(mark = null, transmissionFailure = true))
        assertThat(codes(failed)).isEmpty()
        val unregistered = unmarked.copy(coverage = Coverage(mydataRegistered = false))
        assertThat(codes(unregistered)).isEmpty()
    }

    @Test
    fun `a receipt not in EUR is flagged`() {
        val dollars = CLEAN.copy(document = CLEAN.document.copy(currency = "USD"))
        assertThat(found(dollars)).containsExactly(listOf("non_eur_currency", null, "EUR", "USD"))
    }

    @Test
    fun `an unknown document type is flagged with its label, or its code without one`() {
        val other = CLEAN.document.copy(
            kind = DocumentKind.OTHER,
            typeCode = "8.6",
            typeLabel = "Δελτίο",
        )
        assertThat(found(CLEAN.copy(document = other)))
            .containsExactly(listOf("unknown_document_type", null, null, "Δελτίο"))
        assertThat(found(CLEAN.copy(document = other.copy(typeLabel = ""))))
            .containsExactly(listOf("unknown_document_type", null, null, "8.6"))
    }

    @Test
    fun `a total discount that does not lead to the gross is flagged`() {
        fun discounted(before: String) = CLEAN.copy(
            totals = CLEAN.totals.copy(grossBeforeDiscount = d(before), discount = d("1.00")),
        )
        assertThat(found(discounted("16.94"))).isEmpty()
        assertThat(found(discounted("20.00")))
            .containsExactly(listOf("total_discount_mismatch", null, "19.00", "15.94"))
    }

    @Test
    fun `provider notices become issues`() {
        val notice = Notice("signature_mismatch", "The signature does not match.")
        val issue = validate(CLEAN.copy(notices = listOf(notice))).single()
        assertThat(listOf(issue.code, issue.message))
            .containsExactly("signature_mismatch", "The signature does not match.")
    }
}
