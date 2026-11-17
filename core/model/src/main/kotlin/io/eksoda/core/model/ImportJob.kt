package io.eksoda.core.model

import java.time.Instant

public data class ImportJob(
    val id: String,
    val status: ImportStatus,
    val outcome: ImportOutcome?,
    val providerCode: String,
    /** Host of the receipt page. The receipt URL itself is a credential and never shown. */
    val sourceHost: String,
    val createdAt: Instant,
    val finishedAt: Instant?,
    val receiptId: String?,
    /** The engine's error code, mapped to a local string in the UI. */
    val errorCode: String?,
    /** When a retrying job tries again; null when it is not waiting. */
    val nextAttemptAt: Instant?,
    /** The receipt's merchant and total, once imported. */
    val merchantName: String?,
    val total: Money?,
)

public enum class ImportStatus(public val code: String) {
    PENDING("pending"),
    COMPLETED("completed"),
    FAILED("failed"),
}

public enum class ImportOutcome(public val code: String) {
    CREATED("created"),
    DUPLICATE("duplicate"),
    RECEIPT_DELETED("receipt_deleted"),
}

/** The result of submitting a receipt link. */
public sealed interface ImportSubmission {
    /** A new job, or a failed or deleted import started again. [offline]: it imports when online. */
    public data class Queued(val jobId: String, val offline: Boolean = false) : ImportSubmission

    /** The receipt is already stored. */
    public data class AlreadyImported(val receiptId: String) : ImportSubmission

    /** The link is already waiting to be imported. */
    public data class AlreadyPending(val jobId: String) : ImportSubmission

    /** Not a receipt link Eksoda can read; [code] is the engine's error code (invalid_url, …). */
    public data class Rejected(val code: String) : ImportSubmission
}
