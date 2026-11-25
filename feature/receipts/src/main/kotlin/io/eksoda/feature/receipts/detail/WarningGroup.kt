package io.eksoda.feature.receipts.detail

import androidx.annotation.StringRes
import io.eksoda.core.model.ReceiptItem
import io.eksoda.core.model.ReceiptWarning
import io.eksoda.feature.receipts.R

/**
 * Warnings that read the same, and the receipt lines they concern (in receipt order).
 * [amountCheck]: a check of the receipt's own arithmetic, the amounts kept as printed; otherwise
 * a notice that needs the user's attention (e.g. the QR signature differs from AADE's record).
 */
internal data class WarningGroup(
    @param:StringRes val message: Int,
    val lines: List<Int>,
    val amountCheck: Boolean,
)

/**
 * One group per message, in the order the warnings first give it: three items whose unit price
 * does not fit read as one message for the three items, not as three identical lines.
 */
internal fun groupWarnings(warnings: List<ReceiptWarning>): List<WarningGroup> =
    warnings.groupBy { warningRes(it.code) }.map { (message, group) ->
        WarningGroup(
            message = message,
            lines = group.mapNotNull { it.lineNumber }.distinct().sorted(),
            amountCheck = group.all { it.code in AMOUNT_CHECKS },
        )
    }

/**
 * The item of each receipt line, by line number: the items whose rows can carry a mark. A number
 * more than one item claims is left out: its warning is not put on an item it may not be about,
 * and reads "line N" instead.
 */
internal fun lineItems(items: List<ReceiptItem>): Map<Int, ReceiptItem> {
    val byLine = items.groupBy { it.lineNumber }
    return byLine.filterValues { it.size == 1 }.mapValues { (_, same) -> same.single() }
}

/** One of an item's warnings, as its mark opens it. */
internal data class LineWarning(@param:StringRes val message: Int, val amountCheck: Boolean)

/**
 * The warnings of each item, by item id, one per message: the item's row is marked, and its mark
 * opens these. [byLine] is [lineItems]: items without warnings, and warnings on a line number no
 * single item has, are left out.
 */
internal fun itemWarnings(
    warnings: List<ReceiptWarning>,
    byLine: Map<Int, ReceiptItem>,
): Map<String, List<LineWarning>> = warnings
    .mapNotNull { warning -> warning.lineNumber?.let(byLine::get)?.let { it.id to warning } }
    .groupBy({ it.first }, { it.second })
    .mapValues { (_, own) -> groupWarnings(own).map { LineWarning(it.message, it.amountCheck) } }

/**
 * The position in [items] of the first item on one of [lines] that carries a mark ([marked]):
 * where "Show" in the warnings card scrolls to. Null when none is marked.
 */
internal fun firstMarkedItem(items: List<ReceiptItem>, lines: List<Int>, marked: Set<Int>): Int? =
    items.indexOfFirst { it.lineNumber in lines && it.lineNumber in marked }.takeIf { it >= 0 }

/** A warning's lines: how many carry a mark in the item list, and the others' numbers. */
internal data class LineMarks(val marked: Int, val unmarked: List<Int>)

/**
 * [lines] split by whether their row is marked ([marked]: the keys of [lineItems], or none when
 * the receipt shows no item list).
 */
internal fun lineMarks(lines: List<Int>, marked: Set<Int>): LineMarks {
    val (inList, others) = lines.partition { it in marked }
    return LineMarks(inList.size, others)
}

/** The engine's arithmetic checks (`validation`): amounts that do not agree with each other. */
private val AMOUNT_CHECKS = setOf(
    "line_amount_mismatch",
    "unit_price_mismatch",
    "discount_mismatch",
    "discount_unreadable",
    "vat_rate_mismatch",
    "total_mismatch",
    "receipt_amount_mismatch",
    "total_discount_mismatch",
    "line_discounts_mismatch",
    "vat_breakdown_mismatch",
    "vat_breakdown_rate_mismatch",
    "payment_mismatch",
)

/** Validation issue and provider notice codes (the engine's `validation`), as local strings. */
@StringRes
internal fun warningRes(code: String): Int = when (code) {
    "line_amount_mismatch" -> R.string.warning_line_amount
    "unit_price_mismatch" -> R.string.warning_unit_price
    "discount_mismatch" -> R.string.warning_discount
    "discount_unreadable" -> R.string.warning_discount_unreadable
    "vat_rate_mismatch" -> R.string.warning_vat_rate
    "total_mismatch", "receipt_amount_mismatch" -> R.string.warning_total
    "total_discount_mismatch", "line_discounts_mismatch" -> R.string.warning_total_discount
    "vat_breakdown_mismatch", "vat_breakdown_rate_mismatch" -> R.string.warning_vat_breakdown
    "payment_mismatch" -> R.string.warning_payment
    "signature_mismatch" -> R.string.warning_signature_mismatch
    "amount_mismatch" -> R.string.warning_qr_amount
    "mark_missing" -> R.string.warning_mark_missing
    "non_eur_currency" -> R.string.warning_currency
    "unknown_document_type" -> R.string.warning_document_type
    else -> R.string.warning_other
}
