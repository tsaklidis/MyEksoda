package io.eksoda.core.data.repository

import androidx.paging.PagingData
import io.eksoda.core.model.ImportJob
import io.eksoda.core.model.ImportSubmission
import io.eksoda.core.model.Receipt
import io.eksoda.core.model.ReceiptFilter
import kotlinx.coroutines.flow.Flow

/** Receipts and their import jobs, all local (docs/android-standalone.md §7, L3 and L4). */
interface ReceiptRepository {
    fun getReceipts(filter: ReceiptFilter): Flow<PagingData<Receipt>>

    fun getReceipt(id: String): Flow<Receipt?>

    /**
     * Saves an import job for a receipt link and queues it (§7.2). Invalid or
     * unsupported links fail at once and store nothing.
     */
    suspend fun importReceipt(url: String): Result<ImportSubmission>

    /** Import jobs, newest first. Updates by itself when a job changes (no polling, §7.2). */
    fun getImportJobs(): Flow<List<ImportJob>>

    suspend fun retryImport(id: String): Result<Unit>

    suspend fun deleteReceipt(id: String): Result<Unit>
}
