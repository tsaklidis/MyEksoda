package io.eksoda.core.model

import java.time.Instant
import java.time.LocalDate

/** What a backup file holds, shown before it replaces the data. */
public data class BackupSummary(
    /** When the backup was made. */
    val createdAt: Instant,
    val householdName: String?,
    val receipts: Int,
    val products: Int,
    val merchants: Int,
    /** The oldest and newest receipt dates, null when there are no receipts. */
    val firstIssued: LocalDate?,
    val lastIssued: LocalDate?,
)
