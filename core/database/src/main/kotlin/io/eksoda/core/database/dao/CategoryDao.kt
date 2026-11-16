package io.eksoda.core.database.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import io.eksoda.core.database.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow

/** A category with the number of products in it. */
data class CategoryRow(
    @Embedded val category: CategoryEntity,
    @ColumnInfo(name = "product_count") val productCount: Int,
)

@Dao
interface CategoryDao {
    @Query(
        """SELECT category.*, (SELECT COUNT(*) FROM product WHERE product.category_id = category.id)
           AS product_count FROM category ORDER BY category.name""",
    )
    fun observeRows(): Flow<List<CategoryRow>>

    @Query("SELECT * FROM category WHERE id = :id")
    suspend fun get(id: String): CategoryEntity?

    @Query("SELECT * FROM category")
    suspend fun all(): List<CategoryEntity>

    /** Names are unique by slug among every category, the system ones included. */
    @Query("SELECT EXISTS (SELECT 1 FROM category WHERE slug = :slug AND id != :exceptId)")
    suspend fun slugTaken(slug: String, exceptId: String): Boolean

    @Query("SELECT EXISTS (SELECT 1 FROM product WHERE category_id = :id)")
    suspend fun inUse(id: String): Boolean

    @Insert
    suspend fun insert(category: CategoryEntity)

    @Update
    suspend fun update(category: CategoryEntity)

    @Query("DELETE FROM category WHERE id = :id AND is_system = 0")
    suspend fun delete(id: String): Int
}
