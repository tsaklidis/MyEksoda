package io.eksoda.core.data.repository

import io.eksoda.core.model.PriceBoard
import kotlinx.coroutines.flow.Flow

/**
 * The Prices tab, from the household's own receipts: where each product costs least, what each
 * shop is cheapest for, and how prices move (core:engine `products/PriceBoard.kt`). It follows
 * the receipts, so a new one shows by itself.
 */
interface PriceBoardRepository {
    fun getBoard(): Flow<PriceBoard>
}
