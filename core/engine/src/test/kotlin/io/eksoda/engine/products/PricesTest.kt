package io.eksoda.engine.products

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import io.eksoda.engine.classification.LineType
import io.eksoda.engine.mydata.Unit
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class PricesTest {
    private fun line(
        quantity: String?,
        unit: Unit?,
        gross: String,
        type: LineType = LineType.PRODUCT,
    ) = PricedLine(type, quantity?.let(::BigDecimal), unit, BigDecimal(gross))

    private val noPackage = ProductPackage(null, null)

    @Test
    fun `a weighed line is priced per kg or l`() {
        val price = checkNotNull(normalize(line("1.25", Unit.KG, "5.00"), noPackage))
        assertThat(price.quantity.toPlainString()).isEqualTo("1.250000")
        assertThat(price.unit).isEqualTo(NormalizedUnit.KG)
        assertThat(price.unitPrice?.toPlainString()).isEqualTo("4.0000")
        assertThat(normalize(line("0.5", Unit.L, "1.00"), noPackage)?.unit)
            .isEqualTo(NormalizedUnit.L)
    }

    @Test
    fun `a piece with a package size is priced per base unit`() {
        val grams = ProductPackage(BigDecimal("500"), "g")
        val price = normalize(line("2", Unit.PIECE, "3.00"), grams)
        assertThat(price?.quantity?.toPlainString()).isEqualTo("1.000000")
        assertThat(price?.unit).isEqualTo(NormalizedUnit.KG)
        assertThat(price?.unitPrice?.toPlainString()).isEqualTo("3.0000")
        // A package size without its unit is a data error.
        val noUnit = ProductPackage(BigDecimal.ONE, null)
        assertFailure { normalize(line("1", Unit.PIECE, "1"), noUnit) }
            .isInstanceOf<IllegalArgumentException>()
    }

    @Test
    fun `pieces without a package and other units keep their own unit`() {
        val piece = normalize(line("3", Unit.PIECE, "6.00"), ProductPackage(BigDecimal.ZERO, "g"))
        assertThat(piece?.unit).isEqualTo(NormalizedUnit.PIECE)
        assertThat(piece?.unitPrice?.toPlainString()).isEqualTo("2.0000")
        assertThat(normalize(line("1", Unit.OTHER, "6.00"), noPackage)?.unit)
            .isEqualTo(NormalizedUnit.OTHER)
        assertThat(normalize(line("1", null, "6.00"), noPackage)?.unit)
            .isEqualTo(NormalizedUnit.OTHER)
    }

    @Test
    fun `no price without a product line, a product, a quantity or a non-zero quantity`() {
        assertThat(normalize(line("1", Unit.KG, "-1", LineType.DISCOUNT), noPackage)).isNull()
        assertThat(normalize(line("1", Unit.KG, "1"), null)).isNull()
        assertThat(normalize(line(null, Unit.KG, "1"), noPackage)).isNull()
        val zero = normalize(line("0", Unit.KG, "1"), noPackage)
        assertThat(zero).isNotNull()
        assertThat(zero?.unitPrice).isNull()
    }

    @Test
    fun `units convert to their base unit`() {
        assertThat(toBase(BigDecimal("500"), "ml").let { it.first.toPlainString() to it.second })
            .isEqualTo("0.500" to "l")
        assertThat(toBase(BigDecimal("33"), "cl").second).isEqualTo("l")
        assertThat(toBase(BigDecimal("250"), "mg").second).isEqualTo("kg")
        assertThat(toBase(BigDecimal("2"), "piece").second).isEqualTo("piece")
        assertFailure { toBase(BigDecimal.ONE, "oz") }.isInstanceOf<IllegalArgumentException>()
        assertThat(NormalizedUnit.fromCode("piece")).isEqualTo(NormalizedUnit.PIECE)
    }

    // --- Statistics and comparison -------------------------------------------------------------

    private fun observation(
        merchant: String,
        unit: String,
        price: String,
        gross: String = price,
        quantity: String = "1",
    ) = PriceObservation(
        merchantId = merchant,
        merchantName = merchant.uppercase(),
        normalizedUnit = unit,
        currency = "EUR",
        normalizedUnitPrice = BigDecimal(price),
        grossAmount = BigDecimal(gross),
        normalizedQuantity = BigDecimal(quantity),
    )

    /** Newest first. */
    private val entries = listOf(
        observation("m1", "kg", "2.00"),
        observation("m2", "kg", "1.50", gross = "3.00", quantity = "2"),
        observation("m1", "kg", "2.50"),
        observation("m1", "piece", "1.00"),
    )

    @Test
    fun `statistics are per unit and currency, the average weighted by quantity`() {
        val (kg, piece) = priceStats(entries)
        assertThat(kg.normalizedUnit).isEqualTo("kg")
        assertThat(kg.count).isEqualTo(3)
        assertThat(kg.latest.toPlainString()).isEqualTo("2.00")
        assertThat(kg.previous?.toPlainString()).isEqualTo("1.50")
        assertThat(kg.minimum.toPlainString()).isEqualTo("1.50")
        assertThat(kg.maximum.toPlainString()).isEqualTo("2.50")
        assertThat(kg.average.toPlainString()).isEqualTo("1.8750")
        assertThat(piece.count).isEqualTo(1)
        assertThat(piece.previous).isNull()
    }

    @Test
    fun `merchants are ranked by average in the unit with the most entries`() {
        val comparison = compare(entries)
        assertThat(comparison.comparable).isTrue()
        assertThat(comparison.reason).isNull()
        assertThat(comparison.normalizedUnit).isEqualTo("kg")
        assertThat(comparison.currency).isEqualTo("EUR")
        val ranked = comparison.merchants.map {
            Triple(it.merchantId, it.rank, it.stats.average.toPlainString())
        }
        assertThat(ranked).containsExactly(Triple("m2", 1, "1.5000"), Triple("m1", 2, "2.2500"))
        assertThat(comparison.availableUnits).containsExactly(
            AvailableUnit("kg", "EUR", 3),
            AvailableUnit("piece", "EUR", 1),
        )
    }

    @Test
    fun `a comparison needs prices, a known unit and two merchants`() {
        val piece = compare(entries, "piece")
        assertThat(piece.comparable).isFalse()
        assertThat(piece.reason).isEqualTo(ONE_MERCHANT)
        assertThat(piece.merchants.single().rank).isNull()

        val missing = compare(entries, "l")
        assertThat(missing.reason).isEqualTo(NO_PRICES)
        assertThat(missing.normalizedUnit).isEqualTo("l")
        assertThat(missing.currency).isNull()
        assertThat(compare(emptyList()).reason).isEqualTo(NO_PRICES)

        val unknown = listOf(observation("m1", "other", "1"), observation("m2", "other", "2"))
        assertThat(compare(unknown).reason).isEqualTo(UNKNOWN_UNIT)
    }

    @Test
    fun `equally large groups go to the greater unit`() {
        val tied = listOf(observation("m1", "kg", "1"), observation("m1", "l", "1"))
        assertThat(compare(tied).normalizedUnit).isEqualTo("l")
    }
}
