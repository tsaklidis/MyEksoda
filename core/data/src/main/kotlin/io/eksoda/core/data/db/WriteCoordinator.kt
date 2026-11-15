package io.eksoda.core.data.db

import androidx.room.withTransaction
import io.eksoda.core.database.EksodaDatabase
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * All writes that touch receipts, products or mappings go through here: one Mutex around one
 * transaction (docs/android-standalone.md §4.3). This replaces the server's row locks and
 * IntegrityError retries; the unique indexes stay as the final guard.
 */
@Singleton
class WriteCoordinator @Inject constructor(private val database: EksodaDatabase) {
    private val mutex = Mutex()

    suspend fun <T> write(block: suspend () -> T): T = mutex.withLock {
        database.withTransaction { block() }
    }
}
