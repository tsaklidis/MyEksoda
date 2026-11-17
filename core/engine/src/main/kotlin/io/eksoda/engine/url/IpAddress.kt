// Port of the parts of CPython 3.13's ipaddress module that URL validation and urlsplit use.
package io.eksoda.engine.url

// `ipaddress.ip_address(text)` only decides whether a string is an IP literal here, so these
// functions answer that question with the same rules and never resolve anything.

private const val IPV4_OCTETS = 4
private const val MAX_OCTET = 255
private const val MAX_OCTET_DIGITS = 3
private const val IPV6_HEXTETS = 8
private const val MIN_IPV6_PARTS = 3
private const val MAX_HEXTET_DIGITS = 4
private const val HEX_DIGITS = "0123456789abcdefABCDEF"

internal enum class IpVersion { V4, V6 }

/** The IP version of [text] when `ip_address(text)` accepts it, otherwise null. */
internal fun ipVersion(text: String): IpVersion? = when {
    isIpv4(text) -> IpVersion.V4
    isIpv6(text) -> IpVersion.V6
    else -> null
}

/** `IPv4Address(text)`: four decimal octets, 0–255, no leading zeros. */
internal fun isIpv4(text: String): Boolean {
    if (text.isEmpty()) return false
    val octets = text.split('.')
    if (octets.size != IPV4_OCTETS) return false
    return octets.all { octet ->
        octet.isNotEmpty() &&
            octet.all { it in '0'..'9' } &&
            octet.length <= MAX_OCTET_DIGITS &&
            !(octet != "0" && octet[0] == '0') &&
            octet.toInt() <= MAX_OCTET
    }
}

/** `IPv6Address(text)`, including an optional `%scope` suffix (Python 3.9+). */
@Suppress("ReturnCount") // one return per check, like _ip_int_from_string
internal fun isIpv6(text: String): Boolean {
    if (text.isEmpty()) return false
    var address = text
    if ('%' in address) {
        val scope = address.substringAfter('%')
        if (scope.isEmpty() || '%' in scope) return false
        address = address.substringBefore('%')
    }
    val parts = address.split(':').toMutableList()
    if (parts.size < MIN_IPV6_PARTS) return false
    if ('.' in parts.last()) {
        if (!isIpv4(parts.removeAt(parts.lastIndex))) return false
        parts += "0" // the IPv4 tail stands for two hextets
        parts += "0"
    }
    if (parts.size > IPV6_HEXTETS + 1) return false
    val layout = hextetLayout(parts) ?: return false
    val (hi, lo) = layout
    val hextets = parts.take(hi) + parts.takeLast(lo)
    return hextets.all { hextet ->
        hextet.isNotEmpty() && hextet.length <= MAX_HEXTET_DIGITS &&
            hextet.all { it in HEX_DIGITS }
    }
}

/** `_ip_int_from_string`'s checks of the `::` position: (hextets before, hextets after), or null. */
@Suppress("ReturnCount") // one return per check, like _ip_int_from_string
private fun hextetLayout(parts: List<String>): Pair<Int, Int>? {
    var skipIndex: Int? = null
    for (index in 1 until parts.size - 1) {
        if (parts[index].isEmpty()) {
            if (skipIndex != null) return null // more than one "::"
            skipIndex = index
        }
    }
    if (skipIndex == null) {
        val complete =
            parts.size == IPV6_HEXTETS && parts.first().isNotEmpty() && parts.last().isNotEmpty()
        return if (complete) parts.size to 0 else null
    }
    var hi = skipIndex
    var lo = parts.size - skipIndex - 1
    if (parts.first().isEmpty()) {
        hi -= 1
        if (hi != 0) return null // a leading ":" must be "::"
    }
    if (parts.last().isEmpty()) {
        lo -= 1
        if (lo != 0) return null // a trailing ":" must be "::"
    }
    return if (IPV6_HEXTETS - (hi + lo) < 1) null else hi to lo
}
