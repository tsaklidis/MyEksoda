package io.eksoda.core.data.backup

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.prop
import io.eksoda.core.data.repository.BackupError
import io.eksoda.core.data.repository.BackupException
import io.eksoda.core.database.EksodaDatabase
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class BackupArchiveTest {
    private lateinit var database: EksodaDatabase
    private lateinit var db: SupportSQLiteDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            EksodaDatabase::class.java,
        ).allowMainThreadQueries().build()
        db = database.openHelper.writableDatabase
        seed()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `the table list covers every table in the schema`() {
        val tables = db.query(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' " +
                "AND name NOT IN ('room_master_table', 'android_metadata')",
        ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(0)) } }
        assertThat(tables).containsExactlyInAnyOrder(*BackupArchive.TABLES.toTypedArray())
    }

    @Test
    fun `restore gives back exactly the rows that were written`() {
        val before = dump()
        val written = transaction { BackupArchive.write(db) }
        db.execSQL("DELETE FROM receipt_item")
        db.execSQL("DELETE FROM receipt")
        db.execSQL(
            "INSERT INTO category VALUES ('c-new', 'new', 'New', 0, 5, 5)",
        )
        transaction { BackupArchive.restore(db, written.bytes) }
        assertThat(dump()).isEqualTo(before)
    }

    @Test
    fun `decimals are written as decimal strings without the scale suffix`() {
        val written = transaction { BackupArchive.write(db) }
        val receipts = entry(written.bytes, "receipt.ndjson")
        val items = entry(written.bytes, "receipt_item.ndjson")
        assertThat(receipts).contains("\"gross_total\":\"32.10\"")
        assertThat(items).contains("\"quantity\":\"1.5000\"")
        assertThat(items).contains("\"vat_rate\":\"13.00\"")
        assertThat(receipts).contains("\"net_total\":null")
    }

    @Test
    fun `inspect reads the manifest and checks the counts`() {
        val manifest = BackupArchive.inspect(transaction { BackupArchive.write(db) }.bytes)
        assertThat(manifest.counts["receipt"]).isEqualTo(1)
        assertThat(manifest.counts["receipt_item"]).isEqualTo(1)
        assertThat(manifest.householdName).isEqualTo("Σπίτι")
        assertThat(manifest.firstIssued).isEqualTo(LocalDate.of(2026, 11, 15))
        assertThat(manifest.schema).isEqualTo(db.version)
    }

    @Test
    fun `a row without its parent is refused and nothing changes`() {
        val before = dump()
        val archive = zip(
            "manifest.json" to MANIFEST_ONE_ITEM.replace("SCHEMA", db.version.toString()),
            "receipt_item.ndjson" to BROKEN_ITEM + "\n",
        )
        assertFailure {
            transaction { BackupArchive.restore(db, archive) }
        }.isInstanceOf(BackupException::class)
            .prop(BackupException::error)
            .isEqualTo(BackupError.DAMAGED)
        assertThat(dump()).isEqualTo(before)
    }

    @Test
    fun `counts that do not match the rows are damaged`() {
        val archive = zip(
            "manifest.json" to MANIFEST_ONE_ITEM.replace("SCHEMA", db.version.toString()),
            "receipt_item.ndjson" to "",
        )
        assertFailure { BackupArchive.inspect(archive) }
            .isInstanceOf(BackupException::class)
            .prop(BackupException::error)
            .isEqualTo(BackupError.DAMAGED)
    }

    /** Like WriteCoordinator: everything in one transaction, rolled back when [block] throws. */
    private fun <T> transaction(block: () -> T): T {
        db.beginTransaction()
        try {
            return block().also { db.setTransactionSuccessful() }
        } finally {
            db.endTransaction()
        }
    }

    private fun dump(): Map<String, List<List<String?>>> = BackupArchive.TABLES.associateWith {
        db.query("SELECT * FROM `$it` ORDER BY 1, 2").use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add((0 until cursor.columnCount).map { i -> cursor.getString(i) })
                }
            }
        }
    }

    private fun entry(archive: ByteArray, name: String): String =
        ZipInputStream(archive.inputStream()).use { zip ->
            generateSequence { zip.nextEntry }.first { it.name == name }
            zip.readBytes().toString(Charsets.UTF_8)
        }

    private fun zip(vararg entries: Pair<String, String>): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            entries.forEach { (name, text) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(text.toByteArray())
                zip.closeEntry()
            }
        }
        return bytes.toByteArray()
    }

    private fun seed() {
        db.execSQL(
            "INSERT INTO household (id, name, default_currency, timezone, created_at, " +
                "updated_at) VALUES ('h1', 'Σπίτι', 'EUR', 'Europe/Athens', 1, 1)",
        )
        db.execSQL(
            "INSERT INTO category (id, slug, name, is_system, created_at, updated_at) " +
                "VALUES ('c1', 'dairy', 'Dairy', 1, 1, 1)",
        )
        db.execSQL(
            "INSERT INTO merchant (id, country_code, vat_number, legal_name, display_name, " +
                "normalized_display_name, display_name_set_by_user, activity_description, " +
                "tax_office, gemi_number, street, street_number, city, postal_code, " +
                "details_as_of, default_category_id, created_at, updated_at) VALUES ('m1', 'GR', " +
                "'094014201', " +
                "'ΣΚΛΑΒΕΝΙΤΗΣ ΑΕ', 'Σκλαβενίτης', 'σκλαβενιτης', 0, '', '', '', '', '', '', '', " +
                "NULL, 'c1', 1, 1)",
        )
        db.execSQL(
            "INSERT INTO product (id, name, normalized_name, brand, category_id, " +
                "package_quantity_e4, package_unit, created_at, updated_at) VALUES ('p1', " +
                "'Γάλα', 'γαλα', '', 'c1', 10000, 'l', 1, 1)",
        )
        db.execSQL(
            "INSERT INTO receipt (id, merchant_id, store_id, provider_code, parser_version, " +
                "issuer_country, issuer_vat, uid, mark, cancellation_mark, source_key, " +
                "document_kind, document_type_code, document_number, issued_at, " +
                "issued_local_date, currency, gross_total_minor, net_total_minor, " +
                "vat_total_minor, discount_total_minor, payment_method, status, " +
                "validation_warnings, " +
                "excluded_from_analytics, user_note, source_url, source_url_fingerprint, " +
                "source_data, created_at, updated_at) VALUES ('r1', 'm1', NULL, 'impact', '1', " +
                "'GR', '094014201', NULL, '400001', NULL, NULL, 'sale', '11.1', 'A-1', " +
                "1763200000000, '2026-11-15', 'EUR', 3210, NULL, 370, NULL, 'card', 'active', " +
                "'[]', 0, 'σημείωση', 'https://example.invalid/r', 'fp', '{}', 1, 1)",
        )
        db.execSQL(
            "INSERT INTO receipt_item (id, receipt_id, line_number, line_type, description, " +
                "normalized_description, merchant_product_code, quantity_e4, unit, " +
                "unit_price_e4, gross_amount_minor, net_amount_minor, vat_rate_e2, " +
                "vat_amount_minor, discount_amount_minor, product_id, match_status, " +
                "normalized_quantity_e6, normalized_unit, normalized_unit_price_e4, user_note, " +
                "created_at, updated_at) VALUES ('i1', 'r1', 1, 'product', 'ΓΑΛΑ 1L', " +
                "'γαλα 1l', " +
                "'123', 15000, 'piece', 21400, 3210, 2841, 1300, 369, NULL, 'p1', 'matched', " +
                "1500000, 'l', 21400, '', 1, 1)",
        )
    }

    private companion object {
        val MANIFEST_ONE_ITEM = """
            {"format":1,"schema":SCHEMA,"counts":{"receipt_item":1},"household":{"name":null},
            "first_issued":null,"last_issued":null}
        """.trimIndent().replace("\n", "")

        // Points to a receipt and a product that are not in the file.
        val BROKEN_ITEM = """
            {"id":"i9","receipt_id":"missing","line_number":1,"line_type":"product",
            "description":"X","normalized_description":"x","merchant_product_code":null,
            "quantity":null,"unit":null,"unit_price":null,"gross_amount":"1.00",
            "net_amount":null,"vat_rate":null,"vat_amount":null,"discount_amount":null,
            "product_id":"missing","match_status":"matched","normalized_quantity":null,
            "normalized_unit":null,"normalized_unit_price":null,"user_note":"",
            "created_at":1,"updated_at":1}
        """.trimIndent().replace("\n", "")
    }
}
