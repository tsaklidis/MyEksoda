package io.eksoda.core.data.repository

import android.net.Uri
import io.eksoda.core.model.BackupSummary

/**
 * Export and import of all data as one encrypted file. There is no server, so this file is the
 * only copy of the data outside the phone. It is encrypted with a passphrase the user chooses;
 * the passphrase is never stored, and without it the file cannot be opened.
 */
interface BackupRepository {
    /**
     * Writes a backup of every table to [destination] (a document the user created through the
     * Storage Access Framework), encrypted with [passphrase]. Keeps running when the screen is
     * left.
     */
    suspend fun export(destination: Uri, passphrase: String): Result<BackupSummary>

    /**
     * Reads, decrypts and checks the backup at [source] without changing any data. The returned
     * plan is what [restore] applies after the user confirms.
     */
    suspend fun open(source: Uri, passphrase: String): Result<RestorePlan>

    /**
     * Replaces all data with the backup's in one transaction (there is no merge). On any
     * error nothing changes. Keeps running when the screen is left.
     */
    suspend fun restore(plan: RestorePlan): Result<Unit>

    companion object {
        /** The shortest passphrase an export accepts. */
        const val MIN_PASSPHRASE_LENGTH = 12
    }
}

/** A decrypted, checked backup waiting for the user to confirm the restore. */
class RestorePlan internal constructor(val summary: BackupSummary, internal val archive: ByteArray)

enum class BackupError {
    /** The file is not an Eksoda backup. */
    NOT_A_BACKUP,

    /** The passphrase is wrong, or the file was changed after it was written. */
    WRONG_PASSPHRASE,

    /** A newer version of the app wrote the file. */
    NEWER_VERSION,

    /** The file decrypts but its contents are inconsistent. */
    DAMAGED,

    /** The file could not be read or written. */
    FILE_ERROR,
}

class BackupException(val error: BackupError, cause: Throwable? = null) :
    Exception(error.name, cause)

/** The [BackupError] of a failed backup call; anything unexpected counts as a file error. */
fun Throwable?.backupError(): BackupError =
    (this as? BackupException)?.error ?: BackupError.FILE_ERROR
