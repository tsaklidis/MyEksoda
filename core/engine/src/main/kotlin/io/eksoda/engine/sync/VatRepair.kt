package io.eksoda.engine.sync

import io.eksoda.engine.providers.entersoft.FIRST_VERSION
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import io.eksoda.engine.providers.entersoft.PROVIDER_CODE as ENTERSOFT

/**
 * Merchants saved under a wrong VAT number, and their receipts. The first Entersoft parser
 * ([FIRST_VERSION], until 0.3.10) read every digit of the "Α.Φ.Μ" line, so an issuer that
 * prints its phone and ΓΕΜΗ there (Sklavenitis) was saved as a new merchant under the VAT
 * followed by those numbers.
 *
 * Only that parser's data is repaired, never from the shape of a VAT alone:
 * - a merchant with a Greek VAT of more than nine digits, all digits, and no receipt from
 *   another provider, that has a receipt the first Entersoft parser read; or that has no
 *   receipts while the merchant with its first nine digits has such receipts (the copy a
 *   household phone that has not updated yet sends back, after its receipts moved);
 * - a receipt the first Entersoft parser read with such a VAT, when its nine digits are its
 *   merchant's VAT once repaired.
 *
 * The repair is a merge ([SyncMerge]) of this phone's data with [patch]: the corrected rows,
 * newer than the stored ones. The merge then does what it does for two phones' copies of a
 * merchant: one merchant per VAT number (the smaller id), its receipts, stores and product
 * mappings repointed, and every phone of the household ends up with the same data.
 */
public object VatRepair {
    private const val GREEK_VAT_LENGTH = 9

    /** More digits than a Greek VAT, and only digits (ASCII: `\d` without UNICODE flags). */
    private val LONG_VAT = Regex("""\d{10,}""")
    private const val GREECE = "GR"
    private const val VAT = "vat_number"
    private const val ISSUER_VAT = "issuer_vat"
    private const val SOURCE_DATA = "source_data"
    private const val MERCHANT_ID = "merchant_id"

    /** The VAT number [vat] stands for when it is a Greek VAT followed by more digits; else null. */
    public fun repairedVat(country: Any?, vat: Any?): String? {
        if (country != GREECE || vat !is String || !LONG_VAT.matches(vat)) return null
        return vat.take(GREEK_VAT_LENGTH)
    }

    /**
     * The corrected rows to merge into [local], or null when nothing needs repairing. Each is
     * newer than its stored copy and than [now]. A merchant already under the right VAT is
     * stamped newer still, so when the merge joins the two, its own details (a rename, a default
     * category) are the ones kept.
     */
    public fun patch(local: Snapshot, now: Long): Snapshot? {
        val merchants = local.rows(SyncSchema.MERCHANT)
        val wrong = wrongMerchants(merchants, local.rows(SyncSchema.RECEIPT))
        val vats = wrong.values.toSet()
        val existing = merchants.filter { it["country_code"] == GREECE && it[VAT] in vats }
        val wrongRows = merchants.filter { it[SyncSchema.ID] in wrong }
        val base = (wrongRows + existing).maxOfOrNull { version(it) + 1 }?.coerceAtLeast(now)
            ?: now
        val corrected = wrongRows.map { merchant ->
            merchant + (VAT to wrong[merchant[SyncSchema.ID]]) + (SyncSchema.VERSION to base)
        } + existing.map { it + (SyncSchema.VERSION to base + 1) }

        // Each merchant's VAT once repaired: a receipt is repaired only to match it.
        val finalVat = merchants.associate { it[SyncSchema.ID] to it[VAT] } + wrong
        val movedReceipts = local.rows(SyncSchema.RECEIPT).mapNotNull { receipt ->
            repairedReceipt(receipt, now)?.takeIf { it[ISSUER_VAT] == finalVat[it[MERCHANT_ID]] }
        }
        if (corrected.isEmpty() && movedReceipts.isEmpty()) return null
        return Snapshot(
            local.schema,
            mapOf(SyncSchema.MERCHANT to corrected, SyncSchema.RECEIPT to movedReceipts),
        )
    }

    /** Merchant id → its VAT once repaired, for the merchants to repair. */
    private fun wrongMerchants(merchants: List<Row>, receipts: List<Row>): Map<Any?, String> {
        val firstEntersoft = receipts.filter(::isFirstEntersoft).map { it[MERCHANT_ID] }.toSet()
        val withReceipts = receipts.map { it[MERCHANT_ID] }.toSet()
        val elsewhere = receipts.filter { it["provider_code"] != ENTERSOFT }
            .map { it[MERCHANT_ID] }
            .toSet()
        val entersoftVats = merchants.filter { it[SyncSchema.ID] in firstEntersoft }
            .map { it[VAT] }
            .toSet()
        return merchants.mapNotNull { merchant ->
            val id = merchant[SyncSchema.ID]
            val vat = repairedVat(merchant["country_code"], merchant[VAT])
            val evidence = id in firstEntersoft || (id !in withReceipts && vat in entersoftVats)
            if (vat == null || id in elsewhere || !evidence) null else id to vat
        }.toMap()
    }

    private fun isFirstEntersoft(receipt: Row): Boolean =
        receipt["provider_code"] == ENTERSOFT && receipt["parser_version"] == FIRST_VERSION

    private fun repairedReceipt(receipt: Row, now: Long): Row? {
        if (!isFirstEntersoft(receipt)) return null
        val vat = repairedVat(receipt["issuer_country"], receipt[ISSUER_VAT]) ?: return null
        val wrong = receipt.getValue(ISSUER_VAT) as String
        val data = repairedSourceData(receipt[SOURCE_DATA], wrong, vat)
            ?.let { mapOf(SOURCE_DATA to it) }
            .orEmpty()
        val stamp = maxOf(now, version(receipt) + 1)
        return receipt + (ISSUER_VAT to vat) + data + (SyncSchema.VERSION to stamp)
    }

    /**
     * The stored canonical receipt ([data], `persist/ReceiptValues.kt` `sourceData`) with only
     * its `issuer.vat_number` repaired; null when that is not [wrong] (nothing else to change).
     */
    private fun repairedSourceData(data: Any?, wrong: String, vat: String): String? {
        val json = (data as? String)
            ?.let { runCatching { Json.parseToJsonElement(it) }.getOrNull() } as? JsonObject
        val issuer = json?.get("issuer") as? JsonObject
        val stored = (issuer?.get(VAT) as? JsonPrimitive)?.content
        if (json == null || issuer == null || stored != wrong) return null
        return JsonObject(json + ("issuer" to JsonObject(issuer + (VAT to JsonPrimitive(vat)))))
            .toString()
    }

    private fun version(row: Row): Long = row[SyncSchema.VERSION] as? Long ?: 0L
}
