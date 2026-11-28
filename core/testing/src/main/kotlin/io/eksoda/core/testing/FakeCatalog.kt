package io.eksoda.core.testing

import androidx.paging.PagingData
import io.eksoda.core.data.repository.CatalogRepository
import io.eksoda.core.data.repository.ProductRepository
import io.eksoda.core.data.repository.ReviewRepository
import io.eksoda.core.model.Category
import io.eksoda.core.model.CategorySummary
import io.eksoda.core.model.LineType
import io.eksoda.core.model.MerchantSummary
import io.eksoda.core.model.Product
import io.eksoda.core.model.ProductDraft
import io.eksoda.core.model.ProductFilter
import io.eksoda.core.model.ProductPrices
import io.eksoda.core.model.ReviewItem
import io.eksoda.core.model.Store
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/** [CatalogRepository] in memory: categories, merchants and stores as set. */
class FakeCatalogRepository :
    Recorder(),
    CatalogRepository {
    val categories = MutableStateFlow<List<CategorySummary>>(emptyList())
    val merchants = MutableStateFlow<List<MerchantSummary>>(emptyList())
    val stores = MutableStateFlow<Map<String, List<Store>>>(emptyMap())

    override fun getCategories(): Flow<List<CategorySummary>> = categories

    override fun getCategoryList(): Flow<List<Category>> =
        categories.map { list -> list.map { it.category } }

    override suspend fun createCategory(name: String): Result<String> =
        answer("createCategory($name)") { "category-$name" }

    override suspend fun renameCategory(id: String, name: String): Result<Unit> =
        answer("renameCategory($id, $name)") {}

    override suspend fun deleteCategory(id: String): Result<Unit> = answer("deleteCategory($id)") {}

    override fun getMerchants(): Flow<List<MerchantSummary>> = merchants

    override fun getMerchant(id: String): Flow<MerchantSummary?> =
        merchants.map { list -> list.firstOrNull { it.id == id } }

    override fun getStores(merchantId: String): Flow<List<Store>> =
        stores.map { it[merchantId].orEmpty() }

    override suspend fun renameMerchant(id: String, name: String): Result<Unit> =
        answer("renameMerchant($id, $name)") {}

    override suspend fun setDefaultCategory(merchantId: String, categoryId: String?): Result<Unit> =
        answer("setDefaultCategory($merchantId, $categoryId)") {}
}

/** [ProductRepository] in memory. The filters the lists asked for are in [filters]. */
class FakeProductRepository :
    Recorder(),
    ProductRepository {
    val products = MutableStateFlow<List<Product>>(emptyList())

    /** The prices of every product; the units the screen asked for are in [units]. */
    val prices = MutableStateFlow(testPrices())
    val units = mutableListOf<String?>()
    val filters = mutableListOf<ProductFilter>()

    /** When set, [getProduct] fails with it. */
    var readFailure: Throwable? = null

    override fun getProducts(filter: ProductFilter): Flow<PagingData<Product>> {
        filters += filter
        return products.map { staticPage(it) }
    }

    override fun getProduct(id: String): Flow<Product?> = products.map { list ->
        readFailure?.let { throw it }
        list.firstOrNull { it.id == id }
    }

    override fun getPrices(id: String, unit: Flow<String?>): Flow<ProductPrices> =
        combine(prices, unit) { current, selected ->
            units += selected
            current
        }

    override suspend fun createProduct(draft: ProductDraft): Result<String> =
        answer("createProduct(${draft.name})") { "product-${draft.name}" }

    override suspend fun updateProduct(id: String, draft: ProductDraft): Result<Unit> =
        answer("updateProduct($id, ${draft.name})") {}

    override suspend fun mergeProducts(targetId: String, sourceIds: Set<String>): Result<Unit> =
        answer("mergeProducts($targetId, ${sourceIds.sorted()})") {}
}

/** [ReviewRepository] in memory: the queue as set; resolving a line takes it off. */
class FakeReviewRepository :
    Recorder(),
    ReviewRepository {
    val items = MutableStateFlow<List<ReviewItem>>(emptyList())
    val count = MutableStateFlow(0)

    override fun getReviewItems(): Flow<PagingData<ReviewItem>> = items.map { staticPage(it) }

    override fun getReviewCount(): Flow<Int> = count

    override suspend fun linkItem(itemId: String, productId: String): Result<Unit> =
        answer("linkItem($itemId, $productId)") { resolve(itemId) }

    override suspend fun createProductForItem(itemId: String, draft: ProductDraft): Result<Unit> =
        answer("createProductForItem($itemId, ${draft.name})") { resolve(itemId) }

    override suspend fun reclassifyItem(itemId: String, lineType: LineType): Result<Unit> =
        answer("reclassifyItem($itemId, $lineType)") { resolve(itemId) }

    override suspend fun setItemNote(itemId: String, note: String): Result<Unit> =
        answer("setItemNote($itemId, $note)") {}

    private fun resolve(itemId: String) {
        items.value = items.value.filterNot { it.id == itemId }
        count.value = items.value.size
    }
}
