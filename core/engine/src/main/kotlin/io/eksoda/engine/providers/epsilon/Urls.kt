package io.eksoda.engine.providers.epsilon

import io.eksoda.engine.providers.UrlIdentity
import io.eksoda.engine.url.ParsedUrl
import java.util.Locale

// Epsilon Digital (Epsilon Net) receipt URL recognition.
//
// Some merchants have their own host (see [HOSTS]); others, even a big chain, share one. New
// hosts keep appearing, so any epsilondigital-<name>.epsilonnet.gr host is accepted
// ([HOST_PATTERN]) and its receipt fetched. A host that serves no receipts fails at the fetch
// like any other unreadable link: a 404 or a response that is not XML at once, a host that does
// not resolve after the usual retries (DNS cannot tell a host that does not exist from a
// resolver outage, so it is never refused for that alone).
// The receipt's QR code links to /fd/{document id}:{number}, which redirects to the
// viewer /DocViewer/{document UUID}. The viewer is behind a Cloudflare Turnstile check, so the
// app reads the receipt's detailed myDATA XML instead (fetchDocument in Sanitize.kt).

public const val PROVIDER_CODE: String = "epsilon"
internal const val UUID = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
private val UUID_GROUPS = listOf(8, 4, 4, 4, 12)

/**
 * Every Epsilon Digital host: one label `epsilondigital-<name>` right under epsilonnet.gr. The
 * name is letters, digits and inner hyphens, 1 to 48 characters: DNS allows 63 for the whole
 * label, and `epsilondigital-` takes 15. Hosts arrive lowercased (parseSubmittedUrl).
 */
public val HOST_PATTERN: Regex =
    Regex("""^epsilondigital-[a-z0-9](?:[a-z0-9-]{0,46}[a-z0-9])?\.epsilonnet\.gr$""")

/**
 * Hosts seen serving receipts: the provider's listed hosts, found by the registry before the
 * pattern. A link on any other [HOST_PATTERN] host is read the same way; this list is the record
 * of where receipts were observed. A host of one merchant never names the issuer in its XML
 * (see [ISSUER_NAMES]); a shared host usually does, though not always (Masoutis on 3rdpartb).
 */
public val HOSTS: Set<String> = setOf(
    // One merchant each: Sklavenitis (2026-11-17), AB Vasilopoulos and Market In (2026-11-26).
    "epsilondigital-sklavenitis.epsilonnet.gr",
    "epsilondigital-abmarket.epsilonnet.gr",
    "epsilondigital-marketin.epsilonnet.gr",
    // Shared: a fuel station (2026-11-20), Masoutis supermarkets (2026-11-24).
    "epsilondigital-3rd.epsilonnet.gr",
    "epsilondigital-3rdpartb.epsilonnet.gr",
)

/**
 * Legal names of issuers whose XML carries only their VAT number, by that VAT number. A VAT
 * number names one company, so the entry holds on every Epsilon host. An issuer without a name in
 * its XML and without an entry here is read with an empty name (the app shows its VAT number
 * until a receipt names it); an entry gives it a proper name from the first receipt. Only a
 * verified legal name goes here: Market In (998771189, on its own host) has none yet, since a
 * wrong name would be worse than the VAT number the user can rename.
 */
public val ISSUER_NAMES: Map<String, String> = mapOf(
    "800764388" to "ΕΛΛΗΝΙΚΕΣ ΥΠΕΡΑΓΟΡΕΣ ΣΚΛΑΒΕΝΙΤΗΣ ΑΝΩΝΥΜΗ ΕΜΠΟΡΙΚΗ ΕΤΑΙΡΕΙΑ",
    "094247924" to "ΔΙΑΜΑΝΤΗΣ ΜΑΣΟΥΤΗΣ Α.Ε. ΣΟΥΠΕΡ ΜΑΡΚΕΤ",
    "094025817" to "ΑΛΦΑ ΒΗΤΑ ΒΑΣΙΛΟΠΟΥΛΟΣ ΜΟΝΟΠΡΟΣΩΠΗ Α.Ε.",
)

/** /fd/{32 hex digits}:{number} (the QR code; the number is optional). */
private val SHORT_PATH = Regex("""^/fd/([0-9a-f]{32})(?::(\d{1,9}))?/?$""", RegexOption.IGNORE_CASE)

/** /DocViewer/{UUID} (the address after the redirect, e.g. a link shared from a browser). */
private val VIEWER_PATH = Regex("""^/DocViewer/($UUID)/?$""", RegexOption.IGNORE_CASE)

public data class EpsilonLink(
    val host: String,
    /** Lowercase UUID. */
    val documentId: String,
    /** The receipt link: a credential. Never log it. */
    val canonicalUrl: String,
) {
    /** The detailed myDATA XML of the receipt: lines with descriptions, totals, payments. */
    val documentUrl: String get() = "https://$host/filedocument/GetInvoiceDocDetailed/$documentId"
}

/** 32 hex digits → the dashed UUID form. */
internal fun hexToUuid(hex: String): String {
    var start = 0
    return UUID_GROUPS.joinToString("-") { size ->
        hex.substring(start, start + size).also { start += size }
    }
}

public fun split(url: ParsedUrl): EpsilonLink? {
    if (!HOST_PATTERN.matches(url.host) || url.query.isNotEmpty()) return null
    val short = SHORT_PATH.matchEntire(url.path)
    val viewer = VIEWER_PATH.matchEntire(url.path)
    return when {
        short != null -> {
            val hex = short.groupValues[1].lowercase(Locale.ROOT)
            val number = short.groupValues[2]
            val suffix = if (number.isEmpty()) "" else ":$number"
            EpsilonLink(url.host, hexToUuid(hex), "https://${url.host}/fd/$hex$suffix")
        }

        viewer != null -> {
            val id = viewer.groupValues[1].lowercase(Locale.ROOT)
            EpsilonLink(url.host, id, "https://${url.host}/DocViewer/$id")
        }

        else -> null
    }
}

public fun recognize(url: ParsedUrl): UrlIdentity? {
    val link = split(url) ?: return null
    return UrlIdentity(
        providerCode = PROVIDER_CODE,
        identity = "epsilon:${link.documentId}",
        canonicalUrl = link.canonicalUrl,
    )
}
