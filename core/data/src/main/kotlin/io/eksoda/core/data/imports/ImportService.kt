package io.eksoda.core.data.imports

import io.eksoda.core.data.db.WriteCoordinator
import io.eksoda.core.database.dao.ImportJobDao
import io.eksoda.core.database.entity.ImportJobEntity
import io.eksoda.core.model.ImportSubmission
import io.eksoda.engine.Engine
import io.eksoda.engine.url.parseSubmittedUrl
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Submitting receipt links (docs/android-standalone.md §7.2, the server's `imports.py`).
 * Nothing is fetched here: the job is stored and the worker is queued.
 */
@Singleton
class ImportService @Inject constructor(
    private val engine: Engine,
    private val jobs: ImportJobDao,
    private val coordinator: WriteCoordinator,
    private val scheduler: ImportScheduler,
    private val clock: Clock,
) {
    /**
     * Stores or reuses the job for this link's identity. Invalid, unsupported and unrecognized
     * links throw the engine's ReceiptException at once and store nothing.
     */
    suspend fun submit(rawUrl: String): ImportSubmission {
        val link = engine.resolve(rawUrl)
        val identity = link.identity
        val now = clock.millis()
        val submission = coordinator.write {
            val existing = jobs.findByIdentity(identity.providerCode, identity.identity)
            val importedReceipt = existing?.receiptId?.takeIf {
                existing.status == STATUS_COMPLETED && existing.outcome != OUTCOME_RECEIPT_DELETED
            }
            when {
                existing == null -> {
                    val job = ImportJobEntity(
                        id = UUID.randomUUID().toString(),
                        providerCode = identity.providerCode,
                        urlIdentity = identity.identity,
                        submittedUrl = identity.canonicalUrl,
                        urlFingerprint = identity.urlFingerprint,
                        sourceHost = parseSubmittedUrl(identity.canonicalUrl).host,
                        issuerVat = identity.issuerVat,
                        status = STATUS_PENDING,
                        outcome = null,
                        attemptCount = 0,
                        nextAttemptAt = now,
                        lastErrorCode = null,
                        lastErrorMessage = null,
                        receiptId = null,
                        finishedAt = null,
                        appVersionAtFailure = null,
                        createdAt = now,
                        updatedAt = now,
                    )
                    jobs.insert(job)
                    ImportSubmission.Queued(job.id)
                }

                importedReceipt != null -> ImportSubmission.AlreadyImported(importedReceipt)

                // Failed, or its receipt was deleted: start again with the newly submitted link.
                jobs.requeueResubmitted(
                    existing.id,
                    identity.canonicalUrl,
                    identity.urlFingerprint,
                    now,
                ) ==
                    1 ->
                    ImportSubmission.Queued(existing.id)

                else -> ImportSubmission.AlreadyPending(existing.id)
            }
        }
        if (submission is ImportSubmission.Queued) {
            scheduler.enqueue(submission.jobId, userInitiated = true)
        }
        return submission
    }

    /** `retry_import`: a failed job starts again with the full schedule. False when it is not failed. */
    suspend fun retry(jobId: String): Boolean {
        val requeued = coordinator.write { jobs.requeueFailed(jobId, clock.millis()) == 1 }
        if (requeued) scheduler.enqueue(jobId, userInitiated = true)
        return requeued
    }

    companion object {
        const val STATUS_PENDING = "pending"
        const val STATUS_COMPLETED = "completed"
        const val OUTCOME_RECEIPT_DELETED = "receipt_deleted"
    }
}
