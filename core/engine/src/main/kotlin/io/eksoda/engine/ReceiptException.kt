// Port of backend/apps/receipts/errors.py.
package io.eksoda.engine

// Receipt import errors (requirements §6.4, TD §9.4).
//
// Every error has a stable `code` (shown to users), a `publicMessage` (safe to show), a retry
// policy, and an internal `detail` for logs. Details must never contain full receipt URLs. The
// app maps codes to local strings and never shows `publicMessage` (docs/android-standalone.md
// §5.4); it is kept for parity and logs.

public enum class ErrorCode(public val code: String) {
    INVALID_URL("invalid_url"),
    UNSUPPORTED_PROVIDER("unsupported_provider"),
    UNSUPPORTED_URL_FORMAT("unsupported_url_format"),
    PROVIDER_UNREACHABLE("provider_unreachable"),
    PROVIDER_TIMEOUT("provider_timeout"),
    PROVIDER_ERROR("provider_error"),
    RECEIPT_NOT_FOUND("receipt_not_found"),
    BLOCKED_DESTINATION("blocked_destination"),
    RESPONSE_TOO_LARGE("response_too_large"),
    UNEXPECTED_CONTENT_TYPE("unexpected_content_type"),
    PROVIDER_FORMAT_CHANGED("provider_format_changed"),
    INCONSISTENT_SOURCE("inconsistent_source"),
    INTERNAL_ERROR("internal_error"),
    ;

    override fun toString(): String = code
}

public enum class RetryPolicy(public val code: String) {
    /** Network or provider trouble: retried. */
    TRANSIENT("transient"),

    /** Retried too: a receipt can appear at the provider after a delay. */
    NOT_FOUND("not_found"),
    INTERNAL("internal"),
    NONE("none"),
    ;

    override fun toString(): String = code
}

/** `ReceiptError`. Subclasses fix [code], [retry] and [publicMessage], as the Python classes do. */
public sealed class ReceiptException(
    public val code: ErrorCode,
    public val retry: RetryPolicy,
    public val publicMessage: String,
    public val detail: String,
) : Exception(if (detail.isNotEmpty()) "$code: $detail" else code.toString())

// --- Submission errors: rejected at once, never retried ---------------------------------------

public class InvalidUrl(detail: String = "") :
    ReceiptException(
        ErrorCode.INVALID_URL,
        RetryPolicy.NONE,
        "The receipt link is not a valid https URL.",
        detail,
    )

public class UnsupportedProvider(detail: String = "") :
    ReceiptException(
        ErrorCode.UNSUPPORTED_PROVIDER,
        RetryPolicy.NONE,
        "Receipts from this provider are not supported yet.",
        detail,
    )

public class UnsupportedUrlFormat(detail: String = "") :
    ReceiptException(
        ErrorCode.UNSUPPORTED_URL_FORMAT,
        RetryPolicy.NONE,
        "This receipt link has an unexpected format.",
        detail,
    )

// --- Fetch and parse errors -------------------------------------------------------------------

public class ProviderUnreachable(detail: String = "") :
    ReceiptException(
        ErrorCode.PROVIDER_UNREACHABLE,
        RetryPolicy.TRANSIENT,
        "The receipt provider could not be reached.",
        detail,
    )

public class ProviderTimeout(detail: String = "") :
    ReceiptException(
        ErrorCode.PROVIDER_TIMEOUT,
        RetryPolicy.TRANSIENT,
        "The receipt provider did not respond in time.",
        detail,
    )

/** `ProviderHttpError`. */
public class ProviderHttpError(detail: String = "") :
    ReceiptException(
        ErrorCode.PROVIDER_ERROR,
        RetryPolicy.TRANSIENT,
        "The receipt provider returned an error.",
        detail,
    )

public class BlockedDestination(detail: String = "") :
    ReceiptException(
        ErrorCode.BLOCKED_DESTINATION,
        RetryPolicy.NONE,
        "The receipt address is not allowed.",
        detail,
    )

public class ResponseTooLarge(detail: String = "") :
    ReceiptException(
        ErrorCode.RESPONSE_TOO_LARGE,
        RetryPolicy.NONE,
        "The receipt page is too large.",
        detail,
    )

public class UnexpectedContentType(detail: String = "") :
    ReceiptException(
        ErrorCode.UNEXPECTED_CONTENT_TYPE,
        RetryPolicy.NONE,
        "The receipt provider returned an unexpected kind of response.",
        detail,
    )

public class ReceiptNotFound(detail: String = "") :
    ReceiptException(
        ErrorCode.RECEIPT_NOT_FOUND,
        RetryPolicy.NOT_FOUND,
        "The receipt is not available from the provider (yet).",
        detail,
    )

/** `FormatChanged`. */
public class FormatChanged(detail: String = "") :
    ReceiptException(
        ErrorCode.PROVIDER_FORMAT_CHANGED,
        RetryPolicy.NONE,
        "The receipt page format is not recognized.",
        detail,
    )

public class InconsistentSource(detail: String = "") :
    ReceiptException(
        ErrorCode.INCONSISTENT_SOURCE,
        RetryPolicy.NONE,
        "The receipt page contains inconsistent data.",
        detail,
    )

public class InternalError(detail: String = "") :
    ReceiptException(
        ErrorCode.INTERNAL_ERROR,
        RetryPolicy.INTERNAL,
        "An internal error occurred while importing the receipt.",
        detail,
    )
