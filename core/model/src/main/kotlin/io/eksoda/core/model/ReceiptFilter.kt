package io.eksoda.core.model

import java.time.LocalDate

public data class ReceiptFilter(
    val query: String? = null,
    val merchantId: String? = null,
    val categoryId: String? = null,
    val issuedFrom: LocalDate? = null,
    val issuedTo: LocalDate? = null,
    val minAmount: Money? = null,
    val maxAmount: Money? = null,
    val isExcludedFromAnalytics: Boolean? = null,
    val ordering: ReceiptOrdering = ReceiptOrdering.ISSUED_AT_DESC,
)

public enum class ReceiptOrdering {
    ISSUED_AT_DESC,
    ISSUED_AT_ASC,
    AMOUNT_DESC,
    AMOUNT_ASC,
}
