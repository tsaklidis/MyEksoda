// Port of backend/apps/products/packages.py.
package io.eksoda.engine.products

import io.eksoda.engine.text.pyDivide
import io.eksoda.engine.text.pyRegex
import io.eksoda.engine.text.stripDiacritics
import java.math.BigDecimal
import java.text.Normalizer
import java.util.Locale

// Package-size suggestions from item descriptions (requirements §9.4).
//
// A suggestion is applied automatically only when it is unambiguous: exactly one size token, and
// no multipack (`6X330ML`) or slash pack (`27/250ML`). Percentages (`1,5%`) are never read as
// sizes. Otherwise the size stays empty until a person sets it.
//
// Receipts mix Greek and Latin letters that look alike (`1ΚG`), so unit letters accept both
// scripts.

private const val NUMBER = """(\d+(?:\.\d+)?)"""

/** Unit spellings, tried in this order. Letter classes accept Latin and Greek forms. */
private val UNITS: List<Pair<String, String>> = listOf(
    "kg" to "[KΚ][GΓ][RΡ]?|[KΚ][IΙ][LΛ][OΟAΑ]?",
    "mg" to "[MΜ][GΓ][RΡ]?",
    "g" to "[GΓ][RΡ]?",
    "ml" to "[MΜ][LΛ]",
    "cl" to "C[LΛ]",
    "l" to "[LΛ][IΙ][TΤ](?:[RΡ][OΟAΑ])?|[LΛ][TΤ][RΡ]?|[LΛ]",
)
private val UNIT = UNITS.joinToString("|") { (unit, pattern) -> "(?<$unit>$pattern)" }

/**
 * The number starts a token: not inside another number ("1.5" → not "5"), but it may follow an
 * abbreviation dot ("ΦΥ.Ζ.195G").
 */
public val SIZE_TOKEN: Regex =
    pyRegex("""(?<![\d/])(?<!\d[.,])$NUMBER\s?(?:$UNIT)(?![A-ZΑ-Ω0-9])""")

/** 6X330ML, 2 X 1L, 5+1X330ML */
public val MULTIPACK: Regex = pyRegex("""\d\s?[XΧ×]\s?\d""")

/** 27/250ML */
public val SLASH_PACK: Regex = pyRegex("""\d\s?/\s?\d+(?:\.\d+)?\s?(?:$UNIT)""")
private val PERCENT = pyRegex("""\d+(?:\.\d+)?\s?%""")
private val DECIMAL_COMMA = pyRegex("""(?<=\d),(?=\d)""")
private val THOUSAND = BigDecimal(1000)

public data class PackageSuggestion(
    val quantity: BigDecimal,
    /** g | kg | ml | cl | l */
    val unit: String,
)

public fun suggest(description: String): PackageSuggestion? {
    val text = prepare(description)
    if (MULTIPACK.containsMatchIn(text) || SLASH_PACK.containsMatchIn(text)) return null
    val tokens = SIZE_TOKEN.findAll(PERCENT.replace(text, " ")).toList()
    val match = tokens.singleOrNull() ?: return null
    var quantity = BigDecimal(checkNotNull(match.groups[1]).value)
    var unit = UNITS.first { (name, _) -> match.groups[name] != null }.first
    if (unit == "mg") {
        quantity = quantity.pyDivide(THOUSAND)
        unit = "g"
    }
    if (quantity.signum() <= 0) return null
    return PackageSuggestion(quantity = quantity, unit = unit)
}

/**
 * Uppercase, accent-free, decimal commas as dots. Not `normalizeText`: it would drop the "/"
 * that marks a slash pack.
 */
private fun prepare(description: String): String {
    val text = stripDiacritics(
        Normalizer.normalize(description, Normalizer.Form.NFKC),
    ).uppercase(Locale.ROOT)
    return DECIMAL_COMMA.replace(text, ".")
}
