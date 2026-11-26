package io.eksoda.engine.sync

import io.eksoda.engine.providers.entersoft.PROVIDER_CODE as ENTERSOFT

/**
 * Merchants saved under a wrong VAT number, and their receipts. Until 0.3.10 the Entersoft
 * parser read every digit of the "Α.Φ.Μ" line, so an issuer that prints its phone and ΓΕΜΗ
 * there (Sklavenitis) was saved as a new merchant under the VAT followed by those numbers.
 * Only that data is repaired: Entersoft receipts, and merchants with no receipt from another
 * provider (one a household phone that has not updated yet sent back is empty).
 *
 * The repair is a merge ([SyncMerge]) of this phone's data with [patch]: the corrected rows,
 * newer than the stored ones. The merge then does what it does for two phones' copies of a
 * merchant: one merchant per VAT number (the smaller id), its receipts, stores and product
 * mappings repointed, and every phone of the household ends up with the same data.
 */
public object VatRepair {
    private const val GREEK_VAT_LENGTH = 9
    private const val GREECE = "GR"
    private const val VAT = "vat_number"
    private const val ISSUER_VAT = "issuer_vat"
    private const val SOURCE_DATA = "source_data"
    private const val PROVIDER = "provider_code"

    /** The VAT number [vat] stands for when it is a Greek VAT followed by more digits; else null. */
    public fun repairedVat(country: Any?, vat: Any?): String? {
        if (country != GREECE || vat !is String) return null
        val longer = vat.length > GREEK_VAT_LENGTH && vat.all { it in '0'..'9' }
        return if (longer) vat.take(GREEK_VAT_LENGTH) else null
    }

    /**
     * The corrected rows to merge into [local], or null when nothing needs repairing. Each is
     * newer than its stored copy and than [now]. A merchant already under the right VAT is
     * stamped newer still, so when the merge joins the two, its own details (a rename, a default
     * category) are the ones kept.
     */
    public fun patch(local: Snapshot, now: Long): Snapshot? {
        val receipts = local.rows(SyncSchema.RECEIPT)
        val elsewhere = receipts.filter { it[PROVIDER] != ENTERSOFT }.map { it["merchant_id"] }
            .toSet()
        val merchants = local.rows(SyncSchema.MERCHANT)
        val wrong = merchants.mapNotNull { merchant ->
            val vat = repairedVat(merchant["country_code"], merchant[VAT])
            if (vat == null || merchant[SyncSchema.ID] in elsewhere) null else merchant to vat
        }
        val vats = wrong.map { (_, vat) -> vat }.toSet()
        val existing = merchants.filter { it["country_code"] == GREECE && it[VAT] in vats }
        val base = (wrong.map { (merchant, _) -> merchant } + existing)
            .maxOfOrNull { version(it) + 1 }
            ?.coerceAtLeast(now) ?: now
        val corrected = wrong.map { (merchant, vat) ->
            merchant + (VAT to vat) + (SyncSchema.VERSION to base)
        } + existing.map { it + (SyncSchema.VERSION to base + 1) }
        val movedReceipts = receipts.mapNotNull { receipt -> repairedReceipt(receipt, now) }
        if (corrected.isEmpty() && movedReceipts.isEmpty()) return null
        return Snapshot(
            local.schema,
            mapOf(SyncSchema.MERCHANT to corrected, SyncSchema.RECEIPT to movedReceipts),
        )
    }

    private fun repairedReceipt(receipt: Row, now: Long): Row? {
        if (receipt[PROVIDER] != ENTERSOFT) return null
        val wrong = receipt[ISSUER_VAT] as? String
        val vat = repairedVat(receipt["issuer_country"], wrong) ?: return null
        val data = receipt[SOURCE_DATA]
        val repairedData = if (data is String) {
            mapOf(SOURCE_DATA to data.replace("\"$wrong\"", "\"$vat\""))
        } else {
            emptyMap()
        }
        val stamp = maxOf(now, version(receipt) + 1)
        return receipt + (ISSUER_VAT to vat) + repairedData + (SyncSchema.VERSION to stamp)
    }

    private fun version(row: Row): Long = row[SyncSchema.VERSION] as? Long ?: 0L
}
