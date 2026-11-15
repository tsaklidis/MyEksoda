// Port of backend/apps/receipts/providers/impact/urls.py.
package io.eksoda.engine.providers.impact

import io.eksoda.engine.providers.UrlIdentity
import io.eksoda.engine.url.ParsedUrl
import java.util.Locale

// Impact receipt URL recognition (docs/providers/impact.md §1).

public const val PROVIDER_CODE: String = "impact"
public const val HOST: String = "einvoice.impact.gr"

/** /p/{country prefix + 9-digit VAT}/{40-hex UID}/{32-hex access GUID} */
private val PATH_RE =
    Regex("""^/p/([A-Z]{2})(\d{9})/([0-9A-F]{40})/([0-9A-F]{32})/?$""", RegexOption.IGNORE_CASE)

/** VAT prefixes (EU VIES style) → ISO country codes. */
public val COUNTRY_BY_PREFIX: Map<String, String> = mapOf("EL" to "GR")

public fun recognize(url: ParsedUrl): UrlIdentity? {
    if (url.host != HOST || url.query.isNotEmpty()) return null
    val match = PATH_RE.matchEntire(url.path) ?: return null
    val groups = match.groupValues
    val prefix = groups[1].uppercase(Locale.ROOT)
    val vat = groups[2]
    val uid = groups[3].uppercase(Locale.ROOT)
    val guid = groups[4].uppercase(Locale.ROOT)
    val country = COUNTRY_BY_PREFIX[prefix] ?: return null
    return UrlIdentity(
        providerCode = PROVIDER_CODE,
        identity = "$country:$vat:$uid",
        canonicalUrl = "https://$HOST/p/$prefix$vat/$uid/$guid",
        issuerCountry = country,
        issuerVat = vat,
        uid = uid,
    )
}
