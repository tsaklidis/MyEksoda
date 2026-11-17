package io.eksoda.core.database.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Query

// Spending analytics as SQL. Every query
// counts the *included* receipts: active, not excluded, in the household's currency, issued in
// [start, end] (local dates, YYYY-MM-DD, both inclusive). Credit notes count as negative.

/** Totals of the included receipts, and the count of receipts in other currencies. */
data class TotalsRow(
    @ColumnInfo(name = "spending_minor") val spendingMinor: Long,
    @ColumnInfo(name = "receipt_count") val receiptCount: Int,
    @ColumnInfo(name = "sales_count") val salesCount: Int,
    @ColumnInfo(name = "sales_total_minor") val salesTotalMinor: Long,
    @ColumnInfo(name = "credit_notes_minor") val creditNotesMinor: Long,
    @ColumnInfo(name = "other_currency_receipts") val otherCurrencyReceipts: Int,
)

/** Signed line totals by line type and category (product, else merchant default). */
data class CategoryLineRow(
    @ColumnInfo(name = "line_type") val lineType: String,
    @ColumnInfo(name = "category_id") val categoryId: String?,
    @ColumnInfo(name = "total_minor") val totalMinor: Long,
)

/** A signed total per merchant default category (null: the merchant has none). */
data class DefaultCategoryTotalRow(
    @ColumnInfo(name = "default_category_id") val defaultCategoryId: String?,
    @ColumnInfo(name = "total_minor") val totalMinor: Long,
)

/** A signed total for one key (a month "YYYY-MM" or a day "YYYY-MM-DD"). */
data class KeyTotalRow(
    @ColumnInfo(name = "period_key") val key: String,
    @ColumnInfo(name = "total_minor") val totalMinor: Long,
    val receipts: Int,
)

/** `spending_by_merchant`. */
data class MerchantTotalRow(
    @ColumnInfo(name = "merchant_id") val merchantId: String,
    @ColumnInfo(name = "merchant_name") val merchantName: String,
    @ColumnInfo(name = "total_minor") val totalMinor: Long,
    val receipts: Int,
)

/** `top_products`: over the PRICE_HISTORY lines of the included receipts. */
data class TopProductRow(
    @ColumnInfo(name = "product_id") val productId: String,
    @ColumnInfo(name = "product_name") val productName: String,
    val receipts: Int,
    @ColumnInfo(name = "spend_minor") val spendMinor: Long,
)

private const val INCLUDED = """receipt.status = 'active' AND receipt.excluded_from_analytics = 0
    AND receipt.issued_local_date >= :start AND receipt.issued_local_date <= :end"""
private const val OWN = "receipt.currency = :currency"
private const val SIGNED_TOTAL =
    "CASE WHEN receipt.document_kind = 'credit_note' THEN -receipt.gross_total_minor " +
        "ELSE receipt.gross_total_minor END"
private const val SIGNED_LINE =
    "CASE WHEN receipt.document_kind = 'credit_note' THEN -receipt_item.gross_amount_minor " +
        "ELSE receipt_item.gross_amount_minor END"

@Dao
interface AnalyticsDao {
    @Query(
        """SELECT
            COALESCE(SUM(CASE WHEN $OWN THEN $SIGNED_TOTAL END), 0) AS spending_minor,
            COUNT(CASE WHEN $OWN THEN 1 END) AS receipt_count,
            COUNT(CASE WHEN $OWN AND receipt.document_kind = 'sale' THEN 1 END) AS sales_count,
            COALESCE(SUM(CASE WHEN $OWN AND receipt.document_kind = 'sale'
                THEN receipt.gross_total_minor END), 0) AS sales_total_minor,
            COALESCE(SUM(CASE WHEN $OWN AND receipt.document_kind = 'credit_note'
                THEN receipt.gross_total_minor END), 0) AS credit_notes_minor,
            COUNT(CASE WHEN receipt.currency != :currency THEN 1 END) AS other_currency_receipts
           FROM receipt WHERE $INCLUDED""",
    )
    suspend fun totals(start: String, end: String, currency: String): TotalsRow

