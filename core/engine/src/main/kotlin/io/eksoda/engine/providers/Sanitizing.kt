package io.eksoda.engine.providers

import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

// Shared machinery for turning saved receipt pages into personal-data-free pages for bug
// reports.
//
// A provider sanitizer replaces secret values *where they occur*: the text of the specific
// elements that hold them (buyer data, payment ids, signatures; any length), specific attributes
// (download tokens), and link-shaped occurrences matched by a provider pattern (access tokens
// inside receipt URLs). Nothing is replaced by a blind search over the whole page, so unrelated
// content (item codes, amounts, inline images) is never touched. [finish] then verifies that no
// recorded original survives anywhere; if one does, [SanitizeError].

/**
 * Originals shorter than this are replaced structurally but not searched for during
 * verification: a 3-letter value would also match unrelated words.
 */
private const val MIN_VERIFY_LENGTH = 4

public class SanitizeError(message: String) : Exception(message)

public data class SanitizeResult(
    val html: String,
    /** category → number of distinct values replaced */
    val replaced: Map<String, Int>,
)

/** Same shape: digits → 0, letters → [letter], other characters kept. */
public fun fakeLike(value: String, letter: Char = 'X'): String = buildString {
    value.codePoints().forEach { codePoint ->
        when {
            Character.isDigit(codePoint) -> append('0')
            Character.isLetter(codePoint) -> append(letter)
            else -> appendCodePoint(codePoint)
        }
    }
}

public class Secrets {
    /** original → category (for counts and verify) */
    private val originals = LinkedHashMap<String, String>()
    private val patterns = mutableListOf<Triple<Regex, (String) -> String, String>>()

    public fun record(original: String, category: String) {
        if (original.isNotEmpty()) originals.putIfAbsent(original, category)
    }

    /** Replaces an element's whole text with a same-shape fake (any length). */
    public fun replaceElement(element: Element?, category: String) {
        if (element == null) return
        val original = textOf(element)
        val fake = fakeLike(original)
        // Empty, or already a fake (re-sanitizing).
        if (original.isEmpty() || fake == original) return
        element.text(fake)
        record(original, category)
    }

    public fun replaceAttribute(
        element: Element,
        attribute: String,
        category: String,
        fake: String = "",
    ) {
        val original = element.attr(attribute).trim()
        if (original.isNotEmpty() && original != fake) {
            element.attr(attribute, fake)
            record(original, category)
        }
    }

    /** Replaces group 1 of every match of [pattern] (applied in [finish]). */
    public fun replacePattern(pattern: Regex, fakeFor: (String) -> String, category: String) {
        patterns += Triple(pattern, fakeFor, category)
    }

    internal fun applyPatterns(input: String): String = patterns.fold(input) {
            output,
            (pattern, fakeFor, category),
        ->
        pattern.replace(output) { match ->
            val group = checkNotNull(match.groups[1])
            val fake = fakeFor(group.value)
            if (fake == group.value) {
                match.value
            } else {
                record(group.value, category)
                val start = group.range.first - match.range.first
                val end = group.range.last + 1 - match.range.first
                match.value.substring(0, start) + fake + match.value.substring(end)
            }
        }
    }

    internal fun items(): Map<String, String> = originals

    public fun counts(): Map<String, Int> = originals.values.groupingBy { it }.eachCount()
}

/** Removes scripts, styles, external links and hidden form values. */
public fun stripActiveContent(document: Document) {
    document.select("script, style, noscript, link").remove()
    document.select("input[type=\"hidden\"]").attr("value", "")
}

public fun finish(document: Document, secrets: Secrets): SanitizeResult {
    val output = secrets.applyPatterns(document.outerHtml())
    verify(output, secrets)
    return SanitizeResult(html = output, replaced = secrets.counts())
}

public fun verify(output: String, secrets: Secrets) {
    val check = parseHtml(output)
    val attributes = check.allElements.joinToString(" ") { element ->
        element.attributes().joinToString(" ") { it.value }
    }
    val haystack = listOf(output, check.wholeText(), attributes).joinToString("\n")
    val leaked = secrets.items()
        .filter { (original, _) -> original.length >= MIN_VERIFY_LENGTH && original in haystack }
        .values.toSortedSet()
    if (leaked.isNotEmpty()) {
        throw SanitizeError("values still present after sanitizing: ${leaked.joinToString(", ")}")
    }
}
