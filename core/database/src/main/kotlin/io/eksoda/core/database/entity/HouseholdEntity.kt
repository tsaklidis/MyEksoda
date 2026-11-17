package io.eksoda.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * The one household of this database file.
 * Timestamps are UTC epoch milliseconds.
 */
@Entity(tableName = "household")
data class HouseholdEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "default_currency") val defaultCurrency: String,
    val timezone: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
