package io.eksoda.core.model

import java.math.BigDecimal
import java.time.OffsetDateTime

public data class Receipt(
    val id: String,
    val merchant: Merchant,
    val store: Store?,
    val issuedAt: OffsetDateTime,
    val totalAmount: Money,
    val items: List<ReceiptItem>,
    val createdAt: OffsetDateTime,
)

public data class Merchant(
    val id: String,
    val name: String,
)

public data class Store(
    val id: String,
    val name: String,
    val address: String?,
    val city: String?,
)

public data class ReceiptItem(
    val id: String,
    /** The linked product's id and name, if the line is matched. */
    val productId: String?,
    val productName: String?,
    val description: String,
    val quantity: BigDecimal?,
    val unit: String?,
    val unitPrice: Money?,
    val totalPrice: Money,
    val isProduct: Boolean,
)
