package io.eksoda.core.data.repository

import io.eksoda.core.model.Option
import kotlinx.coroutines.flow.Flow

/** Choices for the receipt filter sheet and the store picker. */
interface ReceiptOptionsRepository {
    /** Merchants, the one with the most receipts first. */
    fun merchantOptions(): Flow<List<Option>>

    /** The stores of one merchant. */
    fun storeOptions(merchantId: String): Flow<List<Option>>

    /** System categories, with their slug as the name (the UI shows a translated name). */
    fun categoryOptions(): Flow<List<Option>>

    /** Products whose normalized name contains the normalized query; at most 30. */
    suspend fun searchProducts(query: String): List<Option>
}
