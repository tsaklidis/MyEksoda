package io.eksoda.core.data.repository

import io.eksoda.core.data.db.WriteCoordinator
import io.eksoda.core.data.products.fail
import io.eksoda.core.database.dao.CategoryDao
import io.eksoda.core.database.dao.CategoryRow
import io.eksoda.core.database.dao.MerchantDao
import io.eksoda.core.database.dao.MerchantRow
import io.eksoda.core.database.dao.TombstoneDao
import io.eksoda.core.database.entity.CategoryEntity
import io.eksoda.core.database.entity.TombstoneEntity
import io.eksoda.core.model.CatalogError
import io.eksoda.core.model.Category
import io.eksoda.core.model.CategorySummary
import io.eksoda.core.model.MerchantSummary
import io.eksoda.core.model.Store
import io.eksoda.engine.merchants.FieldSize
import io.eksoda.engine.merchants.deriveDisplayName
import io.eksoda.engine.products.categorySlug
import io.eksoda.engine.text.normalizeText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Household categories and the user's merchant edits (name, default category). */
@Singleton
class CatalogRepositoryImpl @Inject constructor(
    private val categories: CategoryDao,
    private val merchants: MerchantDao,
    private val tombstones: TombstoneDao,
    private val coordinator: WriteCoordinator,
    private val householdZone: HouseholdZone,
    private val clock: Clock,
) : CatalogRepository {

    override fun getCategories(): Flow<List<CategorySummary>> =
        categories.observeRows().map { rows -> rows.map { it.toModel() } }

    override fun getCategoryList(): Flow<List<Category>> = categories.observeAll()
        .map { rows -> rows.map { Category(it.id, it.name, it.slug, it.isSystem) } }

    override suspend fun createCategory(name: String): Result<String> = runCatching {
        val (text, slug) = checkedName(name)
        coordinator.write {
            if (categories.slugTaken(slug, exceptId = "")) fail(CatalogError.NAME_TAKEN)
            val now = clock.millis()
            val id = UUID.randomUUID().toString()
            categories.insert(CategoryEntity(id, slug, text, false, now, now))
            id
        }
    }

    override suspend fun renameCategory(id: String, name: String): Result<Unit> = runCatching {
        coordinator.write {
            val category = categories.get(id) ?: fail(CatalogError.NOT_FOUND)
            if (category.isSystem) fail(CatalogError.SYSTEM_CATEGORY)
            val (text, slug) = checkedName(name)
            if (categories.slugTaken(slug, exceptId = id)) fail(CatalogError.NAME_TAKEN)
            categories.update(category.copy(name = text, slug = slug, updatedAt = clock.millis()))
        }
    }

    /** Merchants with it as their default category lose it (`SET NULL`). */
    override suspend fun deleteCategory(id: String): Result<Unit> = runCatching {
        coordinator.write {
            val category = categories.get(id) ?: fail(CatalogError.NOT_FOUND)
            if (category.isSystem) fail(CatalogError.SYSTEM_CATEGORY)
            if (categories.inUse(id)) fail(CatalogError.CATEGORY_IN_USE)
            categories.delete(id)
            tombstones.insert(TombstoneEntity("category", id, clock.millis()))
        }
    }

    override fun getMerchants(): Flow<List<MerchantSummary>> = merchants.observeRows()
        .map { rows ->
            val zone = householdZone.current()
            rows.map { it.toModel(zone) }
        }

    override fun getMerchant(id: String): Flow<MerchantSummary?> =
        merchants.observeRow(id).map { it?.toModel(householdZone.current()) }

    override fun getStores(merchantId: String): Flow<List<Store>> =
        merchants.observeStores(merchantId).map { rows ->
            rows.map {
                Store(
                    id = it.id,
                    name = it.displayName,
                    address = it.street.ifEmpty { null },
                    city = it.city.ifEmpty { null },
                )
            }
        }

    /** Sets `display_name_set_by_user`, so later imports keep the name. */
    override suspend fun renameMerchant(id: String, name: String): Result<Unit> = runCatching {
        coordinator.write {
            val merchant = merchants.get(id) ?: fail(CatalogError.NOT_FOUND)
            val text = name.trim().take(FieldSize.DISPLAY_NAME)
            val display = text.ifEmpty { deriveDisplayName(merchant.legalName) }
            merchants.update(
                merchant.copy(
                    displayName = display,
                    normalizedDisplayName = normalizeText(display),
                    displayNameSetByUser = text.isNotEmpty(),
                    updatedAt = clock.millis(),
                ),
            )
        }
    }

    override suspend fun setDefaultCategory(merchantId: String, categoryId: String?): Result<Unit> =
        runCatching {
            coordinator.write {
                if (categoryId != null && categories.get(categoryId) == null) {
                    fail(CatalogError.NOT_FOUND)
                }
                val updated = merchants.setDefaultCategory(merchantId, categoryId, clock.millis())
                if (updated != 1) fail(CatalogError.NOT_FOUND)
            }
        }

    /** The name trimmed, and its slug. A blank name is refused. */
    private fun checkedName(name: String): Pair<String, String> {
        val text = name.trim().take(CATEGORY_NAME_LENGTH)
        if (text.isEmpty()) fail(CatalogError.BLANK_NAME)
        return text to categorySlug(text)
    }

    private companion object {
        const val CATEGORY_NAME_LENGTH = 80
    }
}

private fun CategoryRow.toModel() = CategorySummary(
    category = Category(category.id, category.name, category.slug, category.isSystem),
    productCount = productCount,
)

private fun MerchantRow.toModel(zone: ZoneId) = MerchantSummary(
    id = merchant.id,
    name = merchant.displayName,
    legalName = merchant.legalName,
    vatNumber = merchant.vatNumber,
    nameSetByUser = merchant.displayNameSetByUser,
    address = listOf(
        listOf(merchant.street, merchant.streetNumber).filter { it.isNotEmpty() }.joinToString(" "),
        listOf(merchant.postalCode, merchant.city).filter { it.isNotEmpty() }.joinToString(" "),
    ).filter { it.isNotEmpty() }.joinToString(", ").ifEmpty { null },
    defaultCategory = merchant.defaultCategoryId?.let {
        Category(it, categoryName.orEmpty(), categorySlug.orEmpty(), categoryIsSystem == true)
    },
    receiptCount = receiptCount,
    lastReceiptAt = lastReceiptAt?.let { OffsetDateTime.ofInstant(Instant.ofEpochMilli(it), zone) },
)
