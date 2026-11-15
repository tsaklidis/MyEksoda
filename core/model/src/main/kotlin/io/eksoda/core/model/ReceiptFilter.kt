package io.eksoda.core.model

import java.math.BigDecimal
import java.time.LocalDate

/** The receipt list's search and filters (android-app.md §6.4.2). Null means "any". */
public data class ReceiptFilter(
    /** Text in a line description or the merchant name, compared as normalized text (§6.5). */
    val query: String? = null,
    val merchant: Option? = null,
    val store: Option? = null,
    val category: Option? = null,
    val product: Option? = null,
    /** Days in the household timezone, both inclusive. */
    val issuedFrom: LocalDate? = null,
    val issuedTo: LocalDate? = null,
    val minTotal: BigDecimal? = null,
    val maxTotal: BigDecimal? = null,
    val needsReview: Boolean = false,
    val hasWarnings: Boolean = false,
    /** null: purchases and refunds. */
    val documentKind: DocumentKind? = null,
    val hideCancelled: Boolean = false,
    val ordering: ReceiptOrdering = ReceiptOrdering.NEWEST,
) {
    /** True when anything but the search text and ordering narrows the list. */
    val hasFilters: Boolean
        get() = merchant != null || store != null || category != null || product != null ||
            issuedFrom != null || issuedTo != null || minTotal != null || maxTotal != null ||
            needsReview || hasWarnings || documentKind != null || hideCancelled
}

public enum class ReceiptOrdering { NEWEST, OLDEST, HIGHEST_TOTAL, LOWEST_TOTAL }
