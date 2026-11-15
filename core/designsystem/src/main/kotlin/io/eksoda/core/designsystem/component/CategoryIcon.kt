package io.eksoda.core.designsystem.component

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
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.SetMeal
import androidx.compose.material.icons.filled.ShoppingBasket
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

@Composable
fun CategoryIcon(slug: String?, modifier: Modifier = Modifier, tint: Color = Color.Unspecified) {
    val (icon, color) = getCategoryInfo(slug)
    Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = modifier,
        tint = if (tint == Color.Unspecified) color else tint,
    )
}

fun getCategoryInfo(slug: String?): Pair<ImageVector, Color> =
    CATEGORY_STYLES[slug] ?: DEFAULT_STYLE

/** Icon and color per system category slug (the 19 seeded categories, §6.3). */
private val CATEGORY_STYLES: Map<String, Pair<ImageVector, Color>> = mapOf(
    "groceries" to (Icons.Default.ShoppingBasket to Color(0xFF4CAF50)),
    "dairy-and-eggs" to (Icons.Default.Egg to Color(0xFF81D4FA)),
    "meat-and-fish" to (Icons.Default.SetMeal to Color(0xFFFF7043)),
    "fruits-and-vegetables" to (Icons.Default.Eco to Color(0xFFCDDC39)),
    "bakery" to (Icons.Default.BakeryDining to Color(0xFFFFC107)),
    "beverages" to (Icons.Default.LocalCafe to Color(0xFF795548)),
    "frozen-food" to (Icons.Default.AcUnit to Color(0xFF00BCD4)),
    "snacks-and-sweets" to (Icons.Default.Cookie to Color(0xFFF06292)),
    "cleaning-products" to (Icons.Default.CleaningServices to Color(0xFF009688)),
    "personal-care" to (Icons.Default.Spa to Color(0xFF9575CD)),
    "household-items" to (Icons.Default.Chair to Color(0xFF607D8B)),
    "pet-supplies" to (Icons.Default.Pets to Color(0xFFFF9800)),
    "restaurants-and-cafes" to (Icons.Default.Restaurant to Color(0xFFFF5722)),
    "pharmacy-and-health" to (Icons.Default.Medication to Color(0xFFF44336)),
    "fuel-and-transport" to (Icons.Default.LocalGasStation to Color(0xFF3F51B5)),
    "clothing-and-footwear" to (Icons.Default.Checkroom to Color(0xFF673AB7)),
    "electronics-and-appliances" to (Icons.Default.Devices to Color(0xFF2196F3)),
    "services" to (Icons.Default.Handyman to Color(0xFF9E9E9E)),
)

private val DEFAULT_STYLE: Pair<ImageVector, Color> = Icons.Default.Category to Color(0xFF9E9E9E)
