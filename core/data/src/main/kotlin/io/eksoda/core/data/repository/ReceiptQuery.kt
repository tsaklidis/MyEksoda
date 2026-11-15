package io.eksoda.core.data.repository

import androidx.sqlite.db.SimpleSQLiteQuery
import io.eksoda.core.data.util.Fixed
import io.eksoda.core.database.dao.ReceiptDao
import io.eksoda.core.model.ReceiptFilter
import io.eksoda.core.model.ReceiptOrdering
import io.eksoda.engine.text.normalizeText

/**
 * SQL for the receipt list (android-app.md §6.4.2). Search matches normalized text with
 * `instr`, not LIKE, so there is no wildcard escaping and no ASCII-only case folding (§6.5).
 */
internal object ReceiptQuery {
    fun list(filter: ReceiptFilter): SimpleSQLiteQuery {
        val conditions = mutableListOf<String>()
        val args = mutableListOf<Any>()
        fun where(condition: String, vararg values: Any) {
            conditions += condition
            args.addAll(values)
        }
        filter.query?.let(::normalizeText)?.takeIf { it.isNotEmpty() }?.let { key ->
            where(
                "(instr(merchant.normalized_display_name, ?) > 0 OR EXISTS (SELECT 1 FROM receipt_item i " +
                    "WHERE i.receipt_id = receipt.id AND instr(i.normalized_description, ?) > 0))",
                key,
                key,
            )
        }
        filter.merchant?.let { where("receipt.merchant_id = ?", it.id) }
        filter.store?.let { where("receipt.store_id = ?", it.id) }
        filter.category?.let {
            where(
                "EXISTS (SELECT 1 FROM receipt_item i JOIN product p ON p.id = i.product_id " +
                    "WHERE i.receipt_id = receipt.id AND p.category_id = ?)",
                it.id,
            )
        }
        filter.product?.let {
            where("EXISTS (SELECT 1 FROM receipt_item i WHERE i.receipt_id = receipt.id AND i.product_id = ?)", it.id)
        }
        filter.issuedFrom?.let { where("receipt.issued_local_date >= ?", it.toString()) }
        filter.issuedTo?.let { where("receipt.issued_local_date <= ?", it.toString()) }
        filter.minTotal?.let { where("receipt.gross_total_minor >= ?", Fixed.toLong(it, Fixed.MINOR)) }
        filter.maxTotal?.let { where("receipt.gross_total_minor <= ?", Fixed.toLong(it, Fixed.MINOR)) }
        if (filter.needsReview) {
            where("EXISTS (SELECT 1 FROM receipt_item i WHERE i.receipt_id = receipt.id AND i.match_status = 'needs_review')")
        }
        if (filter.hasWarnings) where("receipt.validation_warnings != '[]'")
        filter.documentKind?.let { where("receipt.document_kind = ?", it.code) }
        if (filter.hideCancelled) where("receipt.status = 'active'")

        val order = when (filter.ordering) {
            ReceiptOrdering.NEWEST -> "receipt.issued_at DESC"
            ReceiptOrdering.OLDEST -> "receipt.issued_at ASC"
            ReceiptOrdering.HIGHEST_TOTAL -> "receipt.gross_total_minor DESC"
            ReceiptOrdering.LOWEST_TOTAL -> "receipt.gross_total_minor ASC"
        }
        val whereClause = if (conditions.isEmpty()) "" else " WHERE " + conditions.joinToString(" AND ")
        return SimpleSQLiteQuery("${ReceiptDao.ROW_SELECT}$whereClause ORDER BY $order, receipt.id", args.toTypedArray())
    }

    fun one(id: String): SimpleSQLiteQuery = SimpleSQLiteQuery("${ReceiptDao.ROW_SELECT} WHERE receipt.id = ?", arrayOf(id))
}
