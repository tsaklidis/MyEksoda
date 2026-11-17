package io.eksoda.engine.fetch

import io.eksoda.engine.FormatChanged
import io.eksoda.engine.url.ParsedUrl
import java.nio.charset.Charset
import java.nio.charset.IllegalCharsetNameException
import java.nio.charset.UnsupportedCharsetException

public const val USER_AGENT: String = "EksodaReceiptImporter/1.0"
private const val TWO_MIB: Long = 2L * 1024 * 1024

public data class FetchLimits(
    val connectTimeoutMillis: Long = 5_000,
    val readTimeoutMillis: Long = 15_000,
    val totalTimeoutMillis: Long = 30_000,
    val maxBytes: Long = TWO_MIB,
)

/** A fetched HTML page. */
public class FetchedDocument(
    public val body: ByteArray,
    public val status: Int = 200,
    public val charset: String = "utf-8",
) {
    /** The body decoded with [charset]; undecodable bytes become U+FFFD (like errors="replace"). */
    public fun text(): String {
        val encoding = charset.ifEmpty { "utf-8" }
        val decoder = try {
            Charset.forName(encoding)
        } catch (_: IllegalCharsetNameException) {
            throw FormatChanged("unknown charset '$encoding'")
        } catch (_: UnsupportedCharsetException) {
            throw FormatChanged("unknown charset '$encoding'")
        }
        return String(body, decoder)
    }
}

/** The content types of receipt pages: the default for [Fetcher.get]. */
public val HTML_TYPES: Set<String> = setOf("text/html")

/** The content types of myDATA XML documents (sources that offer the XML instead of a page). */
public val XML_TYPES: Set<String> = setOf("application/xml", "text/xml")

/**
 * Fetches receipt pages. [SafeFetcher] is the real one; tests use a fake that serves the
 * receipt fixtures.
 */
public interface Fetcher {
    /**
     * Fetches an HTML page (or another document in [contentTypes], lowercase MIME types).
     * Statuses in [acceptStatuses] are returned, not raised, so a provider can interpret them
     * (e.g. a provider that answers 418 for missing documents).
     */
    public suspend fun get(
        url: ParsedUrl,
        allowedHosts: Set<String>,
        acceptStatuses: Set<Int> = emptySet(),
        contentTypes: Set<String> = HTML_TYPES,
    ): FetchedDocument
}
