package io.eksoda.engine.providers.parochos

import io.eksoda.engine.fetch.FetchedDocument
import io.eksoda.engine.fetch.Fetcher
import io.eksoda.engine.providers.Provider
import io.eksoda.engine.providers.SanitizeResult
import io.eksoda.engine.providers.UrlIdentity
import io.eksoda.engine.providers.epsilon.fetchXml
import io.eksoda.engine.providers.epsilon.sanitizeXml as sanitizeEpsilonXml

// Removes personal data from a saved Parochos XML: Epsilon Digital's XML, so Epsilon's
// sanitizer (its link patterns do not depend on the host).

public fun sanitizeXml(rawXml: String): SanitizeResult = sanitizeEpsilonXml(rawXml)

/** One request: the detailed XML from the link's own Parochos host. */
public suspend fun fetchDocument(identity: UrlIdentity, fetcher: Fetcher): FetchedDocument =
    fetchXml(identity, fetcher, ::split)

/** Parochos e-receipt hosts (srv*.parochos.gr), serving Epsilon Digital's platform. */
public val PAROCHOS: Provider = Provider(
    code = PROVIDER_CODE,
    hosts = HOSTS,
    layouts = LAYOUTS,
    recognize = ::recognize,
    parseHtml = ::parseDocument,
    sanitize = ::sanitizeXml,
    fetchDocument = ::fetchDocument,
)
