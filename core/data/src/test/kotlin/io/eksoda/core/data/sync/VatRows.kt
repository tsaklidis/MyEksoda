package io.eksoda.core.data.sync

import androidx.sqlite.db.SupportSQLiteDatabase

/** Rows for the VAT repair's tests: Sklavenitis under its VAT, and under VAT plus phone digits. */
internal object VatRows {
    const val RIGHT = "800764388"
    const val WRONG = "8007643882141009999214100999814009120100058777"

    /** [right] from another provider; [wrong] from the first Entersoft parser, with an import. */
    fun SupportSQLiteDatabase.household(right: String, wrong: String) {
        merchant(right, RIGHT, "Σκλαβενίτης")
        merchant(wrong, WRONG, "ΕΛΛΗΝΙΚΕΣ ΥΠΕΡΑΓΟΡΕΣ ΣΚΛΑΒΕΝΙΤΗΣ Α.Ε.Ε.")
        receipt("r1", right, RIGHT, "400001", provider = "epsilon", version = "epsilon/1")
        receipt("r2", wrong, WRONG, "400002")
        job("j2", receipt = "r2")
    }

    fun SupportSQLiteDatabase.merchant(
        id: String,
        vat: String,
        name: String,
        country: String = "GR",
    ) = execSQL(
        "INSERT INTO merchant (id, country_code, vat_number, legal_name, display_name, " +
            "normalized_display_name, display_name_set_by_user, activity_description, " +
            "tax_office, gemi_number, street, street_number, city, postal_code, details_as_of, " +
            "created_at, updated_at) VALUES (?, ?, ?, ?, ?, '', 0, '', '', '', '', '', '', " +
            "'', NULL, 1, 1)",
        arrayOf<Any?>(id, country, vat, name, name),
    )

    fun SupportSQLiteDatabase.receipt(
        id: String,
        merchant: String,
        vat: String,
        mark: String,
        provider: String = "entersoft",
        version: String = "entersoft/1",
    ) = execSQL(
        "INSERT INTO receipt (id, merchant_id, store_id, provider_code, parser_version, " +
            "issuer_country, issuer_vat, uid, mark, cancellation_mark, source_key, " +
            "document_kind, document_type_code, document_number, issued_at, " +
            "issued_local_date, currency, gross_total_minor, net_total_minor, vat_total_minor, " +
            "discount_total_minor, payment_method, status, validation_warnings, " +
            "excluded_from_analytics, user_note, source_url, source_url_fingerprint, " +
            "source_data, created_at, updated_at) VALUES (?, ?, NULL, ?, ?, 'GR', " +
            "?, NULL, ?, NULL, NULL, 'sale', '11.1', 'A-1', 1763200000000, '2026-11-15', " +
            "'EUR', 321, NULL, NULL, NULL, 'card', 'active', '[]', 0, '', ?, 'fp', ?, 1, 1)",
        arrayOf<Any?>(
            id,
            merchant,
            provider,
            version,
            vat,
            mark,
            "https://e-invoicing.gr/$id",
            """{"issuer":{"vat_number":"$vat"}}""",
        ),
    )

    fun SupportSQLiteDatabase.job(id: String, receipt: String) = execSQL(
        "INSERT INTO import_job (id, provider_code, url_identity, submitted_url, " +
            "url_fingerprint, source_host, status, outcome, attempt_count, receipt_id, " +
            "created_at, updated_at) VALUES (?, 'entersoft', ?, 'https://x.invalid', 'fp', " +
            "'x.invalid', 'completed', 'created', 1, ?, 1, 1)",
        arrayOf<Any?>(id, id, receipt),
    )
}
