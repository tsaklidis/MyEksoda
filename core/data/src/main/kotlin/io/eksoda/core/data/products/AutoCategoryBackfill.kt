package io.eksoda.core.data.products

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import io.eksoda.core.data.db.WriteCoordinator
import io.eksoda.core.database.CategorySeed
import io.eksoda.core.database.dao.MerchantDao
import io.eksoda.core.database.dao.ProductDao
import io.eksoda.engine.products.merchantCategorySlug
import io.eksoda.engine.products.productCategorySlug
import kotlinx.coroutines.flow.first
import java.time.Clock
import javax.inject.Inject

/**
 * Applies the automatic category rules (AutoCategory.kt) to data imported before them: merchants
 * without a default category get one from their registered activity or name, then products
 * without a category get one from their name (eating out at cafés and restaurants). Runs once
 * per [VERSION] of the rules, so a category the user later removes is not put back on every
 * start; categories that are already set, by the user or earlier rules, are never changed.
 */
class AutoCategoryBackfill @Inject constructor(
    private val merchants: MerchantDao,
    private val products: ProductDao,
    private val coordinator: WriteCoordinator,
    private val preferences: DataStore<Preferences>,
    private val clock: Clock,
) {
    suspend fun run() {
        if ((preferences.data.first()[DONE_KEY] ?: 0) >= VERSION) return
        coordinator.write {
            val now = clock.millis()
            merchants.withoutDefaultCategory().forEach { merchant ->
                val slug = merchantCategorySlug(merchant.activityDescription, merchant.legalName)
                    ?: return@forEach
                merchants.setDefaultCategoryIfNone(merchant.id, CategorySeed.categoryId(slug), now)
            }
            products.uncategorized().forEach { product ->
                val merchantSlug = product.merchantCategoryId?.let(CategorySeed::slugOf)
                val slug = productCategorySlug(product.name, merchantSlug) ?: return@forEach
                products.setCategoryIfNone(product.id, CategorySeed.categoryId(slug), now)
            }
        }
        preferences.edit { it[DONE_KEY] = VERSION }
    }

    private companion object {
        /** 1: product names (2026-11-16). 2: merchant activity, brands, eating out (2026-11-17). */
        const val VERSION = 2
        val DONE_KEY = intPreferencesKey("auto_category_backfill_version")
    }
}
