// Port of the decisions in backend/apps/products/matching.py. The lookups and writes live in
// core:data (docs/android-standalone.md §9.1).
package io.eksoda.engine.products

import io.eksoda.engine.merchants.FieldSize
import io.eksoda.engine.mydata.Unit
import io.eksoda.engine.text.SequenceMatcher
import io.eksoda.engine.text.pyStrip

// Matching receipt lines to household products (requirements §9.2, TD §13.2).
//
// The key of a line is its merchant item code, or its normalized description when the merchant
// prints no code:
//
// 1. A mapping exists for the key → the line is matched to the mapping's product. Guard (code
//    keys only): when the description differs too much from the last one seen for that code, the
//    merchant may have reused the code for another item, so the line goes to review and the
//    mapping is left alone.
// 2. No mapping → a product is created from the line (its name is the description, with a
//    package size when the description states one unambiguously) together with a mapping, so
//    the item's price history starts right away (D7).

/** MATCH_GUARD_SIMILARITY in the server's settings. */
public const val MATCH_GUARD_SIMILARITY: Double = 0.4

/**
 * The mapping key (§6.3): `code:<code>` when the line has an item code, otherwise
 * `desc:<normalized description>`. It replaces the server's two partial unique indexes.
 */
public fun mappingKey(merchantProductCode: String?, normalizedDescription: String): String =
    if (merchantProductCode.isNullOrEmpty()) {
        "desc:$normalizedDescription"
    } else {
        "code:$merchantProductCode"
    }

/**
 * The code-reuse guard. Shops give an old item code to a new item, and trusting the code alone
 * would put the new item's prices in the old product's history. Small wording changes
 * ("ΦΡΕΣΚΟ" → "ΦΡ.") stay above the threshold and still match.
 */
public fun isReusedCode(
    itemNormalizedDescription: String,
    mappingNormalizedDescription: String,
): Boolean = SequenceMatcher(itemNormalizedDescription, mappingNormalizedDescription).ratio() <
    MATCH_GUARD_SIMILARITY

/** A product to create for a line (`new_product`). */
public data class NewProduct(
    val name: String,
    val packageQuantity: java.math.BigDecimal?,
    val packageUnit: String?,
)

/**
 * The product named after the line, with its package size when unambiguous. Only piece lines
 * get one: a weighed line's quantity is already in kg or l.
 */
public fun newProduct(description: String, merchantProductCode: String?, unit: Unit?): NewProduct {
    val name = description.pyStrip().take(FieldSize.PRODUCT_NAME).ifEmpty {
        "Item ${merchantProductCode ?: "?"}"
    }
    val packageSize = if (unit == Unit.PIECE) suggest(description) else null
    return NewProduct(name, packageSize?.quantity, packageSize?.unit)
}
