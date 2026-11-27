package io.eksoda.engine.providers.parochos

import io.eksoda.engine.providers.UrlIdentity
import io.eksoda.engine.providers.epsilon.EpsilonLink
import io.eksoda.engine.providers.epsilon.splitLink
import io.eksoda.engine.url.ParsedUrl

// Parochos (Parochos IT SA) receipt URL recognition.
//
// Parochos runs Epsilon Digital's platform under its own hosts (srv*.parochos.gr, observed
// 2026-11-27 with a pharmacy's receipt): the same /fd/{32 hex}:{number} QR link redirecting to
// /DocViewer/{UUID}, the same detailed myDATA XML. The viewer is a Blazor app that draws the
// receipt over a websocket, so the app reads the XML (fetchDocument in Sanitize.kt), like
// Epsilon's. Neither id in the link is the myDATA UID.

public const val PROVIDER_CODE: String = "parochos"

/**
 * The Parochos hosts. srv2 served the first receipt; srv and srv3 run the same viewer and XML
 * endpoint (checked with an unknown document id, 2026-11-27).
 */
public val HOSTS: Set<String> = setOf("srv.parochos.gr", "srv2.parochos.gr", "srv3.parochos.gr")

/**
 * Legal names of issuers whose XML carries only their VAT number, by that VAT number (the XML
 * has no name; the receipt's UBL document does). Only a verified legal name goes here.
 */
public val ISSUER_NAMES: Map<String, String> = mapOf(
    // A pharmacy in Kalamaria (2026-11-27), the registration name of its UBL document.
    "081258114" to "ΣΥΣΤΕΓΑΣΜΕΝΑ ΦΑΡΜΑΚΕΙΑ ΠΑΠΑΔΟΠΟΥΛΟΥ ΡΟΔΗ-ΚΑΙΑΦΑΣ ΠΑΝΑΓΙΩΤΗΣ Ο.Ε.",
)

public fun split(url: ParsedUrl): EpsilonLink? = splitLink(url) { it in HOSTS }

public fun recognize(url: ParsedUrl): UrlIdentity? {
    val link = split(url) ?: return null
    return UrlIdentity(
        providerCode = PROVIDER_CODE,
        identity = "parochos:${link.documentId}",
        canonicalUrl = link.canonicalUrl,
    )
}
