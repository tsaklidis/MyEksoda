package io.eksoda.core.ui.text

import androidx.annotation.StringRes
import io.eksoda.core.ui.R

/**
 * The local string for an engine error code (docs/android-standalone.md §5.4). The engine's
 * English `publicMessage` is never shown. Unknown codes read as an internal error.
 */
@StringRes
fun importErrorRes(code: String?): Int = when (code) {
    "invalid_url" -> R.string.import_error_invalid_url
    "unsupported_provider" -> R.string.import_error_unsupported_provider
    "unsupported_url_format" -> R.string.import_error_unsupported_url_format
    "provider_unreachable" -> R.string.import_error_provider_unreachable
    "provider_timeout" -> R.string.import_error_provider_timeout
    "provider_error" -> R.string.import_error_provider_error
    "receipt_not_found" -> R.string.import_error_receipt_not_found
    "blocked_destination" -> R.string.import_error_blocked_destination
    "response_too_large" -> R.string.import_error_response_too_large
    "unexpected_content_type" -> R.string.import_error_unexpected_content_type
    "provider_format_changed" -> R.string.import_error_provider_format_changed
    "inconsistent_source" -> R.string.import_error_inconsistent_source
    else -> R.string.import_error_internal_error
}
