package io.eksoda.core.data.imports

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.eksoda.core.data.db.WriteCoordinator
import io.eksoda.core.data.repository.HouseholdZone
import io.eksoda.core.database.dao.ImportJobDao
import io.eksoda.engine.Engine
import io.eksoda.engine.InternalError
import io.eksoda.engine.ReceiptException
import io.eksoda.engine.RetryPolicy
import kotlinx.coroutines.CancellationException
import java.time.Clock
import kotlin.random.Random

/**
 * Processes one import job: claim → fetch → parse → validate
 * → persist and complete in one transaction → notify. A failure is recorded: the next attempt is
 * scheduled for retryable errors while the schedule lasts, otherwise the job fails. It
 * never returns `Result.retry()`; WorkManager's backoff cannot express the schedule.
 *
 * There is no lease: WorkManager's unique work and the WriteCoordinator mean one worker per job.
 * Completing and failing update only jobs that are still pending, so a second run is harmless.
 */
@HiltWorker
class ImportWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted parameters: WorkerParameters,
    private val engine: Engine,
    private val jobs: ImportJobDao,
    private val persister: Persister,
    private val coordinator: WriteCoordinator,
    private val scheduler: ImportScheduler,
    private val notifier: ImportNotifier,
    private val householdZone: HouseholdZone,
    private val appVersion: AppVersion,
    private val clock: Clock,
) : CoroutineWorker(context, parameters) {

    /**
     * Below Android 12 WorkManager runs expedited work as a foreground service, which needs a
     * notification ("Importing receipt…", never the URL). Android 12+ does not call this.
     */
    override suspend fun getForegroundInfo(): ForegroundInfo = notifier.progress()

    override suspend fun doWork(): Result {
        val jobId = inputData.getString(KEY_JOB_ID) ?: return Result.success()
        val attempt = claim(jobId) ?: return Result.success()
        try {
            val link = engine.resolve(attempt.submittedUrl)
            val parsed = engine.read(link)
            val zone = householdZone.current()
            val persisted = coordinator.write {
                val result = persister.persist(parsed.receipt, parsed.issues, zone)
                val completed = jobs.complete(
                    jobId,
                    result.outcome,
                    result.receiptId,
                    parsed.receipt.issuer.vatNumber,
                    clock.millis(),
                )
                // The job stopped being pending meanwhile (e.g. deleted): roll the receipt back.
                if (completed == 0) throw JobChanged()
                result
            }
            if (persisted.outcome == Persister.OUTCOME_CREATED) notifier.imported(persisted)
        } catch (_: JobChanged) {
            return Result.success()
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: ReceiptException) {
            recordFailure(jobId, attempt.attemptCount, exception)
        } catch (
            // Anything else is an internal error.
            @Suppress("TooGenericExceptionCaught") exception: Exception,
        ) {
            recordFailure(
                jobId,
                attempt.attemptCount,
                InternalError(exception.javaClass.simpleName),
            )
        }
        return Result.success()
    }

    private class Attempt(val submittedUrl: String, val attemptCount: Int)

    private class JobChanged : Exception()

    /** `claim`: continue only with a pending job, and count the attempt. */
    private suspend fun claim(jobId: String): Attempt? = coordinator.write {
        val job = jobs.get(jobId)
        when {
            job == null || job.status != ImportService.STATUS_PENDING -> null

            job.attemptCount > RETRY_SCHEDULE_SECONDS.size -> {
                // Every attempt so far ended without recording a result (the process was killed).
                val error = InternalError("attempts exhausted")
                jobs.fail(
                    jobId,
                    error.code.code,
                    error.publicMessage,
                    appVersion.code,
                    clock.millis(),
                )
                null
            }

            else -> {
                jobs.countAttempt(jobId, clock.millis())
                Attempt(job.submittedUrl, job.attemptCount + 1)
            }
        }
    }

    /** `record_failure`: attempt n failed → wait schedule[n − 1] (±10%), or fail. */
    private suspend fun recordFailure(jobId: String, attemptCount: Int, error: ReceiptException) {
        val step = attemptCount - 1
        val now = clock.millis()
        val retryable = error.retry == RetryPolicy.TRANSIENT || error.retry == RetryPolicy.NOT_FOUND
        if (retryable && step < RETRY_SCHEDULE_SECONDS.size) {
            val delayMillis = (
                RETRY_SCHEDULE_SECONDS[step] * MILLIS_PER_SECOND *
                    Random.nextDouble(JITTER_MIN, JITTER_MAX)
                ).toLong()
            val scheduled = coordinator.write {
                jobs.scheduleRetry(
                    jobId,
                    now + delayMillis,
                    error.code.code,
                    error.publicMessage,
                    now,
                )
            }
            if (scheduled == 1) scheduler.enqueueRetry(jobId, delayMillis)
        } else {
            coordinator.write {
                jobs.fail(jobId, error.code.code, error.publicMessage, appVersion.code, now)
            }
        }
    }

    companion object {
        const val KEY_JOB_ID = "job_id"

        /** The retry schedule: 60 s, 5 min, 30 min, 2 h, 6 h, 24 h. */
        val RETRY_SCHEDULE_SECONDS: List<Long> = listOf(60, 300, 1_800, 7_200, 21_600, 86_400)
        private const val MILLIS_PER_SECOND = 1_000
        private const val JITTER_MIN = 0.9
        private const val JITTER_MAX = 1.1
    }
}
