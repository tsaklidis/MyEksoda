package io.eksoda.engine.persist

import assertk.assertThat
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import io.eksoda.engine.TestData
import io.eksoda.engine.canonical.Document
import io.eksoda.engine.canonical.Identity
import io.eksoda.engine.canonical.Payment
import io.eksoda.engine.mydata.DocumentKind
import io.eksoda.engine.mydata.PaymentMethod
import io.eksoda.engine.providers.REGISTRY
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime

class ReceiptValuesTest {
    private fun document(
        formatted: String? = null,
        series: String? = null,
        number: String? = null,
    ) = Document(
        kind = DocumentKind.SALE,
        issuedAt = OffsetDateTime.parse("2024-05-17T09:51:34+03:00"),
        issuedDate = LocalDate.parse("2024-05-17"),
        currency = "EUR",
        series = series,
        number = number,
        formattedNumber = formatted,
    )

    @Test
    fun `the document number is the formatted one, else series and number`() {
        assertThat(documentNumber(document(formatted = "Α-12", series = "B", number = "3")))
            .isEqualTo("Α-12")
        assertThat(documentNumber(document(series = "Α", number = "12"))).isEqualTo("Α-12")
        assertThat(documentNumber(document(formatted = "", series = "", number = "7")))
            .isEqualTo("7")
        assertThat(documentNumber(document())).isEqualTo("")
    }

    private fun payment(method: PaymentMethod?) = Payment(BigDecimal.ONE, method)

    @Test
    fun `the payment method is the one used, mixed or unknown`() {
        assertThat(paymentMethod(emptyList())).isEqualTo("unknown")
        assertThat(paymentMethod(listOf(payment(PaymentMethod.CARD), payment(PaymentMethod.CARD))))
            .isEqualTo("card")
        assertThat(paymentMethod(listOf(payment(PaymentMethod.CARD), payment(PaymentMethod.CASH))))
            .isEqualTo("mixed")
        assertThat(paymentMethod(listOf(payment(null)))).isEqualTo("unknown")
    }

    @Test
    fun `only a non-zero cancellation mark cancels`() {
        assertThat(isCancelled(Identity())).isFalse()
        assertThat(isCancelled(Identity(cancellationMark = "000"))).isFalse()
        assertThat(isCancelled(Identity(cancellationMark = "400001234"))).isTrue()
    }

    @Test
    fun `card numbers that pass the Luhn check are dropped`() {
        assertThat(dropCardNumbers("VISA 4111 1111 1111 1111")).isEqualTo("VISA <card>")
        assertThat(dropCardNumbers("MC 5500-0000-0000-0004 OK")).isEqualTo("MC <card> OK")
        assertThat(dropCardNumbers("REF 4111111111111112")).isEqualTo("REF 4111111111111112")
        assertThat(dropCardNumbers("AUTH 123456")).isEqualTo("AUTH 123456")
    }

    @Test
    fun `stored source data has no URL, unknown values or card numbers`() {
        val directory = TestData.fixtures.resolve("impact")
        val meta = Json.parseToJsonElement(directory.resolve("sample_el.meta.json").readText())
        val (provider, identity) =
            REGISTRY.resolveUrl(meta.jsonObject.getValue("url").jsonPrimitive.content)
        val receipt = provider.parseHtml(directory.resolve("sample_el.html").readText(), identity)
            .copy(
                unknownFields = mapOf("loyalty_code" to "998877"),
                payments = listOf(
                    Payment(BigDecimal.ONE, PaymentMethod.CARD, methodLabel = CARD_LABEL),
                    Payment(BigDecimal.ONE, PaymentMethod.CASH),
                ),
            )
        val data = sourceData(receipt)
        assertThat(data.keys).doesNotContain("source_url")
        assertThat(data.getValue("unknown_fields"))
            .isEqualTo(JsonObject(mapOf("loyalty_code" to JsonPrimitive(""))))
        val payments = data.getValue("payments").jsonArray
        assertThat(payments[0].jsonObject.getValue("method_label").jsonPrimitive.content)
            .isEqualTo("VISA <card>")
        assertThat(data.toString()).doesNotContain("998877")
        assertThat(data.toString()).doesNotContain(receipt.sourceUrl)
    }

    private companion object {
        const val CARD_LABEL = "VISA 4111 1111 1111 1111"
    }
}
