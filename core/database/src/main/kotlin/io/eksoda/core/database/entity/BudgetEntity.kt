package io.eksoda.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A monthly budget (schema 5): the everyday budget ([TOTAL_ID], `category_id` null) or a
 * category's limit (the category's id). Since schema 8 a scope has a default row ([month] null)
 * and may have a row per month ("YYYY-MM", id "<scope>:YYYY-MM"), which wins over the default in
 * that month; its [amountMinor] is null for "none that month". Past months get their own row
 * before a default changes, so they keep what they had (core:engine `MonthAmounts.kt`). Deleting
 * the category deletes its rows.
 */
@Entity(
    tableName = "budget",
    indices = [Index("category_id"), Index(value = ["category_id", "month"], unique = true)],
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class BudgetEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "category_id") val categoryId: String?,
    @ColumnInfo(name = "amount_minor") val amountMinor: Long?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    val month: String? = null,
) {
    companion object {
        /** The id of the whole month's budget. */
        const val TOTAL_ID = "total"
    }
}
