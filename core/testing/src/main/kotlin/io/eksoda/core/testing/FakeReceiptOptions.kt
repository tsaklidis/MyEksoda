package io.eksoda.core.testing

import io.eksoda.core.data.repository.ReceiptFilterStore
import io.eksoda.core.data.repository.ReceiptOptionsRepository
import io.eksoda.core.model.Option
import io.eksoda.core.model.ReceiptFilter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** [ReceiptOptionsRepository] in memory: the options as set, products searched by name. */
class FakeReceiptOptionsRepository : ReceiptOptionsRepository {
    val merchants = MutableStateFlow<List<Option>>(emptyList())
    val stores = MutableStateFlow<Map<String, List<Option>>>(emptyMap())
    val categories = MutableStateFlow<List<Option>>(emptyList())
    var products: List<Option> = emptyList()

    /** The product searches, in order. */
    val searches = mutableListOf<String>()

    override fun merchantOptions(): Flow<List<Option>> = merchants

    override fun storeOptions(merchantId: String): Flow<List<Option>> =
        stores.map { it[merchantId].orEmpty() }

    override fun categoryOptions(): Flow<List<Option>> = categories

    override suspend fun searchProducts(query: String): List<Option> {
        searches += query
        return products.filter { it.name.contains(query, ignoreCase = true) }
    }
}

/** [ReceiptFilterStore] in memory. [loadFailure], when set, is what [load] throws. */
class FakeReceiptFilterStore(var saved: ReceiptFilter? = null) : ReceiptFilterStore {
    var loadFailure: Throwable? = null

    /** Every saved filter, in order. */
    val saves = mutableListOf<ReceiptFilter>()

    override suspend fun load(): ReceiptFilter {
        loadFailure?.let { throw it }
        return saved ?: ReceiptFilter()
    }

    override suspend fun save(filter: ReceiptFilter) {
        saves += filter
        saved = filter
    }
}
