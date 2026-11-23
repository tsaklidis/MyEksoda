package io.eksoda.engine.products

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

class PriceBoardTest {
    private val today = LocalDate.of(2026, 11, 22)

    private fun line(
        product: String,
        shop: String,
        price: String,
        quantity: String = "1",
        daysAgo: Long = 3,
        unit: String = "l",
        currency: String = "EUR",
    ) = PricedPurchase(
        productId = product,
        productName = product.replaceFirstChar { it.uppercase() },
        merchantId = shop,
        merchantName = shop.uppercase(),
        date = today.minusDays(daysAgo),
        unit = unit,
        currency = currency,
        unitPrice = BigDecimal(price),
        quantity = BigDecimal(quantity),
    )

    @Test
    fun `a product bought at two shops shows the cheaper one and what it would save`() {
        val board = priceBoard(
            listOf(
                line("oil", "lidl", "6.90", "2"),
                line("oil", "ab", "8.40", "1"),
                line("oil", "ab", "8.20", "2"),
            ),
            today,
        )
        val deal = board.byProduct.single()
        assertThat(deal.cheapest.merchantId).isEqualTo("lidl")
        assertThat(deal.cheapest.average.toPlainString()).isEqualTo("6.9000")
        // AB: (8.40 - 6.90) + 2 * (8.20 - 6.90).
        assertThat(deal.saving.toPlainString()).isEqualTo("4.10")
        assertThat(deal.others.map { it.average.toPlainString() }).containsExactly("8.2667")
        assertThat(board.saving.toPlainString()).isEqualTo("4.10")
        assertThat(board.currency).isEqualTo("EUR")
    }

    @Test
    fun `a product from one shop, in another unit, or from long ago is not compared`() {
        val board = priceBoard(
            listOf(
                line("milk", "lidl", "1.10"),
                line("milk", "lidl", "1.20"),
                line("eggs", "lidl", "0.30", unit = "piece"),
                line("eggs", "ab", "3.20", unit = "kg"),
                line("feta", "ab", "9.00", daysAgo = 120),
                line("feta", "lidl", "8.00", daysAgo = 3),
            ),
            today,
        )
        assertThat(board.byProduct).isEmpty()
        assertThat(board.byMarket).isEmpty()
        assertThat(board.saving.toPlainString()).isEqualTo("0.00")
    }

    @Test
    fun `each shop lists what it is cheapest for, the most products first`() {
        val board = priceBoard(
            listOf(
                line("oil", "lidl", "6.90"),
                line("oil", "ab", "8.40"),
                line("feta", "lidl", "8.00"),
                line("feta", "ab", "9.00"),
                line("coffee", "ab", "12.00", unit = "kg"),
                line("coffee", "lidl", "13.00", unit = "kg"),
            ),
            today,
        )
        assertThat(board.byMarket.map { it.merchantId }).containsExactly("lidl", "ab")
        assertThat(
            board.byMarket.first().products.map {
                it.productId
            },
        ).containsExactly("oil", "feta")
        assertThat(board.byMarket.first().saving.toPlainString()).isEqualTo("2.50")
    }

    @Test
    fun `trends compare the last 30 days with the 30 before`() {
        val board = priceBoard(
            listOf(
                line("oil", "lidl", "6.00", daysAgo = 40),
                line("oil", "lidl", "6.60", daysAgo = 5),
                line("feta", "lidl", "8.00", daysAgo = 45),
                line("feta", "lidl", "7.60", daysAgo = 2),
                line("milk", "lidl", "1.10", daysAgo = 70),
                line("milk", "lidl", "1.30", daysAgo = 3),
            ),
            today,
        )
        assertThat(board.productTrends.map { it.id to it.changePercent.toPlainString() })
            .containsExactly("oil" to "10.0", "feta" to "-5.0")
        val market = board.marketTrends.single()
        assertThat(market.changePercent.toPlainString()).isEqualTo("2.5")
        assertThat(market.products).isEqualTo(2)
        assertThat(market.previous).isNull()
    }

    @Test
    fun `only the currency most bought in is compared, and nothing gives an empty board`() {
        val board = priceBoard(
            listOf(
                line("oil", "lidl", "6.90"),
                line("oil", "ab", "8.40"),
                line("oil", "shop", "1.00", currency = "USD"),
            ),
            today,
        )
        assertThat(board.byProduct.single().others.map { it.merchantId }).containsExactly("ab")
        val empty = priceBoard(emptyList(), today)
        assertThat(empty.currency).isNull()
        assertThat(empty.productTrends).isEmpty()
        assertThat(empty.marketTrends).isEmpty()
    }
}
