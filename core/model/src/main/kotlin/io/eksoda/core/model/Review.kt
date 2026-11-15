package io.eksoda.core.model

import java.math.BigDecimal
import java.time.OffsetDateTime

/** A receipt line waiting in the review queue (backend/apps/products/review.py). */
public data class ReviewItem(
    val id: String,
    val receiptId: String,
    val issuedAt: OffsetDateTime,
    val merchantName: String,
    val lineNumber: Int,
    val description: String,
    val merchantProductCode: String?,
    val quantity: BigDecimal?,
    val unit: String?,
    val unitPrice: BigDecimal?,
    val grossAmount: Money,
    val userNote: String,
)
