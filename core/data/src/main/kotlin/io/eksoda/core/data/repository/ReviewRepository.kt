package io.eksoda.core.data.repository

import androidx.paging.PagingData
import io.eksoda.core.model.LineType
import io.eksoda.core.model.ProductDraft
import io.eksoda.core.model.ReviewItem
import kotlinx.coroutines.flow.Flow

/** The review queue and line corrections. */
interface ReviewRepository {
    /** Product lines waiting for a person, newest receipt first. */
    fun getReviewItems(): Flow<PagingData<ReviewItem>>

    fun getReviewCount(): Flow<Int>

    /** Links the line; future receipts from the shop match the same item automatically. */
    suspend fun linkItem(itemId: String, productId: String): Result<Unit>

    suspend fun createProductForItem(itemId: String, draft: ProductDraft): Result<Unit>

    /** Back to [LineType.PRODUCT], the line is matched again. */
    suspend fun reclassifyItem(itemId: String, lineType: LineType): Result<Unit>

    suspend fun setItemNote(itemId: String, note: String): Result<Unit>
}
