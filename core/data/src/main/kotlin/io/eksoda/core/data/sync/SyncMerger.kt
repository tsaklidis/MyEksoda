package io.eksoda.core.data.sync

import android.database.SQLException
import io.eksoda.core.data.db.WriteCoordinator
import io.eksoda.core.database.EksodaDatabase
import io.eksoda.engine.sync.Snapshot
import io.eksoda.engine.sync.SyncCounts
import io.eksoda.engine.sync.SyncException
import io.eksoda.engine.sync.SyncMerge
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Household sync on this phone's database: the snapshot it sends, and the merge of the one it
 * receives (core:engine `SyncMerge`). Both run under the write lock, so no import changes the
 * data halfway through.
 */
@Singleton
class SyncMerger @Inject constructor(
    private val database: EksodaDatabase,
    private val coordinator: WriteCoordinator,
    private val clock: Clock,
) {
    /** The schema version both phones must share. */
    val schema: Int get() = database.openHelper.readableDatabase.version

    /** This phone's data as another phone receives it: no receipt links. */
    suspend fun snapshotForPeer(): Snapshot =
        coordinator.write { SnapshotStore.read(database.openHelper.writableDatabase) }.forPeer()

    /**
     * Merges [remote] into this phone's data, in one transaction: on any failure nothing
     * changes. Throws [SyncException] (schema_mismatch, damaged, merge_failed).
     */
    suspend fun merge(remote: Snapshot): SyncCounts = try {
        coordinator.write {
            val db = database.openHelper.writableDatabase
            val result = SyncMerge.merge(SnapshotStore.read(db), remote)
            SnapshotStore.apply(db, result, clock.millis())
            // A phone that has not updated sends merchants saved under a wrong VAT number
            // back: they are repaired here, not left split until the next start (VatRepair).
            repairVat(db, clock.millis())
            SyncCounts(result.inserted, result.updated, result.deleted)
        }
    } catch (e: SQLException) {
        // A constraint failed while writing: the transaction rolled back.
        throw SyncException(SyncException.MERGE_FAILED, e)
    }
}
