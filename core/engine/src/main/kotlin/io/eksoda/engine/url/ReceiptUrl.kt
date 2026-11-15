// Port of backend/apps/receipts/links.py.
package io.eksoda.engine.url

import io.eksoda.engine.InvalidUrl
import io.eksoda.engine.text.isPySpace
import io.eksoda.engine.text.pyLen
import io.eksoda.engine.text.pyStrip
import java.net.IDN
import java.text.Normalizer
import java.util.Locale

// Validation of submitted receipt URLs (requirements §6.2, TD §9.3). The URL splitting follows
// CPython 3.13's urllib.parse.urlsplit and the host encoding its "idna" codec, so a link is
// accepted or refused exactly as the server would.

public const val MAX_URL_LENGTH: Int = 2048
private const val MAX_HOST_LENGTH = 253
private const val HTTPS_PORT = 443
private const val MAX_PORT = 65_535
private const val DELETE = 0x7F
private const val FIRST_PRINTABLE = 0x20
private val LABEL_RE = Regex("^(?!-)[a-z0-9-]{1,63}(?<!-)$")

/** A syntactically safe https URL: lowercase IDNA host, no userinfo/fragment/odd port. */
public data class ParsedUrl(
    val scheme: String,
    val host: String,
    val port: Int?,
    val path: String,
    val query: String,
) {
    /** The request target (path and query) sent to the server. */
    val target: String get() = if (query.isNotEmpty()) "$path?$query" else path

    /** `urlunsplit((scheme, netloc, path, query, ""))`. */
    public fun geturl(): String {
        val netloc = if (port == null) host else "$host:$port"
        val path = if (path.isNotEmpty() && !path.startsWith("/")) "/$path" else path
        return "$scheme://$netloc$path" + if (query.isNotEmpty()) "?$query" else ""
    }
}

/**
 * Validates a submitted receipt link. Throws [InvalidUrl] for anything that is not a plain
 * https URL on port 443. Like Python's `urlsplit`, a malformed bracketed host
 * (`https://[abc/`) throws [IllegalArgumentException] instead.
 */
public fun parseSubmittedUrl(raw: Any?): ParsedUrl {
    val text = checkedText(raw)
    val parts = urlsplit(text)
    val port = checkedPort(parts)
    var host = parts.hostname.orEmpty()
    if (host.isEmpty()) throw InvalidUrl("missing host")
    host = idnaEncode(host) ?: throw InvalidUrl("invalid host name")
    if (!validHost(host)) throw InvalidUrl("invalid host name")
    return ParsedUrl(
        scheme = "https",
        host = host,
        port = port,
        path = parts.path.ifEmpty {
            "/"
        },
        query = parts.query,
    )
}

/** The stripped link, if it is a string of an allowed length without whitespace or controls. */
private fun checkedText(raw: Any?): String {
    if (raw !is String) throw InvalidUrl("not a string")
    val text = raw.pyStrip()
    if (text.isEmpty() || text.pyLen() > MAX_URL_LENGTH) throw InvalidUrl("empty or too long")
    val unsafe = text.codePoints().anyMatch {
        isPySpace(it) || it < FIRST_PRINTABLE || it == DELETE
    }
    if (unsafe) throw InvalidUrl("contains whitespace or control characters")
    return text
}

/** The port (null or 443) after the scheme, userinfo and fragment checks. */
private fun checkedPort(parts: SplitResult): Int? {
    if (parts.scheme.lowercase(Locale.ROOT) != "https") throw InvalidUrl("scheme must be https")
    if (parts.username != null ||
        parts.password != null
    ) {
        throw InvalidUrl("userinfo is not allowed")
    }
    if (parts.fragment.isNotEmpty()) throw InvalidUrl("fragments are not allowed")
    val port = try {
        parts.port
    } catch (exception: IllegalArgumentException) {
        throw InvalidUrl("invalid port").apply { initCause(exception) }
    }
    if (port != null && port != HTTPS_PORT) throw InvalidUrl("only port 443 is allowed")
    return port
}

/** A DNS name made of valid labels, or an IP address literal. */
private fun validHost(host: String): Boolean {
    if (ipVersion(host) != null) return true
    return host.length <= MAX_HOST_LENGTH && host.split('.').all { LABEL_RE.matches(it) }
}

// --- urllib.parse.urlsplit (CPython 3.13) ------------------------------------------------------

private const val SCHEME_CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789+-."
private val VERSIONED_HOST = Regex("""v[a-fA-F0-9]+\..+""")

/** `SplitResult` with the properties links.py reads. */
internal data class SplitResult(
    val scheme: String,
    val netloc: String,
    val path: String,
    val query: String,
    val fragment: String,
) {
    private val userinfo: String? get() = if ('@' in
        netloc
    ) {
        netloc.substringBeforeLast('@')
    } else {
        null
    }

    val username: String? get() = userinfo?.substringBefore(':')

    val password: String? get() = userinfo?.let { if (':' in it) it.substringAfter(':') else null }

    /** `_hostinfo`: (hostname, port text) after any userinfo, brackets removed. */
    private val hostinfo: Pair<String, String?>
        get() {
            val info = netloc.substringAfterLast('@')
            val (hostname, port) = if ('[' in info) {
                val bracketed = info.substringAfter('[')
                bracketed.substringBefore(']') to
                    bracketed.substringAfter(']', "").substringAfter(':', "")
            } else {
                info.substringBefore(':') to info.substringAfter(':', "")
            }
            return hostname to port.ifEmpty { null }
        }

    /** Lowercase host without brackets, or null. A `%zone` suffix keeps its case. */
    val hostname: String?
        get() {
            val name = hostinfo.first
            if (name.isEmpty()) return null
            val zone = if ('%' in name) "%" + name.substringAfter('%') else ""
            return name.substringBefore('%').lowercase(Locale.ROOT) + zone
        }

    /** The port, or null. Throws [IllegalArgumentException] for a non-numeric or out-of-range port. */
    val port: Int?
        get() {
            val text = hostinfo.second ?: return null
            require(
                text.isNotEmpty() && text.all {
                    it in '0'..'9'
                },
            ) { "Port could not be cast to integer value" }
            val value = text.toBigInteger()
            require(value <= MAX_PORT.toBigInteger()) { "Port out of range 0-65535" }
            return value.toInt()
        }
}

