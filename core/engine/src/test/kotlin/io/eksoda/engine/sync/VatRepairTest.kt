package io.eksoda.engine.sync

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import io.eksoda.engine.sync.SyncRows.category
import io.eksoda.engine.sync.SyncRows.item
import io.eksoda.engine.sync.SyncRows.mapping
import io.eksoda.engine.sync.SyncRows.merchant
import io.eksoda.engine.sync.SyncRows.product
import io.eksoda.engine.sync.SyncRows.receipt
import io.eksoda.engine.sync.SyncRows.snapshot
import org.junit.jupiter.api.Test

/** Receipts saved under a Greek VAT followed by more digits move to the right merchant. */
class VatRepairTest {
    private val wrongReceipt = receipt("r2", mark = "200", merchant = "m2", url = URL) + mapOf(
        "provider_code" to "entersoft",
        "issuer_vat" to WRONG,
        "source_data" to """{"issuer":{"vat_number":"$WRONG"}}""",
    )

    /** m1 came from another provider (renamed by the user); m2 from Entersoft, wrong. */
    private val local = snapshot(
        "category" to listOf(category("c1", "groceries")),
        "merchant" to listOf(
            merchant("m1", vat = RIGHT, name = "Σκλαβενίτης", category = "c1"),
            merchant("m2", vat = WRONG, name = "ΕΛΛΗΝΙΚΕΣ ΥΠΕΡΑΓΟΡΕΣ ΣΚΛΑΒΕΝΙΤΗΣ"),
        ),
        "product" to listOf(product("p1", "Γάλα"), product("p2", "ΓΑΛΑ 1L")),
        "merchant_product_mapping" to listOf(
            mapping("map1", merchant = "m1", key = "code:123", product = "p1"),
            mapping("map2", merchant = "m2", key = "code:123", product = "p2"),
        ),
        "receipt" to listOf(
            receipt("r1", mark = "100", merchant = "m1") +
                mapOf("provider_code" to "epsilon", "issuer_vat" to RIGHT),
            wrongReceipt,
        ),
        "receipt_item" to listOf(
            item("i1", receipt = "r1", line = 1, product = "p1"),
            item("i2", receipt = "r2", line = 1, product = "p2"),
        ),
    )

    @Test
    fun `only a Greek VAT followed by more digits is repaired`() {
        assertThat(VatRepair.repairedVat("GR", WRONG)).isEqualTo(RIGHT)
        assertThat(VatRepair.repairedVat("GR", RIGHT)).isNull()
        assertThat(VatRepair.repairedVat("GR", "EL800764388214")).isNull()
        assertThat(VatRepair.repairedVat("CY", WRONG)).isNull()
        assertThat(VatRepair.repairedVat("GR", null)).isNull()
        assertThat(VatRepair.patch(snapshot("merchant" to listOf(merchant("m1"))), NOW)).isNull()

        // A long VAT that another provider saved is not this parser's mistake: left as it is.
        val other = wrongReceipt + ("provider_code" to "impact")
        val elsewhere = snapshot(
            "merchant" to listOf(merchant("m2", vat = WRONG)),
            "receipt" to listOf(other),
        )
        assertThat(VatRepair.patch(elsewhere, NOW)).isNull()
    }

    @Test
    fun `a receipt is repaired under a merchant that is already right, however new its copy`() {
        // A phone that had not updated changed the receipt later than this phone's clock.
        val ahead = wrongReceipt + mapOf("merchant_id" to "m1", "updated_at" to NOW + 50)
        val stale = snapshot(
            "merchant" to listOf(merchant("m1", vat = RIGHT)),
            "receipt" to listOf(ahead),
        )

        val merged = repaired(stale)

        val receipt = merged.row("receipt", "r2")
        assertThat(receipt["issuer_vat"]).isEqualTo(RIGHT)
        assertThat(receipt["updated_at"]).isEqualTo(NOW + 51)
        assertThat(VatRepair.patch(Snapshot(TEST_SCHEMA, merged.tables), NOW)).isNull()
    }

