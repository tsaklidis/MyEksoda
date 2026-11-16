package io.eksoda.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.BakeryDining
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Chair
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Cookie
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Egg
import androidx.compose.material.icons.filled.Handyman
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.SetMeal
import androidx.compose.material.icons.filled.ShoppingBasket
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.eksoda.core.designsystem.theme.EksodaTheme

/**
 * The category's icon, in ink-2 unless [tint] is given. Categories have no colors of their own
 * (docs/design/ui-redesign.md §3, §4): their identity is the name and the icon, and charts color
 * by rank.
 */
@Composable
fun CategoryIcon(slug: String?, modifier: Modifier = Modifier, tint: Color = Color.Unspecified) {
    Icon(
        imageVector = categoryIcon(slug),
        contentDescription = null,
        modifier = modifier,
        tint = if (tint == Color.Unspecified) EksodaTheme.colors.ink2 else tint,
    )
}

/** The category's icon in a neutral tile with a line border, as lists show it. */
@Composable
fun CategoryTile(slug: String?, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val colors = EksodaTheme.colors
    val shape = RoundedCornerShape(size / 4)
    Box(
        modifier = modifier
            .size(size)
            .background(colors.tile, shape)
            .border(1.dp, colors.line, shape),
        contentAlignment = Alignment.Center,
    ) {
        CategoryIcon(slug, modifier = Modifier.size(size / 2))
    }
}

/** The icon of a category (none or unknown: the "other" icon). */
fun categoryIcon(slug: String?): ImageVector = CATEGORY_ICONS[slug] ?: DEFAULT_ICON

/** Icon per system category slug (§6.3); "other" and unknown slugs get the default. */
private val CATEGORY_ICONS: Map<String, ImageVector> = mapOf(
    "groceries" to Icons.Default.ShoppingBasket,
    "dairy-and-eggs" to Icons.Default.Egg,
    "meat-and-fish" to Icons.Default.SetMeal,
    "fruits-and-vegetables" to Icons.Default.Eco,
    "bakery" to Icons.Default.BakeryDining,
    "beverages" to Icons.Default.LocalCafe,
    "frozen-food" to Icons.Default.AcUnit,
    "snacks-and-sweets" to Icons.Default.Cookie,
    "cleaning-products" to Icons.Default.CleaningServices,
    "personal-care" to Icons.Default.Spa,
    "household-items" to Icons.Default.Chair,
    "pet-supplies" to Icons.Default.Pets,
    "restaurants-and-cafes" to Icons.Default.Restaurant,
    "pharmacy-and-health" to Icons.Default.Medication,
    "fuel-and-transport" to Icons.Default.LocalGasStation,
    "clothing-and-footwear" to Icons.Default.Checkroom,
    "electronics-and-appliances" to Icons.Default.Devices,
    "services" to Icons.Default.Handyman,
)

private val DEFAULT_ICON: ImageVector = Icons.Default.Category

@Preview
@Composable
private fun CategoryTilePreview() {
    EksodaTheme { CategoryTile("dairy-and-eggs") }
}
