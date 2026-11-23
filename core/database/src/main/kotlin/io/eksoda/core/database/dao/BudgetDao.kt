package io.eksoda.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import io.eksoda.core.database.entity.BudgetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budget")
    fun observeAll(): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budget")
    suspend fun all(): List<BudgetEntity>

    @Query("SELECT * FROM budget WHERE id = :id")
    suspend fun get(id: String): BudgetEntity?

    /** One scope's rows, the default and the months ([categoryId] null: the everyday budget). */
    @Query("SELECT * FROM budget WHERE category_id IS :categoryId")
    suspend fun scope(categoryId: String?): List<BudgetEntity>

    @Upsert
    suspend fun upsert(budget: BudgetEntity)

    @Insert
    suspend fun insertAll(budgets: List<BudgetEntity>)

    @Query("DELETE FROM budget WHERE id = :id")
    suspend fun delete(id: String): Int

    /** The month ("YYYY-MM") of the oldest receipt, or null without receipts. */
    @Query("SELECT substr(MIN(issued_local_date), 1, 7) FROM receipt")
    suspend fun firstReceiptMonth(): String?
}
