package io.eksoda.core.data.sync

import androidx.sqlite.db.SupportSQLiteDatabase
import io.eksoda.core.data.db.WriteCoordinator
import io.eksoda.core.database.EksodaDatabase
import io.eksoda.engine.sync.SyncMerge
import io.eksoda.engine.sync.VatRepair
import java.time.Clock
import javax.inject.Inject

/**
 * Moves receipts saved under a wrong VAT number to the right merchant (core:engine
 * `VatRepair`), as a merge of this phone's data with the corrected rows, in one transaction.
 * Runs at every start, and after a household sync merge ([SyncMerger]) or a backup restore,
 * which can bring such data back (a phone that has not updated yet, an older backup).
 */
class MerchantVatRepair @Inject constructor(
    private val database: EksodaDatabase,
    private val coordinator: WriteCoordinator,
    private val clock: Clock,
) {
    /** Whether anything was repaired. */
    suspend fun run(): Boolean = coordinator.write {
        repairVat(database.openHelper.writableDatabase, clock.millis())
    }
}

/**
 * Repairs [db] in the caller's transaction. [needsVatRepair] decides with one query whether to
 * read the data at all.
 */
internal fun repairVat(db: SupportSQLiteDatabase, now: Long): Boolean {
    if (!needsVatRepair(db)) return false
    val local = SnapshotStore.read(db)
    val patch = VatRepair.patch(local) ?: return false
    SnapshotStore.apply(db, SyncMerge.merge(local, patch), now)
    return true
}

/**
 * After what brought data in ([SyncMerger], a backup restore) committed, in a transaction of its
 * own: a repair that fails leaves that data as it came, for the next start to try again.
 */
internal suspend fun repairVatAfter(
    database: EksodaDatabase,
    coordinator: WriteCoordinator,
    now: Long,
) {
    runCatching { coordinator.write { repairVat(database.openHelper.writableDatabase, now) } }
}

/** Whether `VatRepair.patch` has anything to do (`VatRepair.NEEDS_REPAIR_SQL`). */
internal fun needsVatRepair(db: SupportSQLiteDatabase): Boolean =
    db.query(VatRepair.NEEDS_REPAIR_SQL).use { it.moveToFirst() }
