package io.eksoda.engine.analytics

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

class CombineTest {
    private fun d(value: String) = BigDecimal(value)

    @Test
    fun `buckets add up to spending, with remainders in the default category`() {
        val (buckets, spending) = combineCategoryBuckets(
            lines = listOf(
                LineTotal("product", "dairy", d("10.00")),
                LineTotal("product", null, d("5.00")),
                LineTotal("discount", null, d("-1.00")),
                LineTotal("fee", null, d("0.08")),
            ),
            receiptsByDefault = listOf(
                DefaultCategoryTotal(null, d("14.08")),
                DefaultCategoryTotal("fuel", d("40.00")),
            ),
            linesByDefault = listOf(DefaultCategoryTotal(null, d("14.08"))),
        )
        assertThat(spending).isEqualTo(d("54.08"))
        assertThat(buckets).containsExactlyInAnyOrder(
            BucketTotal(CATEGORY, "dairy", d("10.00")),
            BucketTotal(UNCATEGORIZED, null, d("5.00")),
            BucketTotal(DISCOUNTS, null, d("-1.00")),
            BucketTotal(FEES, null, d("0.08")),
            BucketTotal(CATEGORY, "fuel", d("40.00")),
        )
        val sum = buckets.fold(BigDecimal.ZERO) { total, bucket -> total + bucket.total }
        assertThat(sum).isEqualTo(spending)
    }

    @Test
    fun `a summary-only receipt without a default category is unallocated`() {
        val (buckets, _) = combineCategoryBuckets(
            lines = emptyList(),
            receiptsByDefault = listOf(DefaultCategoryTotal(null, d("12.00"))),
            linesByDefault = emptyList(),
        )
        assertThat(buckets).containsExactly(BucketTotal(UNALLOCATED, null, d("12.00")))
    }

    @Test
    fun `running totals stop after the day given`() {
        val month = YearMonth.of(2026, 2)
        val running = runningTotals(
            mapOf(LocalDate.of(2026, 2, 1) to d("3"), LocalDate.of(2026, 2, 3) to d("2")),
            month,
            through = LocalDate.of(2026, 2, 3),
        )
        assertThat(running.size).isEqualTo(28)
        assertThat(running.take(4)).containsExactly(d("3"), d("3"), d("5"), null)
    }

    @Test
    fun `the same day is capped at the shorter month`() {
        val february = runningTotals(
            mapOf(LocalDate.of(2026, 2, 28) to d("7")),
            YearMonth.of(2026, 2),
        )
        assertThat(totalBySameDay(february, 31)).isEqualTo(d("7"))
        assertThat(totalBySameDay(listOf(d("1"), d("4")), 31)).isEqualTo(d("4"))
    }
}
