package io.eksoda.engine

import io.eksoda.engine.canonical.CanonicalReceipt
import io.eksoda.engine.fetch.Fetcher
import io.eksoda.engine.providers.Provider
import io.eksoda.engine.providers.ProviderRegistry
import io.eksoda.engine.providers.REGISTRY
import io.eksoda.engine.providers.UrlIdentity
import io.eksoda.engine.validation.ValidationIssue
import io.eksoda.engine.validation.validate

/** A receipt link that a provider recognized, before anything is fetched. */
public data class ResolvedLink(val provider: Provider, val identity: UrlIdentity)

/** A parsed receipt and its validation warnings. */
public data class ParsedReceipt(val receipt: CanonicalReceipt, val issues: List<ValidationIssue>)

/**
 * The engine's entry point: everything an import does before persisting (resolve, fetch, parse,
 * validate).
 */
public class Engine(
    private val fetcher: Fetcher,
    private val registry: ProviderRegistry = REGISTRY,
) {
    /** Validates and identifies a link. Throws [InvalidUrl], [UnsupportedProvider] or [UnsupportedUrlFormat]. */
    public fun resolve(rawUrl: String): ResolvedLink {
        val (provider, identity) = registry.resolveUrl(rawUrl)
        return ResolvedLink(provider, identity)
    }

    /** Fetch + parse + validate. Throws [ReceiptException]. Does no I/O besides the fetch. */
    public suspend fun read(link: ResolvedLink): ParsedReceipt {
        val document = link.provider.fetch(link.identity, fetcher)
        val receipt = link.provider.parse(document, link.identity)
        return ParsedReceipt(receipt, validate(receipt))
    }
}
