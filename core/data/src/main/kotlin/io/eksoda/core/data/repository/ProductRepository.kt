package io.eksoda.core.data.repository

import androidx.paging.PagingData
import io.eksoda.core.model.PriceComparison
import io.eksoda.core.model.PriceEntry
import io.eksoda.core.model.Product
import io.eksoda.core.model.ReviewItem
import kotlinx.coroutines.flow.Flow

/** Products, price history and the review queue (L5). */
interface ProductRepository {
    fun getProducts(query: String? = null, categoryId: String? = null): Flow<PagingData<Product>>

    suspend fun getProduct(id: String): Result<Product>

    suspend fun getProductPrices(id: String, unit: String? = null): Result<List<PriceEntry>>

    suspend fun getPriceComparison(id: String, unit: String? = null): Result<PriceComparison>

    fun getReviewItems(): Flow<PagingData<ReviewItem>>

    suspend fun matchItem(itemId: String, productId: String): Result<Unit>

    suspend fun createProductFromItem(
        itemId: String,
        name: String,
        categoryId: String?,
    ): Result<Unit>
}
