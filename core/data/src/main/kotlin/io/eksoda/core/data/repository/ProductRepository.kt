package io.eksoda.core.data.repository

import androidx.paging.PagingData
import io.eksoda.core.model.PriceComparison
import io.eksoda.core.model.PriceHistory
import io.eksoda.core.model.Product
import io.eksoda.core.model.ProductDraft
import io.eksoda.core.model.ProductFilter
import kotlinx.coroutines.flow.Flow

/**
 * Products, price history and comparison (`android-app.md` §6.6). Writes fail with a
 * [io.eksoda.core.model.CatalogException] when the server would answer 400, 404 or 409.
 */
interface ProductRepository {
    fun getProducts(filter: ProductFilter): Flow<PagingData<Product>>

    /** Null once the product is gone (merged into another, for example). */
    fun getProduct(id: String): Flow<Product?>

    fun getPriceHistory(id: String): Flow<PriceHistory>

    /** Prices per merchant in [unit], or in the unit with the most entries when null. */
    fun getComparison(id: String, unit: String?): Flow<PriceComparison>

    /** The new product's id. */
    suspend fun createProduct(draft: ProductDraft): Result<String>

    /** A package size change recalculates the price per unit of every purchase. */
    suspend fun updateProduct(id: String, draft: ProductDraft): Result<Unit>

    /** The sources' history moves to [targetId], then they are deleted (at most 50). */
    suspend fun mergeProducts(targetId: String, sourceIds: Set<String>): Result<Unit>
}
