package io.eksoda.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Every schema change since the first release (version 4) keeps the user's data. Each migration
 * starts from a database written with the old exported schema (core/database/schemas), migrates
 * it and checks the result against the new schema.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        EksodaDatabase::class.java,
    )

    @Test
    fun `4 to 5 keeps the data and adds budgets and the annual income`() {
        helper.createDatabase(NAME, 4).use { db ->
            db.execSQL(
                """INSERT INTO household (id, name, default_currency, timezone, created_at,
                   updated_at) VALUES ('h1', 'Σπίτι', 'EUR', 'Europe/Athens', 1, 1)""",
            )
            db.execSQL(
                """INSERT INTO category (id, slug, name, is_system, created_at, updated_at)
                   VALUES ('c1', 'groceries', 'Groceries', 1, 1, 1)""",
            )
        }

        helper.runMigrationsAndValidate(NAME, 5, true).use { db ->
            db.query("SELECT name, annual_income_minor FROM household").use { row ->
                assertThat(row.moveToFirst()).isTrue()
                assertThat(row.getString(0)).isEqualTo("Σπίτι")
                assertThat(row.isNull(1)).isTrue()
            }
            assertThat(db.count("category")).isEqualTo(1)

            // A category's budget goes with the category.
            db.execSQL("PRAGMA foreign_keys = ON")
            db.execSQL(
                """INSERT INTO budget (id, category_id, amount_minor, created_at, updated_at)
                   VALUES ('total', NULL, 50000, 1, 1), ('c1', 'c1', 30000, 1, 1)""",
            )
            db.execSQL("DELETE FROM category WHERE id = 'c1'")
            assertThat(db.count("budget")).isEqualTo(1)
        }
    }

    @Test
    fun `5 to 6 keeps the data and adds the sync tables`() {
        helper.createDatabase(NAME, 5).use { db ->
            db.execSQL(
                """INSERT INTO household (id, name, default_currency, timezone, created_at,
                   updated_at, annual_income_minor) VALUES ('h1', 'Σπίτι', 'EUR', 'Europe/Athens',
                   1, 1, 2400000)""",
            )
        }

        helper.runMigrationsAndValidate(NAME, 6, true).use { db ->
            db.query("SELECT name, annual_income_minor FROM household").use { row ->
                assertThat(row.moveToFirst()).isTrue()
                assertThat(row.getString(0)).isEqualTo("Σπίτι")
                assertThat(row.getLong(1)).isEqualTo(2_400_000L)
            }
            assertThat(db.count("sync_peer")).isEqualTo(0)
            assertThat(db.count("sync_event")).isEqualTo(0)
        }
    }

    @Test
    fun `6 to 7 keeps receipts with their lines and lets a receipt have no link`() {
        helper.createDatabase(NAME, 6).use { db ->
            db.execSQL(
                """INSERT INTO merchant (id, country_code, vat_number, legal_name, display_name,
                   normalized_display_name, display_name_set_by_user, activity_description,
                   tax_office, gemi_number, street, street_number, city, postal_code,
                   details_as_of, created_at, updated_at) VALUES ('m1', 'GR', '094014201', 'A',
                   'A', 'a', 0, '', '', '', '', '', '', '', NULL, 1, 1)""",
            )
            db.receipt("r1", mark = "1", url = "'https://a.invalid/1'")
            db.execSQL(
                """INSERT INTO receipt_item (id, receipt_id, line_number, line_type, description,
                   normalized_description, gross_amount_minor, match_status, user_note,
                   created_at, updated_at) VALUES ('i1', 'r1', 1, 'other', 'X', 'x', 100,
                   'not_a_product', '', 1, 1)""",
            )
            db.execSQL("INSERT INTO tombstone (entity, id, deleted_at) VALUES ('receipt', 'r0', 1)")
        }

        helper.runMigrationsAndValidate(NAME, 7, true).use { db ->
            assertThat(db.count("receipt")).isEqualTo(1)
            assertThat(db.count("receipt_item")).isEqualTo(1)
            db.query("SELECT natural_key, replaced_by FROM tombstone").use { row ->
                assertThat(row.moveToFirst()).isTrue()
                assertThat(row.isNull(0) && row.isNull(1)).isTrue()
            }
            // A receipt from another phone has no link.
            db.receipt("r2", mark = "2", url = "NULL")
            assertThat(db.count("receipt")).isEqualTo(2)
        }
    }

    @Test
    fun `7 to 8 adds manual expenses, budgets by month, bills and category names`() {
        helper.createDatabase(NAME, 7).use { db ->
            db.execSQL(
                """INSERT INTO merchant (id, country_code, vat_number, legal_name, display_name,
                   normalized_display_name, display_name_set_by_user, activity_description,
                   tax_office, gemi_number, street, street_number, city, postal_code,
                   details_as_of, created_at, updated_at) VALUES ('m1', 'GR', '094014201', 'A',
                   'A', 'a', 0, '', '', '', '', '', '', '', NULL, 1, 1)""",
            )
            db.receipt("r1", mark = "1", url = "'https://a.invalid/1'")
            db.execSQL(
                """INSERT INTO category (id, slug, name, is_system, created_at, updated_at)
                   VALUES ('c1', 'services', 'Services', 1, 1, 1)""",
            )
            db.execSQL(
                """INSERT INTO budget (id, category_id, amount_minor, created_at, updated_at)
                   VALUES ('total', NULL, 70000, 1, 1), ('c1', 'c1', 5000, 1, 1)""",
            )
        }

        helper.runMigrationsAndValidate(NAME, 8, true).use { db ->
            // The budgets become their scope's default; a month's row may have no amount.
            db.query("SELECT id, month, amount_minor FROM budget ORDER BY id").use { row ->
                assertThat(row.moveToFirst()).isTrue()
                assertThat(row.getString(0)).isEqualTo("c1")
                assertThat(row.isNull(1)).isTrue()
                assertThat(row.moveToNext()).isTrue()
                assertThat(row.getLong(2)).isEqualTo(70000L)
            }
            db.execSQL(
                """INSERT INTO budget (id, category_id, amount_minor, created_at, updated_at,
                   month) VALUES ('total:2026-10', NULL, NULL, 1, 1, '2026-10')""",
            )
            db.execSQL(
                """INSERT INTO bill (id, name, kind, amount_minor, start_month, end_month,
                   position, created_at, updated_at)
                   VALUES ('b1', 'Rent', 'fixed', 65000, '2026-10', NULL, 0, 1, 1)""",
            )
            db.execSQL(
                """INSERT INTO bill_month (id, bill_id, month, amount_minor, created_at,
                   updated_at) VALUES ('b1:2026-11', 'b1', '2026-11', 70000, 1, 1)""",
            )
            // Deleting a bill deletes its months; a system category can carry a name of its own.
            db.execSQL("DELETE FROM bill WHERE id = 'b1'")
            assertThat(db.count("bill_month")).isEqualTo(0)
            db.execSQL("UPDATE category SET custom_name = 'Γιατροί' WHERE id = 'c1'")
            db.query("SELECT origin, category_id, manual_kind FROM receipt").use { row ->
                assertThat(row.moveToFirst()).isTrue()
                assertThat(row.getString(0)).isEqualTo("import")
                assertThat(row.isNull(1)).isTrue()
                assertThat(row.isNull(2)).isTrue()
            }
            db.execSQL(
                "UPDATE receipt SET origin = 'manual', category_id = 'c1', manual_kind = 'service'",
            )
            // Deleting the category leaves the receipt, without a category.
            db.execSQL("DELETE FROM category WHERE id = 'c1'")
            db.query("SELECT origin, category_id FROM receipt").use { row ->
                assertThat(row.moveToFirst()).isTrue()
                assertThat(row.getString(0)).isEqualTo("manual")
                assertThat(row.isNull(1)).isTrue()
            }
        }
    }

    @Test
    fun `the first release upgrades to the current schema in one go`() {
        // 0.1.1 shipped schema 4: its users go through every migration at once.
        helper.createDatabase(NAME, 4).use { db ->
            db.execSQL(
                """INSERT INTO household (id, name, default_currency, timezone, created_at,
                   updated_at) VALUES ('h1', 'Σπίτι', 'EUR', 'Europe/Athens', 1, 1)""",
            )
            db.execSQL(
                """INSERT INTO merchant (id, country_code, vat_number, legal_name, display_name,
                   normalized_display_name, display_name_set_by_user, activity_description,
                   tax_office, gemi_number, street, street_number, city, postal_code,
                   details_as_of, created_at, updated_at) VALUES ('m1', 'GR', '094014201', 'A',
                   'A', 'a', 0, '', '', '', '', '', '', '', NULL, 1, 1)""",
            )
            db.receipt("r1", mark = "1", url = "'https://a.invalid/1'")
            db.execSQL(
                """INSERT INTO receipt_item (id, receipt_id, line_number, line_type, description,
                   normalized_description, gross_amount_minor, match_status, user_note,
                   created_at, updated_at) VALUES ('i1', 'r1', 1, 'other', 'X', 'x', 100,
                   'not_a_product', '', 1, 1)""",
            )
            db.execSQL("INSERT INTO tombstone (entity, id, deleted_at) VALUES ('receipt', 'r0', 1)")
        }

        helper.runMigrationsAndValidate(NAME, 8, true).use { db ->
            assertThat(db.count("household")).isEqualTo(1)
            assertThat(db.count("receipt")).isEqualTo(1)
            assertThat(db.count("receipt_item")).isEqualTo(1)
            assertThat(db.count("tombstone")).isEqualTo(1)
            assertThat(db.count("budget")).isEqualTo(0)
            assertThat(db.count("sync_event")).isEqualTo(0)
            db.query("SELECT source_url FROM receipt").use { row ->
                assertThat(row.moveToFirst()).isTrue()
                assertThat(row.getString(0)).isEqualTo("https://a.invalid/1")
            }
        }
    }

    /** [url] is SQL: a quoted string or NULL. */
    private fun SupportSQLiteDatabase.receipt(id: String, mark: String, url: String) = execSQL(
        """INSERT INTO receipt (id, merchant_id, store_id, provider_code, parser_version,
           issuer_country, issuer_vat, uid, mark, cancellation_mark, source_key, document_kind,
           document_type_code, document_number, issued_at, issued_local_date, currency,
           gross_total_minor, net_total_minor, vat_total_minor, discount_total_minor,
           payment_method, status, validation_warnings, excluded_from_analytics, user_note,
           source_url, source_url_fingerprint, source_data, created_at, updated_at) VALUES
           ('$id', 'm1', NULL, 'impact', '1', 'GR', '094014201', NULL, '$mark', NULL, NULL,
           'sale', NULL, 'A-1', 1, '2026-11-15', 'EUR', 100, NULL, NULL, NULL, 'card',
           'active', '[]', 0, '', $url, 'fp', '{}', 1, 1)""",
    )

    private fun SupportSQLiteDatabase.count(table: String): Int =
        query("SELECT COUNT(*) FROM `$table`").use {
            it.moveToFirst()
            it.getInt(0)
        }

    private companion object {
        const val NAME = "migration-test.db"
    }
}
