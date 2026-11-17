package io.eksoda.core.data.repository

import android.content.Context
import android.database.SQLException
import android.net.Uri
import androidx.core.content.pm.PackageInfoCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import io.eksoda.core.data.backup.BackupArchive
import io.eksoda.core.data.backup.BackupFile
import io.eksoda.core.data.db.WriteCoordinator
import io.eksoda.core.data.di.ApplicationScope
import io.eksoda.core.data.di.IoDispatcher
import io.eksoda.core.data.imports.ImportScheduler
import io.eksoda.core.database.EksodaDatabase
import io.eksoda.core.database.dao.ImportJobDao
import io.eksoda.core.model.BackupSummary
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import java.io.IOException
import java.security.GeneralSecurityException
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: EksodaDatabase,
    private val coordinator: WriteCoordinator,
    private val jobs: ImportJobDao,
    private val scheduler: ImportScheduler,
    private val clock: Clock,
    @ApplicationScope private val scope: CoroutineScope,
    @IoDispatcher private val io: CoroutineDispatcher,
) : BackupRepository {
    override suspend fun export(destination: Uri, passphrase: String): Result<BackupSummary> =
        outliving {
            // Under the write lock, so no import changes the data halfway through the copy.
            val archive = coordinator.write { BackupArchive.write(database.writable()) }
            val createdAt = clock.instant()
            val file = BackupFile.encrypt(archive.bytes, passphrase, appVersion(), createdAt)
            val output = context.contentResolver.openOutputStream(destination, "wt")
                ?: throw BackupException(BackupError.FILE_ERROR)
            output.use { it.write(file) }
            archive.manifest.summary(createdAt)
        }

    override suspend fun open(source: Uri, passphrase: String): Result<RestorePlan> = outliving {
        val input = context.contentResolver.openInputStream(source)
            ?: throw BackupException(BackupError.FILE_ERROR)
        val decrypted = BackupFile.decrypt(input.use { it.readBytes() }, passphrase)
        val manifest = BackupArchive.inspect(decrypted.plaintext)
        // Older schemas are read by column name; a newer one may need columns this one lacks.
        if (manifest.schema > database.openHelper.readableDatabase.version) {
            throw BackupException(BackupError.NEWER_VERSION)
        }
        RestorePlan(manifest.summary(decrypted.header.createdAt), decrypted.plaintext)
    }

    override suspend fun restore(plan: RestorePlan): Result<Unit> = outliving {
        val before = jobs.pending().map { it.id }.toSet()
        coordinator.write { BackupArchive.restore(database.writable(), plan.archive) }
        // Queued imports of jobs that are gone stop; the backup's pending jobs run again.
        val after = jobs.pending().map { it.id }.toSet()
        (before - after).forEach(scheduler::cancel)
        after.forEach { scheduler.enqueue(it) }
    }

    /**
     * Runs in the application scope, so leaving the screen does not stop an export or a restore
     * halfway (the caller only stops waiting). Not WorkManager, as §12.1 suggests: a worker's
     * input is stored on disk unencrypted, and the passphrase must never be written anywhere.
     */
    private suspend fun <T> outliving(block: suspend () -> T): Result<T> = scope.async(io) {
        try {
            Result.success(block())
        } catch (e: BackupException) {
            Result.failure(e)
        } catch (e: IOException) {
            Result.failure(BackupException(BackupError.FILE_ERROR, e))
        } catch (e: SecurityException) {
            // The document provider no longer grants access to the file.
            Result.failure(BackupException(BackupError.FILE_ERROR, e))
        } catch (e: SQLException) {
            // A constraint failed while restoring: the transaction rolled back.
            Result.failure(BackupException(BackupError.DAMAGED, e))
        } catch (e: IllegalArgumentException) {
            Result.failure(BackupException(BackupError.DAMAGED, e))
        } catch (e: GeneralSecurityException) {
            Result.failure(BackupException(BackupError.DAMAGED, e))
        }
    }.await()

    private fun EksodaDatabase.writable() = openHelper.writableDatabase

    private fun appVersion(): String {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return "${info.versionName.orEmpty()} (${PackageInfoCompat.getLongVersionCode(info)})"
    }

    private fun BackupArchive.Manifest.summary(createdAt: Instant) = BackupSummary(
        createdAt = createdAt,
        householdName = householdName,
        receipts = counts["receipt"] ?: 0,
        products = counts["product"] ?: 0,
        merchants = counts["merchant"] ?: 0,
        firstIssued = firstIssued,
        lastIssued = lastIssued,
    )
}