    /** Line totals: a product line's category is its product's, else the merchant default. */
    @Query(
        """SELECT receipt_item.line_type AS line_type,
            CASE WHEN receipt_item.line_type = 'product'
                THEN COALESCE(product.category_id, merchant.default_category_id) END AS category_id,
            SUM($SIGNED_LINE) AS total_minor
           FROM receipt_item
           JOIN receipt ON receipt.id = receipt_item.receipt_id
           JOIN merchant ON merchant.id = receipt.merchant_id
           LEFT JOIN product ON product.id = receipt_item.product_id
           WHERE $INCLUDED AND $OWN
           GROUP BY 1, 2""",
    )
    suspend fun categoryLines(start: String, end: String, currency: String): List<CategoryLineRow>

    /** Receipt totals by their merchant's default category. */
    @Query(
        """SELECT merchant.default_category_id AS default_category_id,
            SUM($SIGNED_TOTAL) AS total_minor
           FROM receipt JOIN merchant ON merchant.id = receipt.merchant_id
           WHERE $INCLUDED AND $OWN
           GROUP BY 1""",
    )
    suspend fun receiptTotalsByDefaultCategory(
        start: String,
        end: String,
        currency: String,
    ): List<DefaultCategoryTotalRow>

    /** Line totals by their receipt's merchant default category (for the remainders). */
    @Query(
        """SELECT merchant.default_category_id AS default_category_id,
            SUM($SIGNED_LINE) AS total_minor
           FROM receipt_item
           JOIN receipt ON receipt.id = receipt_item.receipt_id
           JOIN merchant ON merchant.id = receipt.merchant_id
           WHERE $INCLUDED AND $OWN
           GROUP BY 1""",
    )
    suspend fun lineTotalsByDefaultCategory(
        start: String,
        end: String,
        currency: String,
    ): List<DefaultCategoryTotalRow>

    @Query(
        """SELECT substr(receipt.issued_local_date, 1, 7) AS period_key,
            SUM($SIGNED_TOTAL) AS total_minor, COUNT(*) AS receipts
           FROM receipt WHERE $INCLUDED AND $OWN
           GROUP BY 1 ORDER BY 1""",
    )
    suspend fun monthTotals(start: String, end: String, currency: String): List<KeyTotalRow>

    @Query(
        """SELECT receipt.issued_local_date AS period_key,
            SUM($SIGNED_TOTAL) AS total_minor, COUNT(*) AS receipts
           FROM receipt WHERE $INCLUDED AND $OWN
           GROUP BY 1 ORDER BY 1""",
    )
    suspend fun dayTotals(start: String, end: String, currency: String): List<KeyTotalRow>

    @Query(
        """SELECT merchant.id AS merchant_id, merchant.display_name AS merchant_name,
            SUM($SIGNED_TOTAL) AS total_minor, COUNT(*) AS receipts
           FROM receipt JOIN merchant ON merchant.id = receipt.merchant_id
           WHERE $INCLUDED AND $OWN
           GROUP BY merchant.id ORDER BY total_minor DESC, merchant.display_name""",
    )
    suspend fun merchantTotals(start: String, end: String, currency: String): List<MerchantTotalRow>

    /** [byCount]: by the number of receipts the product was on; else by spend. */
    @Query(
        """SELECT product.id AS product_id, product.name AS product_name,
            COUNT(DISTINCT receipt_item.receipt_id) AS receipts,
            SUM(receipt_item.gross_amount_minor) AS spend_minor
           FROM receipt_item
           JOIN receipt ON receipt.id = receipt_item.receipt_id
           JOIN product ON product.id = receipt_item.product_id
           WHERE $INCLUDED AND $OWN
             AND receipt_item.line_type = 'product'
             AND receipt_item.normalized_unit_price_e4 IS NOT NULL
             AND receipt_item.quantity_e4 > 0 AND receipt_item.gross_amount_minor > 0
             AND receipt.document_kind = 'sale'
           GROUP BY product.id
           ORDER BY CASE WHEN :byCount THEN COUNT(DISTINCT receipt_item.receipt_id)
                ELSE SUM(receipt_item.gross_amount_minor) END DESC, product.name
           LIMIT :limit""",
    )
    suspend fun topProducts(
        start: String,
        end: String,
        currency: String,
        byCount: Boolean,
        limit: Int,
    ): List<TopProductRow>

    /** `needs_review_count`: lines (not receipts) waiting to be matched. */
    @Query("SELECT COUNT(*) FROM receipt_item WHERE match_status = 'needs_review'")
    suspend fun needsReviewCount(): Int
}
