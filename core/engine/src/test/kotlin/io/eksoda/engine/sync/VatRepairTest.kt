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

/** Receipts the first Entersoft parser saved under a VAT followed by more digits. */
class VatRepairTest {
    private val wrongReceipt = receipt("r2", mark = "200", merchant = "m2", url = URL) + mapOf(
        "provider_code" to "entersoft",
        "parser_version" to "entersoft/1",
        "issuer_vat" to WRONG,
        "source_data" to sourceData(WRONG),
    )

    /** The stored receipt, with the same digits in another field, which stays as it is. */
    private fun sourceData(vat: String) =
        """{"issuer":{"vat_number":"$vat","legal_name":"Σ"},"lines":[{"barcode":"$WRONG"}]}"""

    /** A receipt of [merchant] from another provider. */
    private fun otherReceipt(id: String, merchant: String, vat: String = RIGHT) =
        receipt(id, mark = "1$id", merchant = merchant) +
            mapOf(
                "provider_code" to "epsilon",
                "parser_version" to "epsilon/1",
                "issuer_vat" to vat,
            )

    /** m1 came from another provider (renamed by the user); m2 from Entersoft, wrong. */
    private fun household(right: String = "m1", wrong: String = "m2") = snapshot(
        "category" to listOf(category("c1", "groceries")),
        "merchant" to listOf(
            merchant(right, vat = RIGHT, name = "Σκλαβενίτης", category = "c1"),
            merchant(wrong, vat = WRONG, name = "ΕΛΛΗΝΙΚΕΣ ΥΠΕΡΑΓΟΡΕΣ ΣΚΛΑΒΕΝΙΤΗΣ"),
        ),
        "product" to listOf(product("p1", "Γάλα"), product("p2", "ΓΑΛΑ 1L")),
        "merchant_product_mapping" to listOf(
            mapping("map1", merchant = right, key = "code:123", product = "p1"),
            mapping("map2", merchant = wrong, key = "code:123", product = "p2"),
        ),
        "receipt" to listOf(otherReceipt("r1", right), wrongReceipt + ("merchant_id" to wrong)),
        "receipt_item" to listOf(
            item("i1", receipt = "r1", line = 1, product = "p1"),
            item("i2", receipt = "r2", line = 1, product = "p2"),
        ),
    )

    @Test
    fun `only a Greek VAT followed by more digits stands for another`() {
        assertThat(VatRepair.repairedVat("GR", WRONG)).isEqualTo(RIGHT)
        assertThat(VatRepair.repairedVat("GR", RIGHT)).isNull()
        assertThat(VatRepair.repairedVat("GR", "EL800764388214")).isNull()
        assertThat(VatRepair.repairedVat("CY", WRONG)).isNull()
        assertThat(VatRepair.repairedVat("GR", null)).isNull()
        assertThat(VatRepair.patch(snapshot("merchant" to listOf(merchant("m1"))), NOW)).isNull()
    }

    @Test
    fun `nothing is repaired without receipts the first Entersoft parser read`() {
        val cases = mapOf(
            "another provider's" to listOf(otherReceipt("r3", "m2", vat = WRONG)),
            "a later parser's" to listOf(wrongReceipt + ("parser_version" to "entersoft/2")),
            // Mixed: the merchant is not only Entersoft's, so neither it nor its receipts move.
            "mixed" to listOf(wrongReceipt, otherReceipt("r3", "m2", vat = WRONG)),
            // Empty, and the merchant with its nine digits has no first-parser receipts.
            "none" to emptyList(),
        )
        for ((name, receipts) in cases) {
            val data = snapshot(
                "merchant" to listOf(merchant("m1", vat = RIGHT), merchant("m2", vat = WRONG)),
                "receipt" to receipts,
            )
            assertThat(VatRepair.patch(data, NOW), name = name).isNull()
        }
    }

