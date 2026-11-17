package io.eksoda.core.data.repository

import io.eksoda.core.model.CategorySummary
import io.eksoda.core.model.MerchantSummary
import io.eksoda.core.model.Store
import kotlinx.coroutines.flow.Flow

/**
 * Categories and merchants. Refused writes fail with a
 * [io.eksoda.core.model.CatalogException].
 */
interface CatalogRepository {
    fun getCategories(): Flow<List<CategorySummary>>

    /** The new category's id. */
    suspend fun createCategory(name: String): Result<String>

    /** Household categories only; system categories are read-only. */
    suspend fun renameCategory(id: String, name: String): Result<Unit>

    /** Household categories only, and only while no product uses them. */
    suspend fun deleteCategory(id: String): Result<Unit>

    /** The most frequent merchants first. */
    fun getMerchants(): Flow<List<MerchantSummary>>

    fun getMerchant(id: String): Flow<MerchantSummary?>

    fun getStores(merchantId: String): Flow<List<Store>>

    /** A blank name goes back to the name derived from the legal name. */
    suspend fun renameMerchant(id: String, name: String): Result<Unit>

    suspend fun setDefaultCategory(merchantId: String, categoryId: String?): Result<Unit>
}
