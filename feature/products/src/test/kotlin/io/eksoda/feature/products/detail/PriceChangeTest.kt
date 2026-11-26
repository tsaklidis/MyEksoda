package io.eksoda.feature.products.detail

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import io.eksoda.core.model.Merchant
import io.eksoda.core.model.MerchantPrices
import io.eksoda.core.model.PriceStats
import org.junit.Test
import java.math.BigDecimal

class PriceChangeTest {
    private fun stats(latest: String, previous: String?, average: String = latest) = PriceStats(
        normalizedUnit = "kg",
        currency = "EUR",
        count = if (previous == null) 1 else 2,
        latest = BigDecimal(latest),
        previous = previous?.let(::BigDecimal),
        minimum = BigDecimal(latest),
        maximum = BigDecimal(latest),
        average = BigDecimal(average),
    )

    @Test
    fun `the same price as last time shows no change`() {
        assertThat(priceChange(stats("9.95", "9.95"))).isNull()
        assertThat(priceChange(stats("9.95", "9.950"))).isNull()
        // Both show 9,94: no 0,00 change, whatever places the statistics carry.
        assertThat(priceChange(stats("9.9449", "9.9440"))).isNull()
    }

    @Test
    fun `a change is measured between the prices as shown, in cents`() {
        // 9,96 against 9,94.
        assertThat(priceChange(stats("9.955", "9.9449")))
            .isEqualTo(PriceChange.Up(BigDecimal("0.02")))
    }

    @Test
    fun `a dearer or cheaper price shows by how much`() {
        assertThat(priceChange(stats("10.20", "9.95")))
            .isEqualTo(PriceChange.Up(BigDecimal("0.25")))
        assertThat(priceChange(stats("9.70", "9.95")))
            .isEqualTo(PriceChange.Down(BigDecimal("0.25")))
    }

    @Test
    fun `the first purchase has nothing to compare with`() {
        assertThat(priceChange(stats("9.95", null))).isEqualTo(PriceChange.First)
    }

    private fun merchant(id: String, rank: Int, average: String) =
        MerchantPrices(Merchant(id, id), rank, stats(average, null, average))

    @Test
    fun `merchants with the same average have no cheapest gap`() {
        assertThat(cheapestGap(listOf(merchant("a", 1, "9.95"), merchant("b", 2, "9.95"))))
            .isNull()
        val gap = cheapestGap(listOf(merchant("a", 1, "9.80"), merchant("b", 2, "9.95")))
        assertThat(gap).isNotNull()
        assertThat(gap?.difference?.toPlainString()).isEqualTo("0.15")
        assertThat(cheapestGap(listOf(merchant("a", 1, "9.80")))).isNull()
    }
}
