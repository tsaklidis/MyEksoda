package io.eksoda.core.data.sync

import io.eksoda.core.data.db.WriteCoordinator
import io.eksoda.core.database.EksodaDatabase
import io.eksoda.engine.sync.SyncMerge
import io.eksoda.engine.sync.VatRepair
import java.time.Clock
import javax.inject.Inject
import io.eksoda.engine.providers.entersoft.PROVIDER_CODE as ENTERSOFT

/**
 * Moves receipts saved under a wrong VAT number to the right merchant (core:engine
 * `VatRepair`), as a merge of this phone's data with the corrected rows, in one transaction.
 * Runs on every start: a phone of the household that has not updated yet can send the wrong
 * merchant or receipt back, and the next start repairs it again. With nothing to repair it is
 * one query ([SUSPECT], the same rule as `VatRepair`).
 */
class MerchantVatRepair @Inject constructor(
    private val database: EksodaDatabase,
    private val coordinator: WriteCoordinator,
    private val clock: Clock,
) {
    /** Whether anything was repaired. */
    suspend fun run(): Boolean = coordinator.write {
        val db = database.openHelper.writableDatabase
        val suspect = db.query(SUSPECT).use { it.moveToFirst() }
        if (!suspect) return@write false
        val local = SnapshotStore.read(db)
        val now = clock.millis()
        val patch = VatRepair.patch(local, now) ?: return@write false
        SnapshotStore.apply(db, SyncMerge.merge(local, patch), now)
        true
    }

    private companion object {
        /** What VatRepair repairs: a Greek VAT of more than nine digits, only digits. */
        fun longVat(column: String) = "length($column) > 9 AND $column NOT GLOB '*[^0-9]*'"

        /** A merchant with no receipt from another provider, or an Entersoft receipt. */
        val SUSPECT = """
            SELECT 1 FROM merchant m WHERE m.country_code = 'GR' AND ${longVat("m.vat_number")}
              AND NOT EXISTS (SELECT 1 FROM receipt r
                WHERE r.merchant_id = m.id AND r.provider_code != '$ENTERSOFT')
            UNION ALL
            SELECT 1 FROM receipt WHERE provider_code = '$ENTERSOFT' AND issuer_country = 'GR'
              AND ${longVat("issuer_vat")}
            LIMIT 1
        """.trimIndent()
    }
}
