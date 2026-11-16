package io.eksoda.core.data.products

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import io.eksoda.core.data.db.WriteCoordinator
import io.eksoda.core.database.CategorySeed
import io.eksoda.core.database.dao.ProductDao
import io.eksoda.engine.products.suggestCategorySlug
import kotlinx.coroutines.flow.first
import java.time.Clock
import javax.inject.Inject

/**
 * Gives products created before automatic categories (AutoCategory.kt) a system category from
 * their name. Runs once per [VERSION] of the keyword rules, so a category the user later
 * removes is not put back on every start; products the user already categorized are untouched.
 */
class AutoCategoryBackfill @Inject constructor(
    private val products: ProductDao,
    private val coordinator: WriteCoordinator,
    private val preferences: DataStore<Preferences>,
    private val clock: Clock,
) {
    suspend fun run() {
        if ((preferences.data.first()[DONE_KEY] ?: 0) >= VERSION) return
        coordinator.write {
            val now = clock.millis()
            products.uncategorized().forEach { product ->
                val slug = suggestCategorySlug(product.name) ?: return@forEach
                products.setCategoryIfNone(product.id, CategorySeed.categoryId(slug), now)
            }
        }
        preferences.edit { it[DONE_KEY] = VERSION }
    }

    private companion object {
        const val VERSION = 1
        val DONE_KEY = intPreferencesKey("auto_category_backfill_version")
    }
}
