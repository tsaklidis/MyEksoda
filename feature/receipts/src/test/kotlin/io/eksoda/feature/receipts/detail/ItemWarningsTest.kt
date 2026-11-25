package io.eksoda.feature.receipts.detail

import assertk.assertThat
import assertk.assertions.containsOnly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import io.eksoda.core.model.LineType
import io.eksoda.core.model.MatchStatus
import io.eksoda.core.model.Money
import io.eksoda.core.model.ReceiptItem
import io.eksoda.core.model.ReceiptWarning
import io.eksoda.feature.receipts.R
import org.junit.Test
import java.math.BigDecimal

/** The warnings shown as a mark on an item's row. */
class ItemWarningsTest {
    private val milk = item(1, "ΓΑΛΑ")
    private val bread = item(2, "ΨΩΜΙ")
    private val cheese = item(2, "ΤΥΡΙ")
    private val water = item(4, "ΝΕΡΟ")

    @Test
    fun `each line's item, but not when two items share a line number`() {
        assertThat(lineItems(listOf(milk, bread, cheese, water)))
            .isEqualTo(mapOf(1 to milk, 4 to water))
    }

    @Test
    fun `an item is marked with its own warnings, grouped`() {
        val marks = itemWarnings(
            listOf(
                ReceiptWarning("unit_price_mismatch", 1),
                ReceiptWarning("vat_rate_mismatch", 1),
                ReceiptWarning("unit_price_mismatch", 1),
                ReceiptWarning("unit_price_mismatch", 4),
                ReceiptWarning("total_mismatch", null),
            ),
            lineItems(listOf(milk, water)),
        )
        assertThat(marks).isEqualTo(
            mapOf(
                milk.id to listOf(
                    LineWarning(R.string.warning_unit_price, amountCheck = true),
                    LineWarning(R.string.warning_vat_rate, amountCheck = true),
                ),
                water.id to listOf(LineWarning(R.string.warning_unit_price, amountCheck = true)),
            ),
        )
    }

    @Test
    fun `without an item list nothing is marked`() {
        val warnings = listOf(ReceiptWarning("unit_price_mismatch", 1))
        assertThat(itemWarnings(warnings, byLine = emptyMap())).isEmpty()
    }

    @Test
    fun `Show scrolls to the first marked item of the warning`() {
        val items = listOf(milk, bread, cheese, water)
        val marked = lineItems(items).keys
        // Line 2 is shared (not marked), so the first marked item on lines 2 and 4 is water.
        assertThat(firstMarkedItem(items, listOf(2, 4), marked)).isEqualTo(3)
        assertThat(firstMarkedItem(items, listOf(4, 1), marked)).isEqualTo(0)
        assertThat(firstMarkedItem(items, listOf(2, 9), marked)).isNull()
        assertThat(firstMarkedItem(items, listOf(1), emptySet())).isNull()
    }

    @Test
    fun `a warning's lines are counted when marked and named by number otherwise`() {
        val marked = lineItems(listOf(milk, bread, cheese, water)).keys
        // Line 2 is shared, line 9 has no item.
        assertThat(lineMarks(listOf(1, 2, 4, 9), marked)).isEqualTo(LineMarks(2, listOf(2, 9)))
        assertThat(lineMarks(listOf(1, 4), marked)).isEqualTo(LineMarks(2, emptyList()))
        // A receipt that shows no item list marks nothing: every line is named by number.
        assertThat(lineMarks(listOf(1, 4), emptySet())).isEqualTo(LineMarks(0, listOf(1, 4)))
        assertThat(lineMarks(emptyList(), marked)).isEqualTo(LineMarks(0, emptyList()))
    }

    @Test
    fun `receipt-wide warnings, unknown lines and shared line numbers mark no item`() {
        val marks = itemWarnings(
            listOf(
                ReceiptWarning("total_mismatch", null),
                // No item has line 9.
                ReceiptWarning("unit_price_mismatch", 9),
                // Two items share line 2.
                ReceiptWarning("unit_price_mismatch", 2),
            ),
            lineItems(listOf(milk, bread, cheese)),
        )
        assertThat(marks).isEmpty()
    }

    @Test
    fun `a notice on a line is marked for attention`() {
        val marks =
            itemWarnings(listOf(ReceiptWarning("something_new", 1)), lineItems(listOf(milk)))
        assertThat(marks.getValue(milk.id).map { it.amountCheck }).containsOnly(false)
    }

    private fun item(line: Int, description: String) = ReceiptItem(
        id = "i$line$description",
        lineNumber = line,
        lineType = LineType.PRODUCT,
        description = description,
        quantity = BigDecimal.ONE,
        unit = "piece",
        unitPrice = null,
        totalPrice = Money(BigDecimal.ONE, "EUR"),
        matchStatus = MatchStatus.NEEDS_REVIEW,
        productId = null,
        productName = null,
        categorySlug = null,
        normalizedUnitPrice = null,
        normalizedUnit = null,
    )
}
