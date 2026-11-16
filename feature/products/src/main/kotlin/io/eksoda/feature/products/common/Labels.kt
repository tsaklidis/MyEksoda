package io.eksoda.feature.products.common

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.eksoda.core.designsystem.component.CategoryIcon
import io.eksoda.core.model.CatalogError
import io.eksoda.core.model.CatalogException
import io.eksoda.core.model.Category
import io.eksoda.core.ui.text.appLocale
import io.eksoda.core.ui.text.categoryName
import io.eksoda.core.ui.text.formatQuantity
import io.eksoda.feature.products.R
import java.math.BigDecimal

/** The package units a product can have (the server's `Product.PackageUnit`). */
internal val PACKAGE_UNITS = listOf("g", "kg", "ml", "cl", "l", "piece")

/** Why a write failed, for a snackbar or a form. */
internal fun Throwable?.catalogError(): CatalogError? = (this as? CatalogException)?.error

@StringRes
internal fun catalogErrorRes(error: CatalogError?): Int = when (error) {
    CatalogError.BLANK_NAME -> R.string.error_blank_name
    CatalogError.NAME_TAKEN -> R.string.error_name_taken
    CatalogError.SYSTEM_CATEGORY -> R.string.error_system_category
    CatalogError.CATEGORY_IN_USE -> R.string.error_category_in_use
    CatalogError.NOT_A_PRODUCT_LINE -> R.string.error_not_a_product_line
    CatalogError.PACKAGE_INCOMPLETE -> R.string.error_package_incomplete
    CatalogError.TOO_MANY_PRODUCTS -> R.string.error_too_many_products
    CatalogError.NOT_FOUND -> R.string.error_not_found
    null -> R.string.error_save_failed
}

/** A normalized unit (kg, l, piece) as shown after a price: "€1.29 / kg". */
@Composable
internal fun unitLabel(unit: String?): String = stringResource(
    when (unit) {
        "kg" -> R.string.unit_kg
        "l" -> R.string.unit_l
        "piece" -> R.string.unit_piece
        else -> R.string.unit_other
    },
)

@Composable
internal fun packageUnitLabel(unit: String): String = stringResource(
    when (unit) {
        "g" -> R.string.package_g
        "kg" -> R.string.package_kg
        "ml" -> R.string.package_ml
        "cl" -> R.string.package_cl
        "l" -> R.string.package_l
        else -> R.string.unit_piece
    },
)

/** "500 g", "1 l", "6 pieces". */
@Composable
internal fun packageLabel(quantity: BigDecimal, unit: String): String = stringResource(
    R.string.package_size,
    formatQuantity(quantity, appLocale()),
    packageUnitLabel(unit),
)

/** The category's name, or "No category". */
@Composable
internal fun categoryLabel(category: Category?): String =
    category?.let { categoryName(it) } ?: stringResource(R.string.category_none)

/** The category icon in a tinted circle, as product rows show it. */
@Composable
internal fun CategoryBadge(slug: String?, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    Box(
        modifier = modifier
            .size(size)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        CategoryIcon(slug, modifier = Modifier.size(size / 2))
    }
}
