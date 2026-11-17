package io.eksoda.feature.scan

import java.net.URI

/**
 * Light checks before a link is submitted. The engine decides whether a link is a receipt;
 * these only keep obvious non-links away from it. Links are never logged.
 */
internal object ReceiptLinks {
    private val LINK = Regex("""https?://\S+""", RegexOption.IGNORE_CASE)

    /** A scanned QR code is passed on only when it is an https URL. */
    fun isHttpsLink(text: String): Boolean {
        val trimmed = text.trim()
        return trimmed.startsWith("https://", ignoreCase = true) && trimmed.none(Char::isWhitespace)
    }

    /** The first link in shared or pasted text ("Look at this https://…"). */
    fun extract(text: String): String? = LINK.find(text)?.value?.trimEnd('.', ',', ')', '"', '\'')

    /** The host, to name the company's website without showing the link itself. */
    fun host(link: String): String? = runCatching { URI(link).host }.getOrNull()
}
