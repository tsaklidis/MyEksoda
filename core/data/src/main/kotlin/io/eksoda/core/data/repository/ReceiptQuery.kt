package io.eksoda.core.data.repository

import androidx.sqlite.db.SimpleSQLiteQuery
import io.eksoda.core.data.util.Fixed
import io.eksoda.core.database.dao.ReceiptDao
import io.eksoda.core.model.ReceiptFilter
import io.eksoda.core.model.ReceiptOrdering
import io.eksoda.engine.text.normalizeText
import java.math.BigDecimal

/**
 * SQL for the receipt list. Search matches normalized text with
 * `instr`, not LIKE, so there is no wildcard escaping and no ASCII-only case folding.
 * Every value is a bound argument; only fixed SQL fragments are concatenated.
 */
internal object ReceiptQuery {
    fun list(filter: ReceiptFilter): SimpleSQLiteQuery {
        val where = Conditions()
        where.text(filter.query)
        where.references(filter)
        where.ranges(filter)
        where.flags(filter)
        val sql = ReceiptDao.LIST_SELECT + where.clause() +
            " ORDER BY ${orderBy(filter.ordering)}"
        return SimpleSQLiteQuery(sql, where.args())
    }

    fun one(id: String): SimpleSQLiteQuery =
        SimpleSQLiteQuery("${ReceiptDao.ROW_SELECT} WHERE receipt.id = ?", arrayOf(id))

    /**
     * The id breaks ties so pages are stable. It runs in the same direction as the first key, so
     * the (issued_at, id) and (gross_total_minor, id) indexes give the order without a sort.
     */
    private fun orderBy(ordering: ReceiptOrdering): String = when (ordering) {
        ReceiptOrdering.NEWEST -> "receipt.issued_at DESC, receipt.id DESC"
        ReceiptOrdering.OLDEST -> "receipt.issued_at ASC, receipt.id ASC"
        ReceiptOrdering.HIGHEST_TOTAL -> "receipt.gross_total_minor DESC, receipt.id DESC"
        ReceiptOrdering.LOWEST_TOTAL -> "receipt.gross_total_minor ASC, receipt.id ASC"
    }

    private class Conditions {
        private val clauses = mutableListOf<String>()
        private val args = mutableListOf<Any>()

        fun add(condition: String, vararg values: Any) {
            clauses += condition
            args.addAll(values)
        }

        fun clause(): String =
            if (clauses.isEmpty()) "" else " WHERE " + clauses.joinToString(" AND ")

        fun args(): Array<Any> = args.toTypedArray()

        /** Text in the merchant's name or in a line description. */
        fun text(query: String?) {
            val key = query?.let(::normalizeText).orEmpty()
            if (key.isEmpty()) return
            add("(instr(merchant.normalized_display_name, ?) > 0 OR $ITEM_TEXT)", key, key)
        }

        fun references(filter: ReceiptFilter) {
            filter.merchant?.let { add("receipt.merchant_id = ?", it.id) }
            filter.store?.let { add("receipt.store_id = ?", it.id) }
            filter.category?.let { add(CATEGORY, it.id, it.id) }
            filter.product?.let { add(ITEM_PRODUCT, it.id) }
        }

        /** Days in the household timezone (`issued_local_date`) and totals, both inclusive. */
        fun ranges(filter: ReceiptFilter) {
            filter.issuedFrom?.let { add("receipt.issued_local_date >= ?", it.toString()) }
            filter.issuedTo?.let { add("receipt.issued_local_date <= ?", it.toString()) }
            filter.minTotal?.let { add("receipt.gross_total_minor >= ?", minor(it)) }
            filter.maxTotal?.let { add("receipt.gross_total_minor <= ?", minor(it)) }
        }

        fun flags(filter: ReceiptFilter) {
            if (filter.needsReview) add(ITEM_NEEDS_REVIEW)
            if (filter.hasWarnings) add("receipt.validation_warnings != '[]'")
            filter.documentKind?.let { add("receipt.document_kind = ?", it.code) }
            if (filter.hideCancelled) add("receipt.status = 'active'")
        }

        private fun minor(value: BigDecimal): Long = Fixed.toLong(value, Fixed.MINOR)
    }

    private const val ITEM_TEXT = "EXISTS (SELECT 1 FROM receipt_item i " +
        "WHERE i.receipt_id = receipt.id AND instr(i.normalized_description, ?) > 0)"
    private const val ITEM_CATEGORY = "EXISTS (SELECT 1 FROM receipt_item i " +
        "JOIN product p ON p.id = i.product_id " +
        "WHERE i.receipt_id = receipt.id AND p.category_id = ?)"

    /** A product of the category, or a merchant whose default category it is. */
    private const val CATEGORY = "($ITEM_CATEGORY OR merchant.default_category_id = ?)"
    private const val ITEM_PRODUCT = "EXISTS (SELECT 1 FROM receipt_item i " +
        "WHERE i.receipt_id = receipt.id AND i.product_id = ?)"
    private const val ITEM_NEEDS_REVIEW = "EXISTS (SELECT 1 FROM receipt_item i " +
        "WHERE i.receipt_id = receipt.id AND i.match_status = 'needs_review')"
}
