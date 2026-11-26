package io.eksoda.core.data.repository

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import io.eksoda.core.database.dao.PriceRow
import org.junit.Test
import java.time.ZoneOffset

class PriceRowMappingTest {
    /** 0.5 kg for 4.97: 9.9400 per kg, stored with 4 places. */
    private fun row(priceE4: Long? = 99_400L, unit: String? = "kg") = PriceRow(
        id = "i1",
        receiptId = "r1",
        issuedAt = 0L,
        merchantId = "m1",
        merchantName = "MyMarket",
        storeId = null,
        storeName = null,
        storeStreet = null,
        storeCity = null,
        description = "ΤΥΡΙ",
        quantityE4 = 5_000L,
        unit = "kg",
        grossAmountMinor = 497L,
        currency = "EUR",
        normalizedQuantityE6 = 500_000L,
        normalizedUnit = unit,
        normalizedUnitPriceE4 = priceE4,
    )

    @Test
    fun `the history and the statistics see the unit price in cents`() {
        val stored = row(priceE4 = 99_449L) // 9.9449
        assertThat(stored.toEntry(ZoneOffset.UTC).normalizedUnitPrice?.toPlainString())
            .isEqualTo("9.94")
        assertThat(stored.toObservation()?.normalizedUnitPrice?.toPlainString())
            .isEqualTo("9.94")
    }

    @Test
    fun `a row without a unit price or a unit has no price to compare`() {
        assertThat(row(priceE4 = null).toEntry(ZoneOffset.UTC).normalizedUnitPrice).isNull()
        assertThat(row(priceE4 = null).toObservation()).isNull()
        assertThat(row(unit = null).toObservation()).isNull()
    }
}
