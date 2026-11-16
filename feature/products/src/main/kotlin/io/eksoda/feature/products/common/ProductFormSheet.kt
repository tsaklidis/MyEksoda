package io.eksoda.feature.products.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.eksoda.core.designsystem.component.CategoryIcon
import io.eksoda.core.designsystem.component.EksodaButton
import io.eksoda.core.model.Category
import io.eksoda.core.model.ProductDraft
import io.eksoda.core.ui.text.appLocale
import io.eksoda.core.ui.text.categoryName
import io.eksoda.core.ui.text.parseDecimalInput
import io.eksoda.feature.products.R
import java.math.BigDecimal
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Create or edit a product (`android-app.md` §6.6.2 Edit, §6.7 New product): name, brand,
 * category and the package size (both parts or neither). When [warnPackageChange], a changed
 * package size shows that every purchase's price per unit is recalculated.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProductFormSheet(
    title: String,
    initial: ProductDraft,
    categories: List<Category>,
    onSave: (ProductDraft) -> Unit,
    onDismiss: () -> Unit,
    warnPackageChange: Boolean = false,
    saving: Boolean = false,
    errorText: String? = null,
) {
    val locale = appLocale()
    var name by rememberSaveable { mutableStateOf(initial.name) }
    var brand by rememberSaveable { mutableStateOf(initial.brand) }
    var categoryId by rememberSaveable { mutableStateOf(initial.categoryId) }
    var quantityText by rememberSaveable {
        mutableStateOf(initial.packageQuantity?.let { quantityInput(it, locale) }.orEmpty())
    }
    var unit by rememberSaveable { mutableStateOf(initial.packageUnit) }
    val quantity = parseDecimalInput(quantityText)?.takeIf { it.signum() > 0 }
    val packageValid = if (quantityText.isBlank()) {
        unit == null
    } else {
        quantity != null && unit != null
    }
    val draft = ProductDraft(
        name = name,
        brand = brand,
        categoryId = categoryId,
        packageQuantity = quantity,
        packageUnit = if (quantityText.isBlank()) null else unit,
    )
    val packageChanged = !sameDecimal(draft.packageQuantity, initial.packageQuantity) ||
        draft.packageUnit != initial.packageUnit
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.product_name)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = brand,
                onValueChange = { brand = it },
                label = { Text(stringResource(R.string.product_brand)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )
            PackageSection(
                quantityText = quantityText,
                unit = unit,
                valid = packageValid,
                onQuantityChange = { quantityText = it },
                onUnitChange = { unit = it },
            )
            if (warnPackageChange && packageChanged) Warning(R.string.product_package_warning)
            CategorySection(categories, categoryId) { categoryId = it }
            if (errorText != null) {
                Text(
                    text = errorText,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                EksodaButton(
                    onClick = { onSave(draft) },
                    text = stringResource(R.string.save),
                    enabled = name.isNotBlank() && packageValid,
                    isLoading = saving,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PackageSection(
    quantityText: String,
    unit: String?,
    valid: Boolean,
    onQuantityChange: (String) -> Unit,
    onUnitChange: (String?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = quantityText,
            onValueChange = onQuantityChange,
            label = { Text(stringResource(R.string.product_package)) },
            supportingText = { Text(stringResource(R.string.product_package_hint)) },
            isError = !valid && quantityText.isNotBlank(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PACKAGE_UNITS.forEach { option ->
                FilterChip(
                    selected = option == unit,
                    onClick = { onUnitChange(if (option == unit) null else option) },
                    label = { Text(packageUnitLabel(option)) },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategorySection(
    categories: List<Category>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
) {
    Text(
        text = stringResource(R.string.product_category),
        style = MaterialTheme.typography.titleSmall,
    )
    val named = categories.map { it to categoryName(it) }
    val sorted = remember(named) { named.sortedBy { (_, label) -> label.lowercase() } }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        sorted.forEach { (category, label) ->
            FilterChip(
                selected = category.id == selectedId,
                onClick = { onSelect(if (category.id == selectedId) null else category.id) },
                label = { Text(label) },
                leadingIcon = { CategoryIcon(category.slug, modifier = Modifier.size(18.dp)) },
            )
        }
    }
}

@Composable
internal fun Warning(text: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(
            Icons.Outlined.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = stringResource(text),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Every decimal of the package size, with the locale's separator (3 decimals for 0.125 kg). */
private fun quantityInput(value: BigDecimal, locale: Locale): String = value.stripTrailingZeros()
    .toPlainString()
    .replace('.', DecimalFormatSymbols.getInstance(locale).decimalSeparator)

private fun sameDecimal(a: BigDecimal?, b: BigDecimal?): Boolean =
    if (a == null || b == null) a == b else a.compareTo(b) == 0
