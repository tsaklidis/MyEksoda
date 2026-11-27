package io.eksoda.core.data.sync

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import io.eksoda.core.data.db.WriteCoordinator
import io.eksoda.core.data.sync.VatRows.RIGHT
import io.eksoda.core.data.sync.VatRows.WRONG
import io.eksoda.core.data.sync.VatRows.household
import io.eksoda.core.data.sync.VatRows.merchant
import io.eksoda.core.data.sync.VatRows.receipt
import io.eksoda.core.database.EksodaDatabase
import io.eksoda.engine.sync.VatRepair
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** A Sklavenitis receipt from Entersoft saved under its VAT followed by its phone number. */
@RunWith(AndroidJUnit4::class)
class MerchantVatRepairTest {
    private val opened = mutableListOf<EksodaDatabase>()
    private val clock = Clock.fixed(Instant.ofEpochMilli(NOW), ZoneOffset.UTC)
    private lateinit var database: EksodaDatabase
    private lateinit var db: SupportSQLiteDatabase
    private lateinit var repair: MerchantVatRepair

    @Before
    fun setUp() {
        database = database()
        db = database.openHelper.writableDatabase
        repair = MerchantVatRepair(database, WriteCoordinator(database), clock)
    }

    @After
    fun tearDown() = opened.forEach { it.close() }

    @Test
    fun `the receipt moves to the merchant with the right VAT, and the wrong one goes`() {
        db.household(right = "m1", wrong = "m2")

        assertThat(runBlocking { repair.run() }).isTrue()

        assertThat(column("SELECT id FROM merchant")).containsExactly("m1")
        assertThat(column("SELECT display_name FROM merchant")).containsExactly("Σκλαβενίτης")
        assertThat(column("SELECT merchant_id || ' ' || issuer_vat FROM receipt ORDER BY id"))
            .containsExactly("m1 $RIGHT", "m1 $RIGHT")
        assertThat(column("SELECT source_data FROM receipt WHERE id = 'r2'"))
            .containsExactly("""{"issuer":{"vat_number":"$RIGHT"}}""")
        // The receipt keeps its link, and its import still points at it.
        assertThat(column("SELECT source_url FROM receipt WHERE id = 'r2'"))
            .containsExactly("https://e-invoicing.gr/r2")
        assertThat(column("SELECT receipt_id FROM import_job")).containsExactly("r2")

        assertThat(runBlocking { repair.run() }).isFalse()
    }

    @Test
    fun `when the wrong merchant has the smaller id, the right one's receipts move to it`() {
        db.household(right = "m1", wrong = "m0")

        assertThat(runBlocking { repair.run() }).isTrue()

        assertThat(column("SELECT id || ' ' || vat_number || ' ' || display_name FROM merchant"))
            .containsExactly("m0 $RIGHT Σκλαβενίτης")
        assertThat(column("SELECT merchant_id FROM receipt ORDER BY id"))
            .containsExactly("m0", "m0")
    }

    @Test
    fun `a sync with a phone that has not updated repairs what it brings back`() {
        db.household(right = "m1", wrong = "m2")
        val old = database().openHelper.writableDatabase.apply { household("m1", "m2") }
        runBlocking { repair.run() }

        val merger = SyncMerger(database, WriteCoordinator(database), clock)
        runBlocking { merger.merge(SnapshotStore.read(old).forPeer()) }

        assertThat(column("SELECT id FROM merchant")).containsExactly("m1")
        assertThat(column("SELECT merchant_id || ' ' || issuer_vat FROM receipt ORDER BY id"))
            .containsExactly("m1 $RIGHT", "m1 $RIGHT")
    }

    @Test
    fun `the query finds exactly what the repair repairs`() {
        val cases: Map<String, SupportSQLiteDatabase.() -> Unit> = mapOf(
            "wrong merchant" to { household("m1", "m2") },
            "no merchant with the right VAT" to {
                merchant("m2", WRONG, "Σ")
                receipt("r2", "m2", WRONG, "400002")
            },
            "a receipt under the right merchant" to {
                merchant("m1", RIGHT, "Σ")
                receipt("r2", "m1", WRONG, "400002")
            },
            "an empty copy beside the first parser's receipts" to {
                merchant("m1", RIGHT, "Σ")
                merchant("m2", WRONG, "Σ")
                receipt("r2", "m1", RIGHT, "400002")
            },
            "an empty merchant alone" to { merchant("m2", WRONG, "Σ") },
            "an empty copy beside a Cypriot merchant's" to {
                merchant("m1", RIGHT, "Σ", country = "CY")
                merchant("m2", WRONG, "Σ")
                receipt("r2", "m1", RIGHT, "400002")
            },
            "not digits only" to {
                merchant("m3", "EL094025817", "Α")
                receipt("r3", "m3", "EL094025817", "400003")
            },
            "another provider's" to {
                merchant("m4", OTHER, "Α")
                receipt("r4", "m4", OTHER, "400004", provider = "impact", version = "impact/1")
            },
            "mixed providers" to {
                merchant("m2", WRONG, "Σ")
                receipt("r2", "m2", WRONG, "400002")
                receipt("r3", "m2", WRONG, "400003", provider = "impact", version = "impact/1")
            },
            "a later parser's" to {
                merchant("m2", WRONG, "Σ")
                receipt("r2", "m2", WRONG, "400002", version = "entersoft/2")
            },
        )
        for ((name, setUp) in cases) {
            val case = database().openHelper.writableDatabase.apply(setUp)
            val repairs = VatRepair.patch(SnapshotStore.read(case)) != null
            assertThat(needsVatRepair(case), name = name).isEqualTo(repairs)
        }
    }

    @Test
    fun `nothing to repair changes nothing`() {
        db.merchant("m1", RIGHT, "Σκλαβενίτης")
        db.merchant("m4", OTHER, "Άλλο")
        db.receipt("r4", "m4", OTHER, "400004", provider = "impact", version = "impact/1")

        assertThat(runBlocking { repair.run() }).isFalse()
        assertThat(column("SELECT updated_at FROM merchant")).isEqualTo(listOf("1", "1"))
        assertThat(column("SELECT issuer_vat FROM receipt")).containsExactly(OTHER)
    }

    private fun database() = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        EksodaDatabase::class.java,
    ).allowMainThreadQueries().build().also { opened += it }

    private fun column(sql: String): List<String> = db.query(sql).use { cursor ->
        buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
    }

    private companion object {
        const val OTHER = "0940638401234"
        const val NOW = 1_763_300_000_000L
    }
}
