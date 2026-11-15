package io.eksoda.engine.text

import java.util.regex.Pattern

// Python 3.13 string behavior the ported code depends on (docs/android-standalone.md §9.3).
// Each helper names the Python construct it reproduces.

// Character.isWhitespace leaves out these four, which Python's str.isspace() includes.
private const val NEXT_LINE = 0x85
private const val NO_BREAK_SPACE = 0xA0
private const val FIGURE_SPACE = 0x2007
private const val NARROW_NO_BREAK_SPACE = 0x202F
private const val FIRST_NON_ASCII = 0x80

/** `str.isspace()` for one code point: categories Zs, Zl, Zp and bidi classes WS, B, S. */
internal fun isPySpace(codePoint: Int): Boolean = when (codePoint) {
    NEXT_LINE, NO_BREAK_SPACE, FIGURE_SPACE, NARROW_NO_BREAK_SPACE -> true
    else -> Character.isWhitespace(codePoint)
}

/** `str.split()` with no argument: split on runs of whitespace, drop empty parts. */
internal fun String.pySplit(): List<String> {
    val parts = mutableListOf<String>()
    val current = StringBuilder()
    codePoints().forEach { codePoint ->
        if (!isPySpace(codePoint)) {
            current.appendCodePoint(codePoint)
        } else if (current.isNotEmpty()) {
            parts += current.toString()
            current.setLength(0)
        }
    }
    if (current.isNotEmpty()) parts += current.toString()
    return parts
}

/** `str.strip()` with no argument. */
internal fun String.pyStrip(): String = pyLstrip().pyRstrip()

/** `str.lstrip()` with no argument. */
internal fun String.pyLstrip(): String {
    var index = 0
    while (index < length && isPySpace(codePointAt(index))) {
        index += Character.charCount(codePointAt(index))
    }
    return substring(index)
}

/** `str.rstrip()` with no argument. */
internal fun String.pyRstrip(): String {
    var end = length
    while (end > 0 && isPySpace(codePointBefore(end))) {
        end -= Character.charCount(codePointBefore(end))
    }
    return substring(0, end)
}

/** `len(str)`: Python counts code points, not UTF-16 units. */
internal fun String.pyLen(): Int = codePointCount(0, length)

/** `str.isdigit()` on a non-empty string, limited to decimal digits (Nd). */
internal fun String.pyIsDigit(): Boolean = isNotEmpty() &&
    codePoints().allMatch { Character.getType(it) == Character.DECIMAL_DIGIT_NUMBER.toInt() }

/** `str.isascii()`. */
internal fun String.pyIsAscii(): Boolean = all { it.code < FIRST_NON_ASCII }

/** `str.isalpha()` on a non-empty string: every code point is a letter (L*). */
internal fun String.pyIsAlpha(): Boolean =
    isNotEmpty() && codePoints().allMatch { Character.isLetter(it) }

/** Compiles a pattern with Python's Unicode-aware `\d`, `\w` and `\s` (§9.3). */
internal fun pyRegex(pattern: String): Regex =
    Pattern.compile(pattern, Pattern.UNICODE_CHARACTER_CLASS).toRegex()
