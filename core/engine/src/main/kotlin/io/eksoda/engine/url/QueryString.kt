// Port of urllib.parse.parse_qs (CPython 3.13) as the providers use it.
package io.eksoda.engine.url

import java.net.URLDecoder

/**
 * `parse_qs(query, keep_blank_values=keepBlankValues)`: name → values in order. Fields are split
 * on "&", "+" means a space and %-escapes are decoded as UTF-8 (malformed escapes are kept as
 * written; Python replaces undecodable bytes).
 */
public fun parseQs(query: String, keepBlankValues: Boolean = false): Map<String, List<String>> {
    val result = LinkedHashMap<String, MutableList<String>>()
    for (field in query.split('&').filter { it.isNotEmpty() }) {
        val value = if ('=' in field) field.substringAfter('=') else ""
        // Without keep_blank_values, fields with no value (or no "=") are dropped.
        if (value.isNotEmpty() || keepBlankValues) {
            result.getOrPut(unquotePlus(field.substringBefore('='))) { mutableListOf() } +=
                unquotePlus(value)
        }
    }
    return result
}

private fun unquotePlus(text: String): String = try {
    URLDecoder.decode(text, Charsets.UTF_8)
} catch (_: IllegalArgumentException) {
    text.replace('+', ' ')
}
