package io.eksoda.core.data.repository

import io.eksoda.core.model.BillKind
import io.eksoda.core.model.BillsMonth
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.time.YearMonth

/**
 * The bills paid every month (rent, electricity, internet), kept apart from receipts: they never
 * count in spending or the everyday budget. A fixed bill has an amount for every month that can
 * differ in the current month; a variable one is entered each month. Only the current month can
 * be changed, and last month's variable bills until the 10th (core:engine `MonthAmounts.kt`).
 */
interface BillRepository {
    /** [month]'s bills, in their order. */
    fun getMonth(month: YearMonth): Flow<BillsMonth>

    /** The current month. */
    suspend fun currentMonth(): YearMonth

    /**
     * Adds a bill paid from this month on: [amount] is a fixed bill's every month (needed) or a
     * variable one's usual amount (may be null). The new bill's id.
     */
    suspend fun add(name: String, kind: BillKind, amount: BigDecimal?): Result<String>

    /**
     * Changes bill [id]. A new amount applies from this month on: past months keep what they
     * had.
     */
    suspend fun update(id: String, name: String, kind: BillKind, amount: BigDecimal?): Result<Unit>

    /**
     * Sets bill [id]'s amount in [month] (refused for a month that can no longer change). A null
     * [amount] takes a fixed bill back to its usual amount, or clears a variable one's.
     */
    suspend fun setAmount(id: String, month: YearMonth, amount: BigDecimal?): Result<Unit>

    /**
     * Stops bill [id] from this month on. A bill already paid in past months stays in them; one
     * added this month goes entirely.
     */
    suspend fun remove(id: String): Result<Unit>
}
