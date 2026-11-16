package io.eksoda.core.ui.text

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.eksoda.core.model.Category
import io.eksoda.core.ui.R

/** The translated name of a system category, by slug (the 19 seeded categories, §6.3). */
@StringRes
fun categoryNameRes(slug: String?): Int = slug?.let(::systemCategoryRes) ?: R.string.category_other

/** A category's name: a system category's translated name, a household category's own name. */
@Composable
fun categoryName(category: Category): String =
    if (category.isSystem) stringResource(categoryNameRes(category.slug)) else category.name

/**
 * The name for a key that is a system category's slug or a household category's name (the
 * pickers and receipt lines use one column for both; a household name never equals a system
 * slug, since names are unique by slug).
 */
@Composable
fun categoryName(key: String): String = systemCategoryRes(key)?.let { stringResource(it) } ?: key

@StringRes
private fun systemCategoryRes(slug: String): Int? = when (slug) {
    "groceries" -> R.string.category_groceries
    "dairy-and-eggs" -> R.string.category_dairy_and_eggs
    "meat-and-fish" -> R.string.category_meat_and_fish
    "fruits-and-vegetables" -> R.string.category_fruits_and_vegetables
    "bakery" -> R.string.category_bakery
    "beverages" -> R.string.category_beverages
    "frozen-food" -> R.string.category_frozen_food
    "snacks-and-sweets" -> R.string.category_snacks_and_sweets
    "cleaning-products" -> R.string.category_cleaning_products
    "personal-care" -> R.string.category_personal_care
    "household-items" -> R.string.category_household_items
    "pet-supplies" -> R.string.category_pet_supplies
    "restaurants-and-cafes" -> R.string.category_restaurants_and_cafes
    "pharmacy-and-health" -> R.string.category_pharmacy_and_health
    "fuel-and-transport" -> R.string.category_fuel_and_transport
    "clothing-and-footwear" -> R.string.category_clothing_and_footwear
    "electronics-and-appliances" -> R.string.category_electronics_and_appliances
    "services" -> R.string.category_services
    "other" -> R.string.category_other
    else -> null
}
