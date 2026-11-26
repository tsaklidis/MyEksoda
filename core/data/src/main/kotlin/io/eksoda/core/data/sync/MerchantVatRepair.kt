package io.eksoda.core.data.sync

import androidx.sqlite.db.SupportSQLiteDatabase
import io.eksoda.core.data.db.WriteCoordinator
import io.eksoda.core.database.EksodaDatabase
import io.eksoda.engine.sync.SyncMerge
import io.eksoda.engine.sync.VatRepair
import java.time.Clock
import javax.inject.Inject
import io.eksoda.engine.providers.entersoft.FIRST_VERSION as FIRST_ENTERSOFT
import io.eksoda.engine.providers.entersoft.PROVIDER_CODE as ENTERSOFT

/**
 * Moves receipts saved under a wrong VAT number to the right merchant (core:engine
 * `VatRepair`), as a merge of this phone's data with the corrected rows, in one transaction.
 * Runs at every start, and after every household sync merge ([SyncMerger]), where a phone that
 * has not updated yet can bring the wrong merchant back.
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
    val patch = VatRepair.patch(local, now) ?: return false
    SnapshotStore.apply(db, SyncMerge.merge(local, patch), now)
    return true
}

/** Whether `VatRepair.patch` has anything to do: the same rule, in SQL (a test compares them). */
internal fun needsVatRepair(db: SupportSQLiteDatabase): Boolean =
    db.query(NEEDS_REPAIR).use { it.moveToFirst() }

private fun longVat(column: String) = "length($column) > 9 AND $column NOT GLOB '*[^0-9]*'"

/** Receipt [receipt] was read by the first Entersoft parser. */
private fun firstParse(receipt: String) =
    "$receipt.provider_code = '$ENTERSOFT' AND $receipt.parser_version = '$FIRST_ENTERSOFT'"

/**
 * A merchant with a long VAT and no receipt from another provider, that has a receipt the first
 * Entersoft parser read, or none at all while the merchant with its first nine digits has such
 * receipts; or such a receipt with a long VAT under a merchant with its first nine digits.
 */
private val NEEDS_REPAIR = """
    SELECT 1 FROM merchant m WHERE m.country_code = 'GR' AND ${longVat("m.vat_number")}
      AND NOT EXISTS (SELECT 1 FROM receipt r
        WHERE r.merchant_id = m.id AND r.provider_code != '$ENTERSOFT')
      AND (
        EXISTS (SELECT 1 FROM receipt r WHERE r.merchant_id = m.id AND ${firstParse("r")})
        OR (
          NOT EXISTS (SELECT 1 FROM receipt r WHERE r.merchant_id = m.id)
          AND EXISTS (SELECT 1 FROM merchant y JOIN receipt s ON s.merchant_id = y.id
            WHERE y.country_code = 'GR' AND y.vat_number = substr(m.vat_number, 1, 9)
              AND ${firstParse("s")})
        )
      )
    UNION ALL
    SELECT 1 FROM receipt r WHERE ${firstParse("r")} AND r.issuer_country = 'GR'
      AND ${longVat("r.issuer_vat")}
      AND EXISTS (SELECT 1 FROM merchant m
        WHERE m.id = r.merchant_id AND m.vat_number = substr(r.issuer_vat, 1, 9))
    LIMIT 1
""".trimIndent()
