package io.eksoda.core.database.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import io.eksoda.core.database.entity.ImportJobEntity
import kotlinx.coroutines.flow.Flow

/**
 * An import job as the imports list shows it, with its receipt's merchant and total once
 * imported. Only the columns the list needs: never the `submitted_url` credential (§8.1).
 */
data class ImportJobRow(
    val id: String,
    @ColumnInfo(name = "provider_code") val providerCode: String,
    @ColumnInfo(name = "source_host") val sourceHost: String,
    val status: String,
    val outcome: String?,
    @ColumnInfo(name = "next_attempt_at") val nextAttemptAt: Long?,
    @ColumnInfo(name = "last_error_code") val lastErrorCode: String?,
    @ColumnInfo(name = "receipt_id") val receiptId: String?,
    @ColumnInfo(name = "finished_at") val finishedAt: Long?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "merchant_name") val merchantName: String?,
    @ColumnInfo(name = "total_minor") val totalMinor: Long?,
    val currency: String?,
)

/**
 * Import jobs (`imports.py`, `pipeline.py`). State changes are conditional updates on the
 * current status, so a second worker or a stale screen cannot overwrite a newer state; callers
 * check the returned row count.
 */
@Dao
interface ImportJobDao {
    @Insert
    suspend fun insert(job: ImportJobEntity)

    @Query("SELECT * FROM import_job WHERE id = :id")
    suspend fun get(id: String): ImportJobEntity?

    @Query(
        "SELECT * FROM import_job WHERE provider_code = :providerCode AND url_identity = :urlIdentity",
    )
    suspend fun findByIdentity(providerCode: String, urlIdentity: String): ImportJobEntity?

    /** Jobs newest first, with the merchant and total of an imported receipt. */
    @Query(
        """SELECT import_job.id, import_job.provider_code, import_job.source_host,
           import_job.status, import_job.outcome, import_job.next_attempt_at,
           import_job.last_error_code, import_job.receipt_id, import_job.finished_at,
           import_job.created_at, merchant.display_name AS merchant_name,
           receipt.gross_total_minor AS total_minor, receipt.currency AS currency
           FROM import_job LEFT JOIN receipt ON receipt.id = import_job.receipt_id
           LEFT JOIN merchant ON merchant.id = receipt.merchant_id
           ORDER BY import_job.created_at DESC""",
    )
    fun observeAll(): Flow<List<ImportJobRow>>

    @Query("SELECT * FROM import_job WHERE status = 'pending'")
    suspend fun pending(): List<ImportJobEntity>

    /**
     * `_requeue` for a resubmission: failed jobs and jobs whose receipt was deleted start again
     * with the full schedule, with the newly submitted link.
     */
    @Query(
        """UPDATE import_job SET status = 'pending', outcome = NULL, attempt_count = 0,
           next_attempt_at = :now, last_error_code = NULL, last_error_message = NULL,
           finished_at = NULL, submitted_url = :submittedUrl, url_fingerprint = :urlFingerprint,
           updated_at = :now
           WHERE id = :id AND (status = 'failed' OR (status = 'completed' AND outcome = 'receipt_deleted'))""",
    )
    suspend fun requeueResubmitted(
        id: String,
        submittedUrl: String,
        urlFingerprint: String,
        now: Long,
    ): Int

    /** `retry_import`: a failed job starts again with the full schedule. */
    @Query(
        """UPDATE import_job SET status = 'pending', outcome = NULL, attempt_count = 0,
           next_attempt_at = :now, last_error_code = NULL, last_error_message = NULL,
           finished_at = NULL, updated_at = :now
           WHERE id = :id AND status = 'failed'""",
    )
    suspend fun requeueFailed(id: String, now: Long): Int

    /** `claim`: count the attempt of a pending job. */
    @Query(
        """UPDATE import_job SET attempt_count = attempt_count + 1, updated_at = :now
           WHERE id = :id AND status = 'pending'""",
    )
    suspend fun countAttempt(id: String, now: Long): Int

    @Query(
        """UPDATE import_job SET status = 'completed', outcome = :outcome, receipt_id = :receiptId,
           issuer_vat = :issuerVat, next_attempt_at = NULL, last_error_code = NULL,
           last_error_message = NULL, finished_at = :now, updated_at = :now
           WHERE id = :id AND status = 'pending'""",
    )
    suspend fun complete(
        id: String,
        outcome: String,
        receiptId: String,
        issuerVat: String,
        now: Long,
    ): Int

    /** A retryable failure with schedule left: the job stays pending until [nextAttemptAt]. */
    @Query(
        """UPDATE import_job SET next_attempt_at = :nextAttemptAt, last_error_code = :code,
           last_error_message = :message, updated_at = :now
           WHERE id = :id AND status = 'pending'""",
    )
    suspend fun scheduleRetry(
        id: String,
        nextAttemptAt: Long,
        code: String,
        message: String,
        now: Long,
    ): Int

    @Query(
        """UPDATE import_job SET status = 'failed', next_attempt_at = NULL, last_error_code = :code,
           last_error_message = :message, finished_at = :now, app_version_at_failure = :appVersion,
           updated_at = :now
           WHERE id = :id AND status = 'pending'""",
    )
    suspend fun fail(id: String, code: String, message: String, appVersion: Long, now: Long): Int

    /** Failed jobs a new app version may fix (§7.3): the listed codes, failed with an older version. */
    @Query(
        """SELECT * FROM import_job WHERE status = 'failed' AND last_error_code IN (:codes)
           AND (app_version_at_failure IS NULL OR app_version_at_failure < :appVersion)""",
    )
    suspend fun failedBeforeVersion(codes: List<String>, appVersion: Long): List<ImportJobEntity>

    /** `delete_receipt`: the receipt's jobs are kept and marked, so resubmitting imports it again. */
    @Query(
        "UPDATE import_job SET outcome = 'receipt_deleted', updated_at = :now WHERE receipt_id = :receiptId",
    )
    suspend fun markReceiptDeleted(receiptId: String, now: Long)
}
