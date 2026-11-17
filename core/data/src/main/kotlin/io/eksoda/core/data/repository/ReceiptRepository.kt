package io.eksoda.core.data.repository

import androidx.paging.PagingData
import io.eksoda.core.model.ImportJob
import io.eksoda.core.model.ImportSubmission
import io.eksoda.core.model.Receipt
import io.eksoda.core.model.ReceiptDetail
import io.eksoda.core.model.ReceiptFilter
import kotlinx.coroutines.flow.Flow

/** Receipts and their import jobs, all local. */
interface ReceiptRepository {
    fun getReceipts(filter: ReceiptFilter): Flow<PagingData<Receipt>>

    fun getReceipt(id: String): Flow<ReceiptDetail?>

    /** The [limit] newest receipts (Home). */
    fun getRecentReceipts(limit: Int): Flow<List<Receipt>>

    /**
     * Saves an import job for a receipt link and queues it. A link Eksoda cannot read
     * gives [ImportSubmission.Rejected] and stores nothing.
     */
    suspend fun importReceipt(url: String): Result<ImportSubmission>

    /** Import jobs, newest first. Updates by itself when a job changes (no polling). */
    fun getImportJobs(): Flow<List<ImportJob>>

    suspend fun retryImport(id: String): Result<Unit>

    suspend fun deleteReceipt(id: String): Result<Unit>

    suspend fun setNote(id: String, note: String): Result<Unit>

    suspend fun setExcludedFromAnalytics(id: String, excluded: Boolean): Result<Unit>

    /** One of the receipt's merchant's stores, or none. */
    suspend fun setStore(id: String, storeId: String?): Result<Unit>

    /**
     * The receipt link, for "Open original" only. It works like a password: open it in a Custom
     * Tab after the user confirms; never log, copy or share it.
     */
    suspend fun originalLink(id: String): String?
}
