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
import io.eksoda.core.database.EksodaDatabase
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
    private lateinit var database: EksodaDatabase
    private lateinit var db: SupportSQLiteDatabase
    private lateinit var repair: MerchantVatRepair

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            EksodaDatabase::class.java,
        ).allowMainThreadQueries().build()
        db = database.openHelper.writableDatabase
        val clock = Clock.fixed(Instant.ofEpochMilli(NOW), ZoneOffset.UTC)
        repair = MerchantVatRepair(database, WriteCoordinator(database), clock)
        db.merchant("m1", RIGHT, "Σκλαβενίτης")
        db.merchant("m2", WRONG, "ΕΛΛΗΝΙΚΕΣ ΥΠΕΡΑΓΟΡΕΣ ΣΚΛΑΒΕΝΙΤΗΣ Α.Ε.Ε.")
        db.receipt("r1", merchant = "m1", vat = RIGHT, mark = "400001", provider = "epsilon")
        db.receipt("r2", merchant = "m2", vat = WRONG, mark = "400002")
        db.job("j2", receipt = "r2")
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `the receipt moves to the merchant with the right VAT, and the wrong one goes`() {
        assertThat(runBlocking { repair.run() }).isTrue()

        assertThat(column("SELECT id FROM merchant")).containsExactly("m1")
        assertThat(column("SELECT display_name FROM merchant")).containsExactly("Σκλαβενίτης")
        assertThat(column("SELECT merchant_id || ' ' || issuer_vat FROM receipt ORDER BY id"))
            .containsExactly("m1 $RIGHT", "m1 $RIGHT")
        assertThat(column("SELECT source_data FROM receipt WHERE id = 'r2'"))
            .containsExactly("""{"vat_number":"$RIGHT"}""")
        // The receipt keeps its link, and its import still points at it.
        assertThat(column("SELECT source_url FROM receipt WHERE id = 'r2'"))
            .containsExactly("https://e-invoicing.gr/r2")
        assertThat(column("SELECT receipt_id FROM import_job")).containsExactly("r2")

        assertThat(runBlocking { repair.run() }).isFalse()
    }

    @Test
    fun `nothing to repair changes nothing`() {
        db.execSQL("DELETE FROM import_job")
        db.execSQL("DELETE FROM receipt WHERE id = 'r2'")
        db.execSQL("DELETE FROM merchant WHERE id = 'm2'")
        // Not digits only, or another provider's: not this repair's to change.
        db.merchant("m3", "EL094025817", "ΑΛΦΑ ΒΗΤΑ")
        db.merchant("m4", OTHER, "Άλλο")
        db.receipt("r4", merchant = "m4", vat = OTHER, mark = "400004", provider = "impact")

        assertThat(runBlocking { repair.run() }).isFalse()
        assertThat(column("SELECT updated_at FROM merchant")).isEqualTo(listOf("1", "1", "1"))
        assertThat(column("SELECT issuer_vat FROM receipt WHERE id = 'r4'"))
            .containsExactly(OTHER)
    }

    private fun column(sql: String): List<String> = db.query(sql).use { cursor ->
        buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
    }

    private fun SupportSQLiteDatabase.merchant(id: String, vat: String, name: String) = execSQL(
        "INSERT INTO merchant (id, country_code, vat_number, legal_name, display_name, " +
            "normalized_display_name, display_name_set_by_user, activity_description, " +
            "tax_office, gemi_number, street, street_number, city, postal_code, details_as_of, " +
            "created_at, updated_at) VALUES (?, 'GR', ?, ?, ?, '', 0, '', '', '', '', '', '', " +
            "'', NULL, 1, 1)",
        arrayOf<Any?>(id, vat, name, name),
    )

    private fun SupportSQLiteDatabase.receipt(
        id: String,
        merchant: String,
        vat: String,
        mark: String,
        provider: String = "entersoft",
    ) = execSQL(
        "INSERT INTO receipt (id, merchant_id, store_id, provider_code, parser_version, " +
            "issuer_country, issuer_vat, uid, mark, cancellation_mark, source_key, " +
            "document_kind, document_type_code, document_number, issued_at, " +
            "issued_local_date, currency, gross_total_minor, net_total_minor, vat_total_minor, " +
            "discount_total_minor, payment_method, status, validation_warnings, " +
            "excluded_from_analytics, user_note, source_url, source_url_fingerprint, " +
            "source_data, created_at, updated_at) VALUES (?, ?, NULL, ?, '1', 'GR', " +
            "?, NULL, ?, NULL, NULL, 'sale', '11.1', 'A-1', 1763200000000, '2026-11-15', " +
            "'EUR', 321, NULL, NULL, NULL, 'card', 'active', '[]', 0, '', ?, 'fp', ?, 1, 1)",
        arrayOf<Any?>(
            id,
            merchant,
            provider,
            vat,
            mark,
            "https://e-invoicing.gr/$id",
            """{"vat_number":"$vat"}""",
        ),
    )

    private fun SupportSQLiteDatabase.job(id: String, receipt: String) = execSQL(
        "INSERT INTO import_job (id, provider_code, url_identity, submitted_url, " +
            "url_fingerprint, source_host, status, outcome, attempt_count, receipt_id, " +
            "created_at, updated_at) VALUES (?, 'entersoft', ?, 'https://x.invalid', 'fp', " +
            "'x.invalid', 'completed', 'created', 1, ?, 1, 1)",
        arrayOf<Any?>(id, id, receipt),
    )

    private companion object {
        const val RIGHT = "800764388"
        const val WRONG = "8007643882141009999214100999814009120100058777"
        const val OTHER = "0940638401234"
        const val NOW = 1_763_300_000_000L
    }
}
