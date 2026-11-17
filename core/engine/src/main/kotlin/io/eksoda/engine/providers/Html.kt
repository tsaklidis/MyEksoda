package io.eksoda.engine.providers

import io.eksoda.engine.FormatChanged
import io.eksoda.engine.text.Culture
import io.eksoda.engine.text.NumberFormatError
import io.eksoda.engine.text.collapseWhitespace
import io.eksoda.engine.text.normalizeText
import io.eksoda.engine.text.parseOptionalDecimal
import io.eksoda.engine.text.pyStrip
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import org.jsoup.select.NodeTraversor
import org.jsoup.select.NodeVisitor
import java.math.BigDecimal

// HTML helpers shared by provider parsers.

private val CURRENCY_NOISE = Regex("""(?i)\s*(?:€|EUR|ευρώ|ευρω)\s*""")

/**
 * `collapse_whitespace(element.get_text(" ", strip=True))`. Jsoup's `text()` joins text around
 * block elements differently, so this walks the text nodes as BeautifulSoup does: strip each,
 * drop empty ones, join with " ". Script and style contents are data nodes in Jsoup and
 * are skipped, like BeautifulSoup's Script and Stylesheet strings.
 */
public fun textOf(element: Element?): String {
    if (element == null) return ""
    val parts = mutableListOf<String>()
    NodeTraversor.traverse(
        NodeVisitor { node, _ ->
            val text = (node as? TextNode)?.wholeText?.pyStrip()
            if (!text.isNullOrEmpty()) parts += text
        },
        element,
    )
    return collapseWhitespace(parts.joinToString(" "))
}

/** Comparison key for labels/headers: normalized text without a trailing colon. */
public fun keyOf(label: String): String = normalizeText(label.pyStrip().trimEnd(':'))

/** `row.find_all(name, recursive=False)`. */
public fun childCells(row: Element, name: String = "div"): List<Element> =
    row.children().filter { it.tagName() == name }

/** A number that may carry a currency marker ("75,48EUR", "€ 24.87"). */
public fun amount(text: String?, culture: Culture, where: String): BigDecimal? {
    if (text == null) return null
    return try {
        parseOptionalDecimal(CURRENCY_NOISE.replace(text, ""), culture)
    } catch (exception: NumberFormatError) {
        throw FormatChanged("$where: ${exception.message}").apply { initCause(exception) }
    }
}

public fun required(mapping: Map<String, String>, key: String, where: String): String {
    val value = mapping[key].orEmpty().pyStrip()
    if (value.isEmpty()) throw FormatChanged("missing required field $where")
    return value
}

/**
 * Splits (label, value) pairs into (known key → value, unknown label → value). [known] maps
 * human-written labels to keys; labels are compared with [keyOf]. The first value of a key wins.
 */
public fun labelMap(
    pairs: Iterable<Pair<String, String>>,
    known: Map<String, String>,
): Pair<Map<String, String>, Map<String, String>> {
    val index = known.entries.associate { (label, key) -> keyOf(label) to key }
    val values = LinkedHashMap<String, String>()
    val unknown = LinkedHashMap<String, String>()
    for ((label, value) in pairs) {
        val key = index[keyOf(label)]
        when (key) {
            null -> unknown[collapseWhitespace(label)] = value
            else -> values.putIfAbsent(key, value)
        }
    }
    return values to unknown
}
