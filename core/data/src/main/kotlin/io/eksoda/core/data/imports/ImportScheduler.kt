package io.eksoda.core.data.imports

import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Enqueues one ImportWorker run per job as unique work "import-<jobId>", needing a network
 * connection (§7.2). A job waits in `pending` while the phone is offline.
 */
@Singleton
class ImportScheduler @Inject constructor(private val workManager: WorkManager) {
    /**
     * A new or requeued job: runs now. An already queued run is kept. [userInitiated]: the user
     * just scanned, pasted, shared or retried it, so the run is expedited and starts at once
     * instead of whenever the scheduler gets to it (§7.2). Without expedited quota it runs as
     * normal work.
     */
    fun enqueue(jobId: String, userInitiated: Boolean = false) =
        enqueue(jobId, delayMillis = 0, ExistingWorkPolicy.KEEP, expedited = userInitiated)

    /**
     * The next attempt after a retryable failure (§7.3). Called from the running worker, so the
     * request is appended: the current run finishes first.
     */
    fun enqueueRetry(jobId: String, delayMillis: Long) =
        enqueue(jobId, delayMillis, ExistingWorkPolicy.APPEND_OR_REPLACE)

    private fun enqueue(
        jobId: String,
        delayMillis: Long,
        policy: ExistingWorkPolicy,
        expedited: Boolean = false,
    ) {
        val builder = OneTimeWorkRequestBuilder<ImportWorker>()
            .setInputData(workDataOf(ImportWorker.KEY_JOB_ID to jobId))
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
            )
        // Expedited work cannot be delayed; retries are delayed and never expedited.
        if (expedited) {
            builder.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        } else {
            builder.setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
        }
        workManager.enqueueUniqueWork("import-$jobId", policy, builder.build())
    }
}
