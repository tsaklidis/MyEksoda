package io.eksoda.core.database

import io.eksoda.core.database.entity.CategoryEntity
import java.util.UUID

/**
 * The 19 system categories, with fixed name-based UUIDs so every phone has the same ids. The
 * app shows their names through string resources keyed by slug; the English name is stored for
 * completeness.
 */
object CategorySeed {
    val SYSTEM_CATEGORIES: List<Pair<String, String>> = listOf(
        "groceries" to "Groceries",
        "dairy-and-eggs" to "Dairy and eggs",
        "meat-and-fish" to "Meat and fish",
        "fruits-and-vegetables" to "Fruits and vegetables",
        "bakery" to "Bakery",
        "beverages" to "Beverages",
        "frozen-food" to "Frozen food",
        "snacks-and-sweets" to "Snacks and sweets",
        "cleaning-products" to "Cleaning products",
        "personal-care" to "Personal care",
        "household-items" to "Household items",
        "pet-supplies" to "Pet supplies",
        "restaurants-and-cafes" to "Restaurants and cafés",
        "pharmacy-and-health" to "Pharmacy and health",
        "fuel-and-transport" to "Fuel and transport",
        "clothing-and-footwear" to "Clothing and footwear",
        "electronics-and-appliances" to "Electronics and appliances",
        "services" to "Services",
        "other" to "Other",
    )

    fun categoryId(slug: String): String =
        UUID.nameUUIDFromBytes("category:$slug".toByteArray()).toString()

    private val SLUGS_BY_ID: Map<String, String> by lazy {
        SYSTEM_CATEGORIES.associate { (slug, _) -> categoryId(slug) to slug }
    }

    /** The slug of a system category id; null for a household category. */
    fun slugOf(id: String): String? = SLUGS_BY_ID[id]

    fun entities(now: Long): List<CategoryEntity> = SYSTEM_CATEGORIES.map { (slug, name) ->
        CategoryEntity(
            id = categoryId(slug),
            slug = slug,
            name = name,
            isSystem = true,
            createdAt = now,
            updatedAt = now,
        )
    }

    /** SQL for Room's onCreate callback, where DAOs are not available yet. */
    fun insertStatements(now: Long): List<String> = entities(now).map { category ->
        val name = category.name.replace("'", "''")
        "INSERT OR IGNORE INTO category (id, slug, name, is_system, created_at, updated_at) " +
            "VALUES ('${category.id}', '${category.slug}', '$name', 1, $now, $now)"
    }
}
