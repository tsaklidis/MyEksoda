package io.eksoda.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import io.eksoda.core.database.entity.BillEntity
import io.eksoda.core.database.entity.BillMonthEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BillDao {
    @Query("SELECT * FROM bill ORDER BY position, created_at")
    fun observeBills(): Flow<List<BillEntity>>

    @Query("SELECT * FROM bill_month")
    fun observeMonths(): Flow<List<BillMonthEntity>>

    @Query("SELECT * FROM bill WHERE id = :id")
    suspend fun get(id: String): BillEntity?

    @Query("SELECT * FROM bill_month WHERE bill_id = :billId")
    suspend fun months(billId: String): List<BillMonthEntity>

    @Query("SELECT COALESCE(MAX(position) + 1, 0) FROM bill")
    suspend fun nextPosition(): Int

    @Upsert
    suspend fun upsert(bill: BillEntity)

    @Upsert
    suspend fun upsertMonth(month: BillMonthEntity)

    @Insert
    suspend fun insertMonths(months: List<BillMonthEntity>)

    @Query("DELETE FROM bill WHERE id = :id")
    suspend fun delete(id: String): Int

    @Query("DELETE FROM bill_month WHERE id = :id")
    suspend fun deleteMonth(id: String): Int

    /** The ids of [billId]'s months from [month] ("YYYY-MM") on: they go when the bill ends. */
    @Query("SELECT id FROM bill_month WHERE bill_id = :billId AND month >= :month")
    suspend fun monthIdsFrom(billId: String, month: String): List<String>
}
