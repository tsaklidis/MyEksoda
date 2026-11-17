package io.eksoda.engine.providers.epsilon

import io.eksoda.engine.providers.UrlIdentity
import io.eksoda.engine.url.ParsedUrl
import java.util.Locale

// Epsilon Digital (Epsilon Net) receipt URL recognition.
//
// Every merchant has its own host, e.g. epsilondigital-sklavenitis.epsilonnet.gr. The receipt's
// QR code links to /fd/{document id}:{number}, which redirects to the viewer
// /DocViewer/{document UUID}. The viewer is behind a Cloudflare Turnstile check, so the app
// reads the receipt's detailed myDATA XML instead (fetchDocument in Sanitize.kt).

public const val PROVIDER_CODE: String = "epsilon"
internal const val UUID = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
private val UUID_GROUPS = listOf(8, 4, 4, 4, 12)

/**
 * A merchant host. The myDATA XML carries the issuer's VAT number but not its name, so each
 * host lists its issuer; a receipt from another VAT number on that host is refused.
 */
public data class Tenant(val host: String, val vatNumber: String, val legalName: String)

public val TENANTS: List<Tenant> = listOf(
    Tenant(
        host = "epsilondigital-sklavenitis.epsilonnet.gr",
        vatNumber = "800764388",
        legalName = "ΕΛΛΗΝΙΚΕΣ ΥΠΕΡΑΓΟΡΕΣ ΣΚΛΑΒΕΝΙΤΗΣ ΑΝΩΝΥΜΗ ΕΜΠΟΡΙΚΗ ΕΤΑΙΡΕΙΑ",
    ),
)

public val HOSTS: Set<String> = TENANTS.map { it.host }.toSet()

public fun tenant(host: String): Tenant? = TENANTS.firstOrNull { it.host == host }

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
    if (url.host !in HOSTS || url.query.isNotEmpty()) return null
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
