package io.eksoda.core.data.repository

import io.eksoda.core.data.di.DefaultDispatcher
import io.eksoda.core.data.util.Fixed
import io.eksoda.core.database.dao.BoardRow
import io.eksoda.core.database.dao.ProductDao
import io.eksoda.core.model.Merchant
import io.eksoda.core.model.PriceBoard
import io.eksoda.core.model.ProductDeal
import io.eksoda.core.model.ProductTrend
import io.eksoda.core.model.ShopDeals
import io.eksoda.core.model.ShopPrice
import io.eksoda.core.model.ShopTrend
import io.eksoda.engine.products.MerchantPrice
import io.eksoda.engine.products.PricedPurchase
import io.eksoda.engine.products.priceBoard
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import io.eksoda.engine.products.ProductDeal as EngineDeal

@Singleton
class PriceBoardRepositoryImpl @Inject constructor(
    private val products: ProductDao,
    private val householdZone: HouseholdZone,
    private val clock: Clock,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : PriceBoardRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getBoard(): Flow<PriceBoard> = flow {
        emit(LocalDate.now(clock.withZone(householdZone.current())))
    }.flatMapLatest { today ->
        products.observePricesSince(today.minusDays(WINDOW_DAYS).toString()).map { rows ->
            board(rows, today)
        }
    }.flowOn(defaultDispatcher)

    private fun board(rows: List<BoardRow>, today: LocalDate): PriceBoard {
        val vats = rows.associate { it.merchantId to it.vatNumber }
        val merchant = { id: String, name: String -> Merchant(id, name, vats[id]) }
        val shopPrice = { price: MerchantPrice ->
            ShopPrice(merchant(price.merchantId, price.merchantName), price.average)
        }
        val deal = { d: EngineDeal ->
            ProductDeal(
                d.productId,
                d.productName,
                d.unit,
                shopPrice(d.cheapest),
                d.others.map(shopPrice),
                d.saving,
            )
        }
        val board = priceBoard(rows.map { it.toPurchase() }, today)
        return PriceBoard(
            currency = board.currency,
            byProduct = board.byProduct.map(deal),
            byShop = board.byMarket.map {
                ShopDeals(
                    merchant(it.merchantId, it.merchantName),
                    it.products.map(deal),
                    it.saving,
                )
            },
            productTrends = board.productTrends.mapNotNull { trend ->
                val previous = trend.previous ?: return@mapNotNull null
                val latest = trend.latest ?: return@mapNotNull null
                ProductTrend(
                    trend.id,
                    trend.name,
                    trend.unit.orEmpty(),
                    previous,
                    latest,
                    trend.changePercent,
                )
            },
            shopTrends = board.marketTrends.map {
                ShopTrend(merchant(it.id, it.name), it.changePercent, it.products)
            },
            saving = board.saving,
            shops = rows.filter {
                it.currency == board.currency
            }.map { it.merchantId }.distinct().size,
        )
    }

    private companion object {
        /** The comparison looks back 90 days and the trends 60: half a year covers both. */
        const val WINDOW_DAYS = 180L
    }
}

private fun BoardRow.toPurchase() = PricedPurchase(
    productId = productId,
    productName = productName,
    merchantId = merchantId,
    merchantName = merchantName,
    date = LocalDate.parse(issuedLocalDate),
    unit = normalizedUnit,
    currency = currency,
    unitPrice = Fixed.toDecimal(normalizedUnitPriceE4, Fixed.E4),
    quantity = Fixed.toDecimal(normalizedQuantityE6, Fixed.E6),
)
