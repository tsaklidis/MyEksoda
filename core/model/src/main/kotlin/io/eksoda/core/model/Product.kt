package io.eksoda.core.model

import java.math.BigDecimal

public data class Product(
    val id: String,
    val name: String,
    val category: Category?,
    val brand: String?,
    /** Package size, e.g. 500 with unit "g". Both set or both null. */
    val packageQuantity: BigDecimal?,
    val packageUnit: String?,
    /** Receipt lines linked to the product. */
    val lineCount: Int,
)
