package io.eksoda.feature.products.detail

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isLessThanOrEqualTo
import io.eksoda.core.model.Merchant
import io.eksoda.core.model.Money
import io.eksoda.core.model.PriceEntry
import org.junit.Test
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.time.ZoneOffset

class PricePointsTest {
    private val start = OffsetDateTime.of(2026, 1, 1, 10, 0, 0, 0, ZoneOffset.UTC)

    /** Entries newest first, as the repository returns them; [prices] are oldest first. */
    private fun entries(vararg prices: String): List<PriceEntry> =
        prices.mapIndexed { index, price ->
            PriceEntry(
                itemId = "i$index",
                receiptId = "r$index",
                issuedAt = start.plusDays(index.toLong()),
                merchant = Merchant("m", "Lidl"),
                store = null,
                description = "ΓΑΛΑ",
                quantity = BigDecimal.ONE,
                unit = "piece",
                grossAmount = Money(BigDecimal(price), "EUR"),
                normalizedQuantity = BigDecimal.ONE,
                normalizedUnit = "l",
                normalizedUnitPrice = BigDecimal(price),
            )
        }.reversed()

    @Test
    fun `a run of the same price is one point`() {
        val points = pricePoints(entries("1.20", "1.20", "1.25", "1.25", "1.20"))
        assertThat(points.map { it.price.toPlainString() }).containsExactly("1.20", "1.25", "1.20")
    }

    @Test
    fun `many prices are thinned to the limit, keeping the ends and the extremes`() {
        val prices = (0 until 60).map {
            BigDecimal("1.00").add(BigDecimal(it % 13).movePointLeft(2))
        }
        val points =
            pricePoints(entries(*prices.map { it.toPlainString() }.toTypedArray()), max = 12)
        assertThat(points.size).isLessThanOrEqualTo(12)
        assertThat(points.first().price).isEqualTo(prices.first())
        assertThat(points.last().price).isEqualTo(prices.last())
        assertThat(points.minOf { it.price }).isEqualTo(prices.min())
        assertThat(points.maxOf { it.price }).isEqualTo(prices.max())
    }
}