internal fun urlsplit(input: String): SplitResult {
    // WHATWG: leading C0 controls and spaces go, and tabs and newlines anywhere.
    var url = input.trimStart { it.code <= FIRST_PRINTABLE }.filterNot {
        it == '\t' || it == '\r' ||
            it == '\n'
    }
    var scheme = ""
    val colon = url.indexOf(':')
    if (colon > 0 && isScheme(url.substring(0, colon))) {
        scheme = url.substring(0, colon).lowercase(Locale.ROOT)
        url = url.substring(colon + 1)
    }
    var netloc = ""
    if (url.startsWith("//")) {
        val delimiter =
            "/?#".map { url.indexOf(it, 2) }.filter { it >= 0 }.minOrNull() ?: url.length
        netloc = url.substring(2, delimiter)
        url = url.substring(delimiter)
        require(('[' in netloc) == (']' in netloc)) { "Invalid IPv6 URL" }
        if ('[' in netloc) checkBracketedNetloc(netloc)
    }
    var fragment = ""
    if ('#' in url) {
        fragment = url.substringAfter('#')
        url = url.substringBefore('#')
    }
    var query = ""
    if ('?' in url) {
        query = url.substringAfter('?')
        url = url.substringBefore('?')
    }
    checkNetloc(netloc)
    return SplitResult(scheme, netloc, url, query, fragment)
}

/** The text before the first ":" is a scheme: an ASCII letter, then scheme characters. */
private fun isScheme(text: String): Boolean =
    text[0].code < DELETE + 1 && text[0].isLetter() && text.all { it in SCHEME_CHARS }

/** `_checknetloc`: a non-ASCII netloc must not gain URL delimiters under NFKC. */
private fun checkNetloc(netloc: String) {
    if (netloc.isEmpty() || netloc.all { it.code < DELETE + 1 }) return
    val stripped = netloc.replace("@", "").replace(":", "").replace("#", "").replace("?", "")
    val normalized = Normalizer.normalize(stripped, Normalizer.Form.NFKC)
    if (stripped == normalized) return
    require(
        normalized.none {
            it in "/?#@:"
        },
    ) { "netloc contains invalid characters under NFKC normalization" }
}

/** `_check_bracketed_netloc` and `_check_bracketed_host`. */
private fun checkBracketedNetloc(netloc: String) {
    val hostAndPort = netloc.substringAfterLast('@')
    if ('[' !in hostAndPort) return
    require(hostAndPort.substringBefore('[').isEmpty()) { "Invalid IPv6 URL" }
    val bracketed = hostAndPort.substringAfter('[')
    val hostname = bracketed.substringBefore(']')
    val port = bracketed.substringAfter(']', "")
    require(port.isEmpty() || port.startsWith(":")) { "Invalid IPv6 URL" }
    if (hostname.startsWith("v")) {
        require(VERSIONED_HOST.matches(hostname)) { "IPvFuture address is invalid" }
    } else {
        require(ipVersion(hostname) == IpVersion.V6) { "An IPv4 address cannot be in brackets" }
    }
}

// --- The "idna" codec (IDNA 2003) ------------------------------------------------------------

private val IDNA_DOTS = Regex("[.。．｡]")
private const val MAX_LABEL_LENGTH = 63
private const val ACE_PREFIX = "xn--"

/** `host.encode("idna").decode("ascii")`, or null where Python raises UnicodeError. */
private fun idnaEncode(host: String): String? {
    if (host.all { it.code < DELETE + 1 }) {
        val labels = host.split('.')
        val fits =
            labels.dropLast(1).all { it.length in 1..MAX_LABEL_LENGTH } &&
                labels.last().length <= MAX_LABEL_LENGTH
        return if (fits) host else null
    }
    val labels = host.split(IDNA_DOTS).toMutableList()
    val trailingDot = if (labels.isNotEmpty() && labels.last().isEmpty()) {
        labels.removeAt(labels.lastIndex)
        "."
    } else {
        ""
    }
    val encoded = labels.map { toAscii(it) ?: return null }
    return encoded.joinToString(".") + trailingDot
}

/** `ToASCII(label)`: nameprep and punycode for a non-ASCII label (java.net.IDN implements RFC 3490). */
private fun toAscii(label: String): String? {
    if (label.all { it.code < DELETE + 1 }) return label.takeIf { it.length in 1..MAX_LABEL_LENGTH }
    if (label.take(ACE_PREFIX.length).lowercase(Locale.ROOT) == ACE_PREFIX) return null
    val ascii = try {
        IDN.toASCII(label)
    } catch (_: IllegalArgumentException) {
        return null
    }
    return ascii.takeIf { it.length in 1..MAX_LABEL_LENGTH }
}
