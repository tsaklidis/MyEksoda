@file:Suppress("MatchingDeclarationName")

package io.eksoda.engine.merchants

import io.eksoda.engine.canonical.Address
import io.eksoda.engine.text.normalizeText
import io.eksoda.engine.text.pySplit
import io.eksoda.engine.text.pyStrip
import java.time.Instant
import java.util.Locale

// Merchant and store rules. The database lookups (resolveMerchant, resolveStore) live in
// core:data.
//
// Public details are refreshed from receipts:
// - an empty field is filled from any receipt;
// - a filled field is replaced only by a receipt issued at the same time or later than the one
//   that last set the details, and never by a truncated copy of itself (some sources cut long
//   names).

/** Maximum field lengths, which values are cut to (`_fit`). */
public object FieldSize {
    public const val LEGAL_NAME: Int = 255
    public const val DISPLAY_NAME: Int = 120
    public const val TAX_OFFICE: Int = 120
    public const val GEMI_NUMBER: Int = 32
    public const val STREET: Int = 255
    public const val STREET_NUMBER: Int = 20
    public const val CITY: Int = 120
    public const val POSTAL_CODE: Int = 10
    public const val NORMALIZED_ADDRESS: Int = 400
    public const val PRODUCT_NAME: Int = 255
}

/**
 * Legal-form words that end the brand part of a legal name, compared on normalizeText() keys
 * with spaces removed ("Α.Ε." and "ΑΕ" give the same key).
 */
private val LEGAL_FORMS: Set<String> = listOf(
    "ΑΕ", "ΑΕΒΕ", "ΑΒΕΕ", "ΑΒΕ", "ΑΕΕ", "ΑΕΒΕΕ", "ΙΚΕ", "ΜΙΚΕ", "ΟΕ", "ΕΕ", "ΕΠΕ", "ΜΕΠΕ",
    // Spelled-out forms.
    "ΑΝΩΝΥΜΗ", "ΙΔΙΩΤΙΚΗ", "ΟΜΟΡΡΥΘΜΗ", "ΕΤΕΡΟΡΡΥΘΜΗ", "ΜΟΝΟΠΡΟΣΩΠΗ", "ΜΟΝΟΠΡΟΣΩΠΟΣ",
    // Cut short by cash-register pages.
    "ΜΟΝΟΠΡΟ",
    "SA", "S.A.", "LTD", "GMBH",
).map(::key).toSet()

/** "ΚΑΙ ΣΙΑ" / "& ΣΙΑ" (and partners) also ends the brand part. */
private val AND = normalizeText("ΚΑΙ")
private val PARTNERS = normalizeText("ΣΙΑ")

private fun key(word: String): String = normalizeText(word).pySplit().joinToString("")

/** lower() of the whole word keeps the Greek final sigma right. */
private fun title(word: String): String {
    val lowered = word.lowercase(Locale.ROOT)
    return lowered.take(1).uppercase(Locale.ROOT) + lowered.drop(1)
}

/** `value[:max_length].strip()`. */
public fun fit(value: String, maxLength: Int): String = value.take(maxLength).pyStrip()

/** The brand part of a legal name, title-cased: `METRO ΑΕΒΕ …` → `Metro`. */
public fun deriveDisplayName(legalName: String): String {
    val words = legalName.pySplit()
    var cut = words.size
    for (index in 1 until words.size) {
        val word = words[index]
        val following = if (index + 1 < words.size) key(words[index + 1]) else ""
        val partners = (word == "&" || key(word) == AND) && following == PARTNERS
        if (key(word) in LEGAL_FORMS || partners) {
            cut = index
            break
        }
    }
    val brand = words.take(cut).toMutableList()
    // Drop a trailing "&", "-", ",".
    while (brand.isNotEmpty() && key(brand.last()).isEmpty()) brand.removeAt(brand.lastIndex)
    val name = brand.joinToString(" ") { title(it) }.ifEmpty { legalName.pyStrip() }
    return fit(name, FieldSize.DISPLAY_NAME)
}

/** The store matching key: street, number, postal code and city (country omitted). */
public fun normalizeAddress(address: Address?): String {
    if (address == null) return ""
    return addressKey(address.street, address.number, address.postalCode, address.city)
}

public fun addressKey(vararg parts: String?): String = fit(
    normalizeText(
        parts.filterNot {
            it.isNullOrEmpty()
        }.joinToString(" "),
    ),
    FieldSize.NORMALIZED_ADDRESS,
)

/**
 * `_refresh`: which of [details] to write over [current], given when the current details were
 * set and when the new receipt was issued. Returns the fields to change and whether
 * `details_as_of` moves to [asOf].
 */
public fun refreshedDetails(
    current: Map<String, String>,
    currentAsOf: Instant?,
    details: Map<String, String>,
    asOf: Instant,
): Pair<Map<String, String>, Boolean> {
    val newer = currentAsOf == null || asOf >= currentAsOf
    val changed = LinkedHashMap<String, String>()
    for ((field, value) in details) {
        val existing = current[field].orEmpty()
        if (value.isEmpty() || value == existing) continue
        if (existing.isEmpty() || (newer && !existing.startsWith(value))) changed[field] = value
    }
    return changed to (newer && currentAsOf != asOf)
}
