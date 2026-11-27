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
 *   receipts while the Greek merchant with its first nine digits has such receipts (the copy a
 *   household phone that has not updated yet sends back, after its receipts moved);
 * - a receipt the first Entersoft parser read with such a VAT, when its nine digits are its
 *   merchant's VAT once repaired.
 *
 * The repair is a merge ([SyncMerge]) of this phone's data with [patch]: the corrected rows,
 * newer than the stored ones. The merge then does what it does for two phones' copies of a
 * merchant: one merchant per VAT number (the smaller id), its receipts, stores and product
 * mappings repointed, and every phone of the household ends up with the same data.
 * [NEEDS_REPAIR_SQL] is the same rule as a query, to skip reading the data when there is nothing
 * to do (core:data compares the two in a test).
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
    private const val RENAMED = "display_name_set_by_user"

    /** The VAT number [vat] stands for when it is a Greek VAT followed by more digits; else null. */
    public fun repairedVat(country: Any?, vat: Any?): String? {
        if (country != GREECE || vat !is String || !LONG_VAT.matches(vat)) return null
        return vat.take(GREEK_VAT_LENGTH)
    }

    /**
     * The corrected rows to merge into [local], or null when nothing needs repairing. Each is
     * newer than its stored copy. Of the merchants that become one, the one whose details
     * (name, default category) are kept ([keeperOrder]: renamed by the user, else the one
     * already under the right VAT) is the newest; it is left out when it already is.
     */
    public fun patch(local: Snapshot): Snapshot? {
        val merchants = local.rows(SyncSchema.MERCHANT)
        val receipts = local.rows(SyncSchema.RECEIPT)
        val wrong = wrongMerchants(merchants, receipts)
        val corrected = merchants
            .filter { it[SyncSchema.ID] in wrong || (isGreek(it) && it[VAT] in wrong.values) }
            .groupBy { wrong[it[SyncSchema.ID]] ?: it[VAT] }
            .values
            .flatMap { group -> stamped(group, wrong) }

        // Each merchant's VAT once repaired: a receipt is repaired only to match it.
        val finalVat = merchants.associate { it[SyncSchema.ID] to it[VAT] } + wrong
        val movedReceipts = receipts.mapNotNull { receipt ->
            repairedReceipt(receipt)?.takeIf { it[ISSUER_VAT] == finalVat[it[MERCHANT_ID]] }
        }
        if (corrected.isEmpty() && movedReceipts.isEmpty()) return null
        return Snapshot(
            local.schema,
            mapOf(SyncSchema.MERCHANT to corrected, SyncSchema.RECEIPT to movedReceipts),
        )
    }

    /** Of one VAT's merchants: user-renamed first, then the right one, then the smaller id. */
    private fun keeperOrder(wrong: Map<Any?, String>): Comparator<Row> =
        compareBy<Row>({ (it[RENAMED] as? Long ?: 0L) != 0L }, { it[SyncSchema.ID] !in wrong })
            .thenByDescending { it[SyncSchema.ID] as? String }

    /** One VAT's merchants ([group]) as patched: see [patch]. */
    private fun stamped(group: List<Row>, wrong: Map<Any?, String>): List<Row> {
        val keeper = group.maxWith(keeperOrder(wrong))
        val corrected = group.filter { it[SyncSchema.ID] in wrong }.associate { merchant ->
            val id = merchant[SyncSchema.ID]
            val stamp = version(merchant) + 1
            id to merchant + (VAT to wrong.getValue(id)) + (SyncSchema.VERSION to stamp)
        }
        val keeperId = keeper[SyncSchema.ID]
        val others = corrected.values.filter { it[SyncSchema.ID] != keeperId }
        val newest = group.filter { it[SyncSchema.ID] != keeperId }
            .maxOfOrNull { version(corrected[it[SyncSchema.ID]] ?: it) }
            ?: Long.MIN_VALUE
        val kept = corrected[keeperId] ?: keeper
        if (keeperId !in wrong && version(kept) > newest) return others
        return others + (kept + (SyncSchema.VERSION to maxOf(version(kept), newest + 1)))
    }

    /** Merchant id → its VAT once repaired, for the merchants to repair. */
    private fun wrongMerchants(merchants: List<Row>, receipts: List<Row>): Map<Any?, String> {
        val firstEntersoft = receipts.filter(::isFirstEntersoft).map { it[MERCHANT_ID] }.toSet()
        val withReceipts = receipts.map { it[MERCHANT_ID] }.toSet()
        val elsewhere = receipts.filter { it["provider_code"] != ENTERSOFT }
            .map { it[MERCHANT_ID] }
            .toSet()
        val entersoftVats = merchants
            .filter { isGreek(it) && it[SyncSchema.ID] in firstEntersoft }
            .map { it[VAT] }
            .toSet()
        return merchants.mapNotNull { merchant ->
            val id = merchant[SyncSchema.ID]
            val vat = repairedVat(merchant["country_code"], merchant[VAT])
            val evidence = id in firstEntersoft || (id !in withReceipts && vat in entersoftVats)
            if (vat == null || id in elsewhere || !evidence) null else id to vat
        }.toMap()
    }

    private fun isGreek(merchant: Row): Boolean = merchant["country_code"] == GREECE

    private fun isFirstEntersoft(receipt: Row): Boolean =
        receipt["provider_code"] == ENTERSOFT && receipt["parser_version"] == FIRST_VERSION

    private fun repairedReceipt(receipt: Row): Row? {
        if (!isFirstEntersoft(receipt)) return null
        val vat = repairedVat(receipt["issuer_country"], receipt[ISSUER_VAT]) ?: return null
        val wrong = receipt.getValue(ISSUER_VAT) as String
        val data = repairedSourceData(receipt[SOURCE_DATA], wrong, vat)
            ?.let { mapOf(SOURCE_DATA to it) }
            .orEmpty()
        return receipt + (ISSUER_VAT to vat) + data + (SyncSchema.VERSION to version(receipt) + 1)
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

    private fun longVat(column: String) = "length($column) > 9 AND $column NOT GLOB '*[^0-9]*'"

    private fun firstParse(receipt: String) =
        "$receipt.provider_code = '$ENTERSOFT' AND $receipt.parser_version = '$FIRST_VERSION'"

    /** Whether [patch] has anything to do, as one query on the synced tables: the same rule. */
    public val NEEDS_REPAIR_SQL: String = """
        SELECT 1 FROM merchant m WHERE m.country_code = '$GREECE' AND ${longVat("m.vat_number")}
          AND NOT EXISTS (SELECT 1 FROM receipt r
            WHERE r.merchant_id = m.id AND r.provider_code != '$ENTERSOFT')
          AND (
            EXISTS (SELECT 1 FROM receipt r WHERE r.merchant_id = m.id AND ${firstParse("r")})
            OR (
              NOT EXISTS (SELECT 1 FROM receipt r WHERE r.merchant_id = m.id)
              AND EXISTS (SELECT 1 FROM merchant y JOIN receipt s ON s.merchant_id = y.id
                WHERE y.country_code = '$GREECE' AND y.vat_number = substr(m.vat_number, 1, 9)
                  AND ${firstParse("s")})
            )
          )
        UNION ALL
        SELECT 1 FROM receipt r WHERE ${firstParse("r")} AND r.issuer_country = '$GREECE'
          AND ${longVat("r.issuer_vat")}
          AND EXISTS (SELECT 1 FROM merchant m
            WHERE m.id = r.merchant_id AND m.vat_number = substr(r.issuer_vat, 1, 9))
        LIMIT 1
    """.trimIndent()
}
