@file:Suppress("MatchingDeclarationName")

package io.eksoda.engine.classification

import io.eksoda.engine.canonical.Line
import io.eksoda.engine.text.normalizeText
import io.eksoda.engine.text.pySplit
import java.math.BigDecimal

// Line classification into four types.
//
// The first matching rule wins:
//
// 1. A negative amount: `discount` when the description names a discount, otherwise `other`
//    (returns, adjustments).
// 2. Keywords on the normalized description: discount, then fee keywords. A keyword matches the
//    start of a word; a two-word keyword matches two consecutive words (`ΠΕΡΙΒΑΛΛΟΝΤΙΚ ΤΕΛ`
//    matches `ΠΕΡΙΒΑΛΛΟΝΤΙΚΟ ΤΕΛΟΣ`).
// 3. A zero net amount that carries only fees or other taxes: `fee`.
// 4. Everything else: `product`.
//
// The keyword lists are plain constants. A new keyword is a code change with a test.

public enum class LineType(public val code: String) {
    PRODUCT("product"),
    DISCOUNT("discount"),
    FEE("fee"),
    OTHER("other"),
}

public val DISCOUNT_KEYWORDS: List<String> = listOf("ΕΚΠΤΩΣ", "ΚΟΥΠΟΝ", "DISCOUNT")

/**
 * Carrier-bag environmental fee and deposit. Never the bare word "ΣΑΚΟΥΛ": real LIDL receipts
 * sell bags and garbage bags as products.
 */
public val FEE_KEYWORDS: List<String> = listOf("ΠΕΡΙΒΑΛΛΟΝΤΙΚ ΤΕΛ", "ΕΓΓΥΗΣ")

// Compared as normalizeText() keys, like the descriptions.
private val DISCOUNT_KEYS = DISCOUNT_KEYWORDS.map(::normalizeText)
private val FEE_KEYS = FEE_KEYWORDS.map(::normalizeText)

private fun has(normalizedDescription: String, keys: List<String>): Boolean {
    val words = normalizedDescription.pySplit()
    return keys.any { key ->
        val parts = key.pySplit()
        (0..words.size - parts.size).any { start ->
            parts.withIndex().all { (offset, part) -> words[start + offset].startsWith(part) }
        }
    }
}

/** The type of [line]. Pass [normalizedDescription] when it is already computed. */
public fun classify(line: Line, normalizedDescription: String? = null): LineType {
    val text = normalizedDescription ?: normalizeText(line.description)
    val discount = has(text, DISCOUNT_KEYS)
    return when {
        line.gross.signum() < 0 -> if (discount) LineType.DISCOUNT else LineType.OTHER
        discount -> LineType.DISCOUNT
        has(text, FEE_KEYS) -> LineType.FEE
        isZeroNetCharge(line) -> LineType.FEE
        else -> LineType.PRODUCT
    }
}

private fun isZeroNetCharge(line: Line): Boolean {
    val charges = (line.fees ?: BigDecimal.ZERO) + (line.otherTaxes ?: BigDecimal.ZERO)
    val net = line.net
    return net != null && net.signum() == 0 && charges.signum() > 0
}
