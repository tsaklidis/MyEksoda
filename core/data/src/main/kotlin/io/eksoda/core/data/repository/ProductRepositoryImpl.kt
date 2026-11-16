package io.eksoda.core.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import io.eksoda.core.data.db.WriteCoordinator
import io.eksoda.core.data.di.DefaultDispatcher
import io.eksoda.core.data.products.ReviewService
import io.eksoda.core.data.products.fail
import io.eksoda.core.data.util.Fixed
import io.eksoda.core.database.dao.PriceRow
import io.eksoda.core.database.dao.ProductDao
import io.eksoda.core.database.dao.ProductRow
import io.eksoda.core.model.AvailableUnit
import io.eksoda.core.model.CatalogError
import io.eksoda.core.model.Category
import io.eksoda.core.model.ComparisonReason
import io.eksoda.core.model.Merchant
import io.eksoda.core.model.MerchantPrices
import io.eksoda.core.model.Money
import io.eksoda.core.model.PriceComparison
import io.eksoda.core.model.PriceEntry
import io.eksoda.core.model.PriceHistory
import io.eksoda.core.model.PriceStats
import io.eksoda.core.model.Product
import io.eksoda.core.model.ProductDraft
import io.eksoda.core.model.ProductFilter
import io.eksoda.core.model.ProductOrdering
import io.eksoda.core.model.Store
import io.eksoda.engine.products.Comparison
import io.eksoda.engine.products.NO_PRICES
import io.eksoda.engine.products.PriceObservation
import io.eksoda.engine.products.UNKNOWN_UNIT
import io.eksoda.engine.products.compare
import io.eksoda.engine.products.priceStats
import io.eksoda.engine.text.normalizeText
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import io.eksoda.engine.products.MerchantPrices as EngineMerchantPrices
import io.eksoda.engine.products.PriceStats as EnginePriceStats

@Singleton
class ProductRepositoryImpl @Inject constructor(
    private val products: ProductDao,
    private val review: ReviewService,
    private val coordinator: WriteCoordinator,
    private val householdZone: HouseholdZone,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : ProductRepository {

    override fun getProducts(filter: ProductFilter): Flow<PagingData<Product>> {
        val key = normalizeText(filter.query)
        val byLineCount = filter.ordering == ProductOrdering.MOST_BOUGHT
        return Pager(PagingConfig(pageSize = PAGE_SIZE)) {
            products.pagingRows(key, filter.category?.id, filter.merchant?.id, byLineCount)
        }.flow.map { page -> page.map { it.toModel() } }
    }

    override fun getProduct(id: String): Flow<Product?> =
        products.observeRow(id).map { it?.toModel() }

    /** Statistics are computed over every entry, as on the server, off the main thread. */
    override fun getPriceHistory(id: String): Flow<PriceHistory> = products.observePrices(id)
        .map { rows ->
            val zone = householdZone.current()
            PriceHistory(
                entries = rows.map { it.toEntry(zone) },
                stats = priceStats(rows.mapNotNull { it.toObservation() }).map { it.toModel() },
            )
        }.flowOn(defaultDispatcher)

    override fun getComparison(id: String, unit: String?): Flow<PriceComparison> =
        products.observePrices(id)
            .map { rows -> compare(rows.mapNotNull { it.toObservation() }, unit).toModel() }
            .flowOn(defaultDispatcher)

    override suspend fun createProduct(draft: ProductDraft): Result<String> = runCatching {
        coordinator.write { review.createProduct(draft).id }
    }

    override suspend fun updateProduct(id: String, draft: ProductDraft): Result<Unit> =
        runCatching {
            coordinator.write {
                val product = products.get(id) ?: fail(CatalogError.NOT_FOUND)
                review.updateProduct(product, draft)
            }
        }

    override suspend fun mergeProducts(targetId: String, sourceIds: Set<String>): Result<Unit> =
        runCatching {
            coordinator.write {
                val target = products.get(targetId) ?: fail(CatalogError.NOT_FOUND)
                review.mergeProducts(target, sourceIds)
            }
        }

    private companion object {
        const val PAGE_SIZE = 50
    }
}

internal fun ProductRow.toModel(): Product = Product(
    id = product.id,
    name = product.name,
    category = product.categoryId?.let {
        Category(
            id = it,
            name = categoryName.orEmpty(),
            slug = categorySlug.orEmpty(),
            isSystem = categoryIsSystem == true,
        )
    },
    brand = product.brand.ifEmpty { null },
    packageQuantity = Fixed.toDecimalOrNull(product.packageQuantityE4, Fixed.E4)
        ?.stripTrailingZeros(),
    packageUnit = product.packageUnit,
    lineCount = lineCount,
)

private fun PriceRow.toEntry(zone: ZoneId): PriceEntry = PriceEntry(
    itemId = id,
    receiptId = receiptId,
    issuedAt = OffsetDateTime.ofInstant(Instant.ofEpochMilli(issuedAt), zone),
    merchant = Merchant(id = merchantId, name = merchantName),
    store = storeId?.let {
        Store(
            id = it,
            name = storeName.orEmpty(),
            address = storeStreet?.ifEmpty { null },
            city = storeCity?.ifEmpty { null },
        )
    },
    description = description,
    quantity = Fixed.toDecimalOrNull(quantityE4, Fixed.E4),
    unit = unit,
    grossAmount = Money(Fixed.toDecimal(grossAmountMinor, Fixed.MINOR), currency),
    normalizedQuantity = Fixed.toDecimalOrNull(normalizedQuantityE6, Fixed.E6),
    normalizedUnit = normalizedUnit,
    normalizedUnitPrice = Fixed.toDecimalOrNull(normalizedUnitPriceE4, Fixed.E4),
)

/** The engine's view of a `PRICE_HISTORY` row (the query guarantees the price and unit). */
private fun PriceRow.toObservation(): PriceObservation? {
    val unit = normalizedUnit ?: return null
    val price = normalizedUnitPriceE4 ?: return null
    return PriceObservation(
        merchantId = merchantId,
        merchantName = merchantName,
        normalizedUnit = unit,
        currency = currency,
        normalizedUnitPrice = Fixed.toDecimal(price, Fixed.E4),
        grossAmount = Fixed.toDecimal(grossAmountMinor, Fixed.MINOR),
        normalizedQuantity = Fixed.toDecimal(normalizedQuantityE6 ?: 0, Fixed.E6),
    )
}

private fun EnginePriceStats.toModel() = PriceStats(
    normalizedUnit = normalizedUnit,
    currency = currency,
    count = count,
    latest = latest,
    previous = previous,
    minimum = minimum,
    maximum = maximum,
    average = average,
)

private fun EngineMerchantPrices.toModel() = MerchantPrices(
    merchant = Merchant(id = merchantId, name = merchantName),
    rank = rank,
    stats = stats.toModel(),
)

private fun Comparison.toModel() = PriceComparison(
    comparable = comparable,
    reason = when (reason) {
        null -> null
        NO_PRICES -> ComparisonReason.NO_PRICES
        UNKNOWN_UNIT -> ComparisonReason.UNKNOWN_UNIT
        else -> ComparisonReason.ONE_MERCHANT
    },
    normalizedUnit = normalizedUnit,
    currency = currency,
    merchants = merchants.map { it.toModel() },
    availableUnits = availableUnits.map { AvailableUnit(it.normalizedUnit, it.currency, it.count) },
)
