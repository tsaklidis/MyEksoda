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
    /** The engine's error code, mapped to a local string in the UI (§5.4). */
    val errorCode: String?,
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
