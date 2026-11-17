package io.eksoda.engine.text

import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale

// Text normalization for matching and search keys.
//
// Normalized text is a *key*: it never replaces the original receipt text.

// Capital letters that look identical in the Latin and Greek alphabets.
private const val LATIN_LOOKALIKES = "ABEHIKMNOPTXYZ"
private const val GREEK_LOOKALIKES = "ΑΒΕΗΙΚΜΝΟΡΤΧΥΖ"
private val TO_GREEK: Map<Char, Char> = LATIN_LOOKALIKES.zip(GREEK_LOOKALIKES).toMap()
private val TO_LATIN: Map<Char, Char> = GREEK_LOOKALIKES.zip(LATIN_LOOKALIKES).toMap()
private val AMBIGUOUS: Set<Char> = (LATIN_LOOKALIKES + GREEK_LOOKALIKES).toSet()
private val GREEK_ONLY: Set<Char> = "ΑΒΓΔΕΖΗΘΙΚΛΜΝΞΟΠΡΣΤΥΦΧΨΩ".toSet() - AMBIGUOUS
private val LATIN_ONLY: Set<Char> = ('A'..'Z').toSet() - AMBIGUOUS

// Dots not between digits, and decimal commas, with a Unicode-aware \d.
private val LOOSE_DOT = pyRegex("""(?<!\d)\.|\.(?!\d)""")
private val DECIMAL_COMMA = pyRegex("""(?<=\d),(?=\d)""")

/**
 * Removes combining marks: NFD, drop non-spacing marks (Mn), NFC. For Greek and Latin text these
 * are all the marks.
 */
public fun stripDiacritics(text: String): String {
    val decomposed = Normalizer.normalize(text, Normalizer.Form.NFD)
    val kept = StringBuilder(decomposed.length)
    decomposed.codePoints().forEach { codePoint ->
        if (Character.getType(codePoint) != Character.NON_SPACING_MARK.toInt()) {
            kept.appendCodePoint(codePoint)
        }
    }
    return Normalizer.normalize(kept, Normalizer.Form.NFC)
}

/**
 * Writes look-alike letters in the token's dominant script.
 *
 * Tokens made only of look-alike letters (e.g. `KAKAO` vs `ΚΑΚΑΟ`) are written in Latin so both
 * spellings produce the same key.
 */
private fun unifyScript(token: String): String {
    val greek = token.count { it in GREEK_ONLY }
    val latin = token.count { it in LATIN_ONLY }
    val table = if (greek > latin) TO_GREEK else TO_LATIN
    return buildString(token.length) { token.forEach { append(table[it] ?: it) } }
}

/** Uppercase, accent-free, script-unified, punctuation-collapsed matching key. */
public fun normalizeText(text: String): String {
    var value = Normalizer.normalize(text, Normalizer.Form.NFKC)
    // uppercase() also turns the final sigma into Σ.
    value = stripDiacritics(value).uppercase(Locale.ROOT)
    value = DECIMAL_COMMA.replace(value, ".")
    value = replaceNotKept(value)
    value = LOOSE_DOT.replace(value, " ")
    return value.pySplit().joinToString(" ") { unifyScript(it) }
}

/**
 * Every run of characters that are not letters or digits of any script, `.` or `%` (an `_`
 * included) becomes one space. Java's Unicode `\w` also keeps marks and connector punctuation,
 * so the class is spelled out here.
 */
private fun replaceNotKept(value: String): String {
    val result = StringBuilder(value.length)
    var inRun = false
    value.codePoints().forEach { codePoint ->
        val kept = codePoint == '.'.code || codePoint == '%'.code || isPyAlnum(codePoint)
        if (kept) {
            result.appendCodePoint(codePoint)
            inRun = false
        } else if (!inRun) {
            result.append(' ')
            inRun = true
        }
    }
    return result.toString()
}

/** `str.isalnum()` for one code point: letters (L*) and numbers (Nd, Nl, No). */
private fun isPyAlnum(codePoint: Int): Boolean = when (Character.getType(codePoint).toByte()) {
    Character.UPPERCASE_LETTER, Character.LOWERCASE_LETTER, Character.TITLECASE_LETTER,
    Character.MODIFIER_LETTER, Character.OTHER_LETTER,
    Character.DECIMAL_DIGIT_NUMBER, Character.LETTER_NUMBER, Character.OTHER_NUMBER,
    -> true

    else -> false
}

public fun collapseWhitespace(text: String): String = text.pySplit().joinToString(" ")

/** SHA-256 hex digest, used to refer to sensitive values (e.g. receipt URLs) in logs. */
public fun fingerprint(text: String): String = MessageDigest.getInstance("SHA-256")
    .digest(text.toByteArray(Charsets.UTF_8))
    .joinToString("") { "%02x".format(Locale.ROOT, it) }