    @Test
    fun `the wrong merchant joins the right one, which keeps its name`() {
        val merged = repaired(local)

        assertThat(merged.ids("merchant")).containsExactly("m1")
        val shop = merged.row("merchant", "m1")
        assertThat(shop["vat_number"]).isEqualTo(RIGHT)
        assertThat(shop["display_name"]).isEqualTo("Σκλαβενίτης")
        assertThat(shop["default_category_id"]).isEqualTo("c1")

        val moved = merged.row("receipt", "r2")
        assertThat(moved["merchant_id"]).isEqualTo("m1")
        assertThat(moved["issuer_vat"]).isEqualTo(RIGHT)
        assertThat(moved["source_data"]).isEqualTo("""{"issuer":{"vat_number":"$RIGHT"}}""")
        assertThat(moved["source_url"]).isEqualTo(URL)
        // The same shop line at both: one mapping, one product.
        assertThat(merged.ids("merchant_product_mapping")).containsExactly("map1")
        assertThat(merged.ids("product")).containsExactly("p1")
        assertThat(merged.row("receipt_item", "i2")["product_id"]).isEqualTo("p1")

        // Repaired data needs nothing more.
        assertThat(VatRepair.patch(Snapshot(TEST_SCHEMA, merged.tables), NOW + 1)).isNull()
    }

    @Test
    fun `without a right merchant the wrong one is corrected in place`() {
        val alone = snapshot(
            "merchant" to listOf(merchant("m2", vat = WRONG)),
            "receipt" to listOf(wrongReceipt),
        )

        val merged = repaired(alone)

        assertThat(merged.ids("merchant")).containsExactly("m2")
        assertThat(merged.row("merchant", "m2")["vat_number"]).isEqualTo(RIGHT)
        assertThat(merged.row("receipt", "r2")["issuer_vat"]).isEqualTo(RIGHT)
    }

    @Test
    fun `a phone that has not repaired yet ends with the same data after syncing and repairing`() {
        val phoneA = Snapshot(TEST_SCHEMA, repaired(local).tables)
        val phoneB = local.forPeer()

        // A gets B's wrong merchant back (empty now: its receipt's newer copy is A's), and its
        // next start joins it again.
        val onA = SyncMerge.merge(phoneA, phoneB)
        val patchA = VatRepair.patch(Snapshot(TEST_SCHEMA, onA.tables), NOW + 1)
        assertThat(patchA).isNotNull()
        val againA = SyncMerge.merge(Snapshot(TEST_SCHEMA, onA.tables), checkNotNull(patchA))
        assertThat(againA.ids("merchant")).containsExactly("m1")
        assertThat(againA.row("receipt", "r2")["merchant_id"]).isEqualTo("m1")

        // B, merging A's repaired data, gets the receipt under the right merchant; its own wrong
        // merchant, now empty, goes at its own repair.
        val onB = SyncMerge.merge(local, phoneA.forPeer())
        assertThat(onB.row("receipt", "r2")["merchant_id"]).isEqualTo("m1")
        assertThat(onB.row("receipt", "r2")["issuer_vat"]).isEqualTo(RIGHT)
        assertThat(onB.ids("merchant")).containsExactly("m1", "m2")
        val repairedB = repaired(Snapshot(TEST_SCHEMA, onB.tables))
        for (table in listOf("merchant", "product", "merchant_product_mapping", "receipt")) {
            assertThat(repairedB.ids(table), name = table).isEqualTo(againA.ids(table))
        }
    }

    private fun repaired(snapshot: Snapshot): MergeResult =
        SyncMerge.merge(snapshot, checkNotNull(VatRepair.patch(snapshot, NOW)))

    private companion object {
        const val RIGHT = "800764388"
        const val WRONG = "8007643882141009999214100999814009120100058777"
        const val URL = "https://e-invoicing.gr/x"
        const val NOW = 1_000L
    }
}
