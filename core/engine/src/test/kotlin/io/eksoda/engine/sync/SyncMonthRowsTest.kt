package io.eksoda.engine.sync

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import io.eksoda.engine.sync.SyncRows.budget
import io.eksoda.engine.sync.SyncRows.category
import io.eksoda.engine.sync.SyncRows.household
import io.eksoda.engine.sync.SyncRows.item
import io.eksoda.engine.sync.SyncRows.mapping
import io.eksoda.engine.sync.SyncRows.merchant
import io.eksoda.engine.sync.SyncRows.product
import io.eksoda.engine.sync.SyncRows.receipt
import io.eksoda.engine.sync.SyncRows.snapshot
import io.eksoda.engine.sync.SyncRows.tombstone
import org.junit.jupiter.api.Test

/** Rows for one month (budgets, bills): their id follows their scope and month. */
class SyncMonthRowsTest {
    @Test
    fun `a month's limit follows its category and keeps its month in the id`() {
        val local = snapshot(
            "category" to listOf(category("cB", "pets")),
            "budget" to listOf(
                budget("cB:2026-10", category = "cB", amount = null, month = "2026-10"),
            ),
        )
        val remote = snapshot("category" to listOf(category("cA", "pets")))

        val merged = SyncMerge.merge(local, remote)

        assertThat(merged.ids("budget")).containsExactly("cA:2026-10")
        assertThat(merged.row("budget", "cA:2026-10")["amount_minor"]).isNull()
    }

    @Test
    fun `the same month of one bill is one row, the later one wins`() {
        val local = snapshot(
            "bill" to listOf(bill("b1")),
            "bill_month" to listOf(billMonth("b1:2026-11", "b1", "2026-11", 9_200)),
        )
        val remote = snapshot(
            "bill" to listOf(bill("b1")),
            "bill_month" to listOf(billMonth("b1:2026-11", "b1", "2026-11", 9_500, updatedAt = 4)),
        )

        val merged = SyncMerge.merge(local, remote)

        assertThat(merged.ids("bill_month")).containsExactly("b1:2026-11")
        assertThat(merged.row("bill_month", "b1:2026-11")["amount_minor"]).isEqualTo(9_500L)
    }

    private fun bill(id: String, name: String = "Rent", updatedAt: Long = 1) = mapOf(
        "id" to id,
        "name" to name,
        "kind" to "fixed",
        "amount_minor" to 65_000L,
        "start_month" to "2026-09",
        "end_month" to null,
        "position" to 0L,
        "updated_at" to updatedAt,
    )

    private fun billMonth(
        id: String,
        bill: String,
        month: String,
        amount: Long?,
        updatedAt: Long = 1,
    ) = mapOf(
        "id" to id,
        "bill_id" to bill,
        "month" to month,
        "amount_minor" to amount,
        "updated_at" to updatedAt,
    )
}
