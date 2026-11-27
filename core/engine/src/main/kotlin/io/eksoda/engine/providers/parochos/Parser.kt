package io.eksoda.engine.providers.parochos

import io.eksoda.engine.canonical.CanonicalReceipt
import io.eksoda.engine.providers.Layout
import io.eksoda.engine.providers.UrlIdentity
import io.eksoda.engine.providers.epsilon.EpsilonLayoutV1
import io.eksoda.engine.providers.epsilon.Site
import io.eksoda.engine.providers.epsilon.parseXmlDocument

// Parochos detailed myDATA XML → CanonicalReceipt: Epsilon Digital's XML, read by Epsilon's
// layouts under this provider's code, parser version, hosts and issuer names. The XML prints
// the receipt's /fd/ link, which must be on a Parochos host and name the same document.

public const val PARSER_VERSION: String = "parochos/1"

private val SITE = Site(PROVIDER_CODE, PARSER_VERSION, ::split, ISSUER_NAMES)

/** Newest first. */
public val LAYOUTS: List<Layout> = listOf(EpsilonLayoutV1(SITE))

public fun parseDocument(text: String, identity: UrlIdentity): CanonicalReceipt =
    parseXmlDocument(text, identity, LAYOUTS)
