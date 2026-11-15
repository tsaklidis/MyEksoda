package io.eksoda.core.data.repository

import androidx.paging.PagingData
import io.eksoda.core.model.ImportJob
import io.eksoda.core.model.ImportSubmission
import io.eksoda.core.model.Option
import io.eksoda.core.model.Receipt
import io.eksoda.core.model.ReceiptDetail
import io.eksoda.core.model.ReceiptFilter
import kotlinx.coroutines.flow.Flow

/** Receipts and their import jobs, all local (docs/android-standalone.md §7, L3 and L4). */
interface ReceiptRepository {
    fun getReceipts(filter: ReceiptFilter): Flow<PagingData<Receipt>>

    fun getReceipt(id: String): Flow<ReceiptDetail?>

    /**
     * Saves an import job for a receipt link and queues it (§7.2). A link Eksoda cannot read
     * gives [ImportSubmission.Rejected] and stores nothing.
     */
    suspend fun importReceipt(url: String): Result<ImportSubmission>

    /** Import jobs, newest first. Updates by itself when a job changes (no polling, §7.2). */
    fun getImportJobs(): Flow<List<ImportJob>>

    suspend fun retryImport(id: String): Result<Unit>

    suspend fun deleteReceipt(id: String): Result<Unit>

    suspend fun setNote(id: String, note: String): Result<Unit>

    suspend fun setExcludedFromAnalytics(id: String, excluded: Boolean): Result<Unit>

    /** One of the receipt's merchant's stores, or none. */
    suspend fun setStore(id: String, storeId: String?): Result<Unit>

    /**
     * The receipt link, for "Open original" only. It works like a password: open it in a Custom
     * Tab after the user confirms; never log, copy or share it (§8.1).
     */
    suspend fun originalLink(id: String): String?

    fun merchantOptions(): Flow<List<Option>>

    fun storeOptions(merchantId: String): Flow<List<Option>>

    /** System categories, with their slug as the name (shown translated). */
    fun categoryOptions(): Flow<List<Option>>

    suspend fun searchProducts(query: String): List<Option>

    /** The merchant of a receipt, for its store picker. */
    suspend fun merchantOf(receiptId: String): String?
}
