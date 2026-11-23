package io.eksoda.core.data.repository

import io.eksoda.core.model.ManualExpense
import kotlinx.coroutines.flow.Flow

/**
 * Expenses typed in by hand: the doctor, a plumber, a paper receipt (bills such as rent have
 * their own [BillRepository]). Each is stored as a receipt with no lines (`receipt.origin =
 * 'manual'`), its kind and, when picked, its own category, so totals, spending, budgets, the
 * receipt list, backups and household sync treat it like any other receipt. Deleting one is
 * [ReceiptRepository.deleteReceipt].
 */
interface ManualExpenseRepository {
    /** Saves [expense]; the new receipt's id. */
    suspend fun add(expense: ManualExpense): Result<String>

    /** Changes the manual expense [id]; an imported receipt is never changed. */
    suspend fun update(id: String, expense: ManualExpense): Result<Unit>

    /** What manual expenses were, most used first (suggestions for the description). */
    val descriptions: Flow<List<String>>
}
