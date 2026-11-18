package io.eksoda.core.data.repository

import androidx.paging.PagingData
import io.eksoda.core.model.Product
import io.eksoda.core.model.ProductDraft
import io.eksoda.core.model.ProductFilter
import io.eksoda.core.model.ProductPrices
import kotlinx.coroutines.flow.Flow

/**
 * Products, price history and comparison. Refused writes (invalid, missing or conflicting) fail
 * with a [io.eksoda.core.model.CatalogException].
 */
interface ProductRepository {
    fun getProducts(filter: ProductFilter): Flow<PagingData<Product>>

    /** Null once the product is gone (merged into another, for example). */
    fun getProduct(id: String): Flow<Product?>

    /**
     * The price history, and prices per merchant in the unit selected by [unit] (the unit with
     * the most entries while it is null). One price query feeds both, and a unit change only
     * recomputes the comparison.
     */
    fun getPrices(id: String, unit: Flow<String?>): Flow<ProductPrices>

    /** The new product's id. */
    suspend fun createProduct(draft: ProductDraft): Result<String>

    /** A package size change recalculates the price per unit of every purchase. */
    suspend fun updateProduct(id: String, draft: ProductDraft): Result<Unit>

    /** The sources' history moves to [targetId], then they are deleted (at most 50). */
    suspend fun mergeProducts(targetId: String, sourceIds: Set<String>): Result<Unit>
}
