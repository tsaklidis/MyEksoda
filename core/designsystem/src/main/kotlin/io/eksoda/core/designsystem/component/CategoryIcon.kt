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
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector

/** An icon with a color for light surfaces and one for dark surfaces. */
private class CategoryStyle(val icon: ImageVector, val light: Color, val dark: Color)

/**
 * The category's icon in its color. The light and dark colors keep at least 3:1 contrast
 * against the theme's surfaces (WCAG non-text contrast), so the icon stays readable in both.
 */
@Composable
fun CategoryIcon(slug: String?, modifier: Modifier = Modifier, tint: Color = Color.Unspecified) {
    val style = CATEGORY_STYLES[slug] ?: DEFAULT_STYLE
    val dark = MaterialTheme.colorScheme.surface.luminance() < DARK_SURFACE_LUMINANCE
    val themed = if (dark) style.dark else style.light
    Icon(
        imageVector = style.icon,
        contentDescription = null,
        modifier = modifier,
        tint = if (tint == Color.Unspecified) themed else tint,
    )
}

/** The icon and the light-surface color of a category (none: the "other" style). */
fun getCategoryInfo(slug: String?, dark: Boolean = false): Pair<ImageVector, Color> {
    val style = CATEGORY_STYLES[slug] ?: DEFAULT_STYLE
    return style.icon to if (dark) style.dark else style.light
}

private const val DARK_SURFACE_LUMINANCE = 0.5f

/** Icon and colors per system category slug (§6.3); "other" and unknown slugs get the default. */
private val CATEGORY_STYLES: Map<String, CategoryStyle> = mapOf(
    "groceries" to
        CategoryStyle(Icons.Default.ShoppingBasket, Color(0xFF2E7D32), Color(0xFF81C784)),
    "dairy-and-eggs" to CategoryStyle(Icons.Default.Egg, Color(0xFF0277BD), Color(0xFF81D4FA)),
    "meat-and-fish" to CategoryStyle(Icons.Default.SetMeal, Color(0xFFD84315), Color(0xFFFF8A65)),
    "fruits-and-vegetables" to
        CategoryStyle(Icons.Default.Eco, Color(0xFF558B2F), Color(0xFFC5E1A5)),
    "bakery" to CategoryStyle(Icons.Default.BakeryDining, Color(0xFFA1660A), Color(0xFFFFD54F)),
    "beverages" to CategoryStyle(Icons.Default.LocalCafe, Color(0xFF6D4C41), Color(0xFFBCAAA4)),
    "frozen-food" to CategoryStyle(Icons.Default.AcUnit, Color(0xFF00838F), Color(0xFF80DEEA)),
    "snacks-and-sweets" to
        CategoryStyle(Icons.Default.Cookie, Color(0xFFC2185B), Color(0xFFF48FB1)),
    "cleaning-products" to
        CategoryStyle(Icons.Default.CleaningServices, Color(0xFF00796B), Color(0xFF80CBC4)),
    "personal-care" to CategoryStyle(Icons.Default.Spa, Color(0xFF6A1B9A), Color(0xFFCE93D8)),
    "household-items" to CategoryStyle(Icons.Default.Chair, Color(0xFF546E7A), Color(0xFFB0BEC5)),
    "pet-supplies" to CategoryStyle(Icons.Default.Pets, Color(0xFFBF5B00), Color(0xFFFFB74D)),
    "restaurants-and-cafes" to
        CategoryStyle(Icons.Default.Restaurant, Color(0xFFBF360C), Color(0xFFFFAB91)),
    "pharmacy-and-health" to
        CategoryStyle(Icons.Default.Medication, Color(0xFFC62828), Color(0xFFEF9A9A)),
    "fuel-and-transport" to
        CategoryStyle(Icons.Default.LocalGasStation, Color(0xFF3949AB), Color(0xFF9FA8DA)),
    "clothing-and-footwear" to
        CategoryStyle(Icons.Default.Checkroom, Color(0xFF5E35B1), Color(0xFFB39DDB)),
    "electronics-and-appliances" to
        CategoryStyle(Icons.Default.Devices, Color(0xFF1565C0), Color(0xFF90CAF9)),
    "services" to CategoryStyle(Icons.Default.Handyman, Color(0xFF616161), Color(0xFFBDBDBD)),
)

private val DEFAULT_STYLE =
    CategoryStyle(Icons.Default.Category, Color(0xFF616161), Color(0xFFBDBDBD))
