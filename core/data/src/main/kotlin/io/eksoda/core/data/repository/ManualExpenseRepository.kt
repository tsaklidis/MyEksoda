package io.eksoda.core.data.repository

import io.eksoda.core.model.ManualExpense
import kotlinx.coroutines.flow.Flow

/**
 * Expenses typed in by hand: rent, the doctor, a paper receipt. Each is stored as a receipt with
 * no lines (`receipt.origin = 'manual'`) and its own category, so totals, Insights, budgets, the
 * receipt list, backups and household sync treat it like any other receipt. Deleting one is
 * [ReceiptRepository.deleteReceipt].
 */
interface ManualExpenseRepository {
    /** Saves [expense]; the new receipt's id. */
    suspend fun add(expense: ManualExpense): Result<String>

    /** Changes the manual expense [id]; an imported receipt is never changed. */
    suspend fun update(id: String, expense: ManualExpense): Result<Unit>

    /** Who manual expenses were paid to, most used first (suggestions for the payee). */
    val payees: Flow<List<String>>
}
