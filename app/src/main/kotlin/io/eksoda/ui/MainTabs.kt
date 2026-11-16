package io.eksoda.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import io.eksoda.R
import io.eksoda.feature.products.list.ProductListRoute
import io.eksoda.feature.receipts.list.ReceiptListRoute

/** The top-level sections (android-app.md §4.1). */
private enum class Tab(val label: Int, val icon: ImageVector, val selectedIcon: ImageVector) {
    RECEIPTS(
        R.string.tab_receipts,
        Icons.AutoMirrored.Filled.ReceiptLong,
        Icons.AutoMirrored.Filled.ReceiptLong,
    ),
    PRODUCTS(R.string.tab_products, Icons.Outlined.Inventory2, Icons.Filled.Inventory2),
}

/**
 * The tabs with a bottom bar on phones and a navigation rail on wider screens. Each tab keeps
 * its own saved state (scroll position, search text) while another is shown.
 */
@Composable
fun MainTabs(
    onReceiptClick: (String) -> Unit,
    onScanClick: () -> Unit,
    onImportsClick: () -> Unit,
    onProductClick: (String) -> Unit,
    onReviewClick: () -> Unit,
    onCategoriesClick: () -> Unit,
    onMerchantsClick: () -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(Tab.RECEIPTS) }
    val tabStates = rememberSaveableStateHolder()
    NavigationSuiteScaffold(
        navigationSuiteItems = {
            Tab.entries.forEach { entry ->
                item(
                    selected = entry == tab,
                    onClick = { tab = entry },
                    icon = {
                        Icon(
                            imageVector = if (entry == tab) entry.selectedIcon else entry.icon,
                            contentDescription = null,
                        )
                    },
                    label = { Text(stringResource(entry.label)) },
                )
            }
        },
    ) {
        tabStates.SaveableStateProvider(tab.name) {
            when (tab) {
                Tab.RECEIPTS -> ReceiptListRoute(
                    onReceiptClick = onReceiptClick,
                    onScanClick = onScanClick,
                    onImportsClick = onImportsClick,
                )

                Tab.PRODUCTS -> ProductListRoute(
                    onProductClick = onProductClick,
                    onReviewClick = onReviewClick,
                    onCategoriesClick = onCategoriesClick,
                    onMerchantsClick = onMerchantsClick,
                )
            }
        }
    }
}
