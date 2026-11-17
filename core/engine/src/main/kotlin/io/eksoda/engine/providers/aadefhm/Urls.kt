package io.eksoda.engine.providers.aadefhm

import io.eksoda.engine.providers.UrlIdentity
import io.eksoda.engine.text.pyRegex
import io.eksoda.engine.text.pyStrip
import io.eksoda.engine.url.ParsedUrl
import io.eksoda.engine.url.parseQs

// AADE cash-register (ΦΗΜ) receipt check URL recognition.

public const val PROVIDER_CODE: String = "aade_fhm"
public const val HOST: String = "www1.aade.gr"
public const val PATH: String = "/tameiakes/myweb/q1.php"

/**
 * SIG = register id (3 letters + 8 digits) + receipt sequence (8 digits) + signature, date and
 * amount. Only the first two parts are needed: the page repeats the rest.
 */
private val SIG_RE =
    pyRegex("""^(?<register>[A-Z]{3}\d{8})(?<sequence>\d{8})(?<rest>[0-9A-Z]{10,}\.\d{2})$""")

public data class FhmLink(val sig: String, val register: String, val sequence: String) {
    val canonicalUrl: String get() = "https://$HOST$PATH?SIG=$sig"
    val sourceKey: String get() = "fhm:$register:$sequence"
}

public fun split(url: ParsedUrl): FhmLink? {
    if (url.host != HOST || url.path != PATH) return null
    val params = parseQs(url.query, keepBlankValues = true)
    val values = params["SIG"]
    if (params.keys != setOf("SIG") || values == null || values.size != 1) return null
    val sig = values[0].pyStrip()
    val match = SIG_RE.matchEntire(sig) ?: return null
    return FhmLink(
        sig = sig,
        register = checkNotNull(match.groups["register"]).value,
        sequence = checkNotNull(match.groups["sequence"]).value,
    )
}

public fun recognize(url: ParsedUrl): UrlIdentity? {
    val link = split(url) ?: return null
    return UrlIdentity(
        providerCode = PROVIDER_CODE,
        identity = link.sourceKey,
        canonicalUrl = link.canonicalUrl,
    )
}
