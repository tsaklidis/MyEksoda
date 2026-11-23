package io.eksoda.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A bill paid every month, kept apart from receipts (schema 8): rent, electricity, internet.
 * [kind] is `fixed` (the same [amountMinor] every month, changeable for one month) or `variable`
 * (entered each month; [amountMinor] is only "usually about", or null). It holds from
 * [startMonth] to [endMonth] ("YYYY-MM"; null: still paid), and [position] orders the list.
 */
@Entity(tableName = "bill")
data class BillEntity(
    @PrimaryKey val id: String,
    val name: String,
    val kind: String,
    @ColumnInfo(name = "amount_minor") val amountMinor: Long?,
    @ColumnInfo(name = "start_month") val startMonth: String,
    @ColumnInfo(name = "end_month") val endMonth: String?,
    val position: Int,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
) {
    companion object {
        const val FIXED = "fixed"
        const val VARIABLE = "variable"
    }
}

/**
 * A bill's amount for one month ("YYYY-MM"; id "<bill id>:YYYY-MM"): a variable bill's entered
 * amount, a fixed bill's when it differs from its default that month, or a past month's kept
 * before the default changed. Deleting the bill deletes its months.
 */
@Entity(
    tableName = "bill_month",
    indices = [Index(value = ["bill_id", "month"], unique = true)],
    foreignKeys = [
        ForeignKey(
            entity = BillEntity::class,
            parentColumns = ["id"],
            childColumns = ["bill_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class BillMonthEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "bill_id") val billId: String,
    val month: String,
    @ColumnInfo(name = "amount_minor") val amountMinor: Long?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