    @Test
    fun `the wrong merchant joins the right one, which keeps its name`() {
        val merged = repaired(household())

        assertThat(merged.ids("merchant")).containsExactly("m1")
        val shop = merged.row("merchant", "m1")
        assertThat(shop["vat_number"]).isEqualTo(RIGHT)
        assertThat(shop["display_name"]).isEqualTo("Σκλαβενίτης")
        assertThat(shop["default_category_id"]).isEqualTo("c1")

        val moved = merged.row("receipt", "r2")
        assertThat(moved["merchant_id"]).isEqualTo("m1")
        assertThat(moved["issuer_vat"]).isEqualTo(RIGHT)
        assertThat(moved["source_data"]).isEqualTo(sourceData(RIGHT))
        assertThat(moved["source_url"]).isEqualTo(URL)
        // The same shop line at both: one mapping, one product.
        assertThat(merged.ids("merchant_product_mapping")).containsExactly("map1")
        assertThat(merged.ids("product")).containsExactly("p1")
        assertThat(merged.row("receipt_item", "i2")["product_id"]).isEqualTo("p1")

        // Repaired data needs nothing more.
        assertThat(VatRepair.patch(Snapshot(TEST_SCHEMA, merged.tables), NOW + 1)).isNull()
    }

    @Test
    fun `when the wrong merchant has the smaller id, it is kept with the right one's details`() {
        val merged = repaired(household(right = "m1", wrong = "m0"))

        assertThat(merged.ids("merchant")).containsExactly("m0")
        val shop = merged.row("merchant", "m0")
        assertThat(shop["vat_number"]).isEqualTo(RIGHT)
        assertThat(shop["display_name"]).isEqualTo("Σκλαβενίτης")
        assertThat(shop["default_category_id"]).isEqualTo("c1")
        assertThat(merged.row("receipt", "r1")["merchant_id"]).isEqualTo("m0")
        assertThat(merged.row("receipt", "r2")["merchant_id"]).isEqualTo("m0")
        assertThat(merged.remaps["merchant"]).isEqualTo(mapOf("m1" to "m0"))
    }

    @Test
    fun `without a right merchant the wrong one is corrected in place`() {
        // A Cypriot merchant with the same digits is another merchant.
        val cypriot = merchant("m9", vat = RIGHT) + ("country_code" to "CY")
        val alone = snapshot(
            "merchant" to listOf(merchant("m2", vat = WRONG), cypriot),
            // Unreadable source data is left as it is.
            "receipt" to listOf(wrongReceipt + ("source_data" to "not json")),
        )

        val merged = repaired(alone)
        assertThat(merged.row("receipt", "r2")["source_data"]).isEqualTo("not json")

        assertThat(merged.ids("merchant")).containsExactly("m2", "m9")
        assertThat(merged.row("merchant", "m2")["vat_number"]).isEqualTo(RIGHT)
        assertThat(merged.row("merchant", "m9")).isEqualTo(cypriot)
        assertThat(merged.row("receipt", "r2")["issuer_vat"]).isEqualTo(RIGHT)
    }

    @Test
    fun `a receipt is repaired under a merchant that is already right, however new its copy`() {
        // A phone that had not updated changed the receipt later than this phone's clock.
        // Without source data (rows store it, but the repair does not need it).
        val ahead = wrongReceipt - "source_data" +
            mapOf("merchant_id" to "m1", "updated_at" to NOW + 50)
        val stale = snapshot(
            "merchant" to listOf(merchant("m1", vat = RIGHT)),
            "receipt" to listOf(otherReceipt("r1", "m1"), ahead),
        )

        val merged = repaired(stale)

        val receipt = merged.row("receipt", "r2")
        assertThat(receipt["issuer_vat"]).isEqualTo(RIGHT)
        assertThat(receipt["updated_at"]).isEqualTo(NOW + 51)
        assertThat(receipt.containsKey("source_data")).isEqualTo(false)
        assertThat(VatRepair.patch(Snapshot(TEST_SCHEMA, merged.tables), NOW)).isNull()
    }

    @Test
    fun `a phone that has not updated sends the wrong merchant back, and it joins again`() {
        val phoneA = Snapshot(TEST_SCHEMA, repaired(household()).tables)
        val phoneB = household().forPeer()

        // A gets B's wrong merchant back, empty: its receipt's newer copy is A's. The merchant
        // with its nine digits has the first parser's receipts, so the copy is repaired.
        val onA = Snapshot(TEST_SCHEMA, SyncMerge.merge(phoneA, phoneB).tables)
        assertThat(onA.rows("merchant").map { it["id"] }).containsExactly("m1", "m2")
        val patch = VatRepair.patch(onA, NOW + 1)
        assertThat(patch).isNotNull()
        val againA = SyncMerge.merge(onA, checkNotNull(patch))
        assertThat(againA.ids("merchant")).containsExactly("m1")

        // B, once updated, merges A's data: the same.
        val onB = Snapshot(TEST_SCHEMA, SyncMerge.merge(household(), phoneA.forPeer()).tables)
        val repairedB = repaired(onB)
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
