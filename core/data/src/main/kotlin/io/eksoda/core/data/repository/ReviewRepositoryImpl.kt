package io.eksoda.core.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import io.eksoda.core.data.db.WriteCoordinator
import io.eksoda.core.data.products.ReviewService
import io.eksoda.core.data.products.fail
import io.eksoda.core.data.util.Fixed
import io.eksoda.core.database.dao.ProductDao
import io.eksoda.core.database.dao.ReviewRow
import io.eksoda.core.database.entity.ReceiptItemEntity
import io.eksoda.core.model.CatalogError
import io.eksoda.core.model.LineType
import io.eksoda.core.model.Money
import io.eksoda.core.model.ProductDraft
import io.eksoda.core.model.ReviewItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.OffsetDateTime
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReviewRepositoryImpl @Inject constructor(
    private val products: ProductDao,
    private val review: ReviewService,
    private val coordinator: WriteCoordinator,
    private val householdZone: HouseholdZone,
) : ReviewRepository {

    override fun getReviewItems(): Flow<PagingData<ReviewItem>> =
        Pager(PagingConfig(pageSize = PAGE_SIZE)) { products.pagingReview() }.flow
            .map { page ->
                val zone = householdZone.current()
                page.map { row ->
                    row.toModel(OffsetDateTime.ofInstant(Instant.ofEpochMilli(row.issuedAt), zone))
                }
            }

    override fun getReviewCount(): Flow<Int> = products.observeReviewCount()

    override suspend fun linkItem(itemId: String, productId: String): Result<Unit> = runCatching {
        coordinator.write {
            val product = products.get(productId) ?: fail(CatalogError.NOT_FOUND)
            review.linkItem(item(itemId), product)
        }
    }

    override suspend fun createProductForItem(itemId: String, draft: ProductDraft): Result<Unit> =
        runCatching { coordinator.write { review.createProductForItem(item(itemId), draft) } }

    override suspend fun reclassifyItem(itemId: String, lineType: LineType): Result<Unit> =
        runCatching {
            coordinator.write {
                review.updateItem(item(itemId), lineType.name.lowercase(Locale.ROOT), note = null)
            }
        }

    override suspend fun setItemNote(itemId: String, note: String): Result<Unit> = runCatching {
        val text = note.trim().take(MAX_NOTE_LENGTH)
        coordinator.write { review.updateItem(item(itemId), lineType = null, note = text) }
    }

    private suspend fun item(id: String): ReceiptItemEntity =
        products.getItem(id) ?: fail(CatalogError.NOT_FOUND)

    private companion object {
        const val PAGE_SIZE = 30

        /** The server's `USER_NOTE_MAX_LENGTH`. */
        const val MAX_NOTE_LENGTH = 2000
    }
}

private fun ReviewRow.toModel(issuedAt: OffsetDateTime) = ReviewItem(
    id = item.id,
    receiptId = item.receiptId,
    issuedAt = issuedAt,
    merchantId = merchantId,
    merchantName = merchantName,
    lineNumber = item.lineNumber,
    description = item.description,
    merchantProductCode = item.merchantProductCode,
    quantity = Fixed.toDecimalOrNull(item.quantityE4, Fixed.E4),
    unit = item.unit,
    unitPrice = Fixed.toDecimalOrNull(item.unitPriceE4, Fixed.E4),
    grossAmount = Money(Fixed.toDecimal(item.grossAmountMinor, Fixed.MINOR), currency),
    userNote = item.userNote,
)
