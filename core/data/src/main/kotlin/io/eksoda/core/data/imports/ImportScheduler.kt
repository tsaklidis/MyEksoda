package io.eksoda.core.data.imports

import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
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
    /** A new or requeued job: runs now. An already queued run is kept. */
    fun enqueue(jobId: String) = enqueue(jobId, delayMillis = 0, ExistingWorkPolicy.KEEP)

    /**
     * The next attempt after a retryable failure (§7.3). Called from the running worker, so the
     * request is appended: the current run finishes first.
     */
    fun enqueueRetry(jobId: String, delayMillis: Long) =
        enqueue(jobId, delayMillis, ExistingWorkPolicy.APPEND_OR_REPLACE)

    private fun enqueue(jobId: String, delayMillis: Long, policy: ExistingWorkPolicy) {
        val request = OneTimeWorkRequestBuilder<ImportWorker>()
            .setInputData(workDataOf(ImportWorker.KEY_JOB_ID to jobId))
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
            )
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .build()
        workManager.enqueueUniqueWork("import-$jobId", policy, request)
    }
}
