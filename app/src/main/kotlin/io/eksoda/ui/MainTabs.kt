package io.eksoda.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.eksoda.R
import io.eksoda.core.designsystem.component.EksodaNavigationBar
import io.eksoda.core.designsystem.component.EksodaNavigationBarItem
import io.eksoda.core.designsystem.component.EksodaNavigationRail
import io.eksoda.core.designsystem.component.EksodaNavigationRailItem
import io.eksoda.core.designsystem.component.ScanButton
import io.eksoda.core.designsystem.component.ScanNavigationBarItem
import io.eksoda.feature.home.HomeRoute
import io.eksoda.feature.insights.InsightsRoute
import io.eksoda.feature.products.list.ProductListRoute
import io.eksoda.feature.receipts.list.ReceiptListRoute

/**
 * The top-level sections (android-app.md §4.1) in the redesign's order: Home · Receipts · (Scan)
 * · Insights · Products (docs/design/ui-redesign.md §5).
 */
private enum class Tab(val label: Int, val icon: ImageVector, val selectedIcon: ImageVector) {
    HOME(
        R.string.tab_home,
        Icons.Outlined.Home,
        Icons.Filled.Home,
    ),
    RECEIPTS(
        R.string.tab_receipts,
        Icons.AutoMirrored.Outlined.ReceiptLong,
        Icons.AutoMirrored.Filled.ReceiptLong,
    ),
    INSIGHTS(
        R.string.tab_insights,
        Icons.Outlined.Insights,
        Icons.Filled.Insights,
    ),
    PRODUCTS(
        R.string.tab_products,
        Icons.Outlined.Inventory2,
        Icons.Filled.Inventory2,
    ),
}

/** The tabs before and after the Scan button in the bar. */
private val TABS_BEFORE_SCAN = listOf(Tab.HOME, Tab.RECEIPTS)
private val TABS_AFTER_SCAN = listOf(Tab.INSIGHTS, Tab.PRODUCTS)

/** Wider than this, the tabs move to a rail on the side (Material's medium width class). */
private val RAIL_WIDTH = 600.dp

/**
 * The tabs with the redesign's bottom bar on phones (the raised terracotta Scan button in the
 * middle) and a rail on wider screens (Scan on top). Each tab keeps its own saved state (scroll
 * position, search text) while another is shown.
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
    onSettingsClick: () -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    // The tabs visited before this one, most recent last: Back returns to them in turn, then to
    // Home, and only leaves the app from Home.
    var history by rememberSaveable { mutableStateOf(listOf<Tab>()) }
    val select: (Tab) -> Unit = { entry ->
        if (entry != tab) {
            history = (history - entry) + tab
            tab = entry
        }
    }
    BackHandler(enabled = history.isNotEmpty() || tab != Tab.HOME) {
        val previous = history.lastOrNull() ?: Tab.HOME
        history = history.dropLast(1)
        tab = previous
    }
    val tabStates = rememberSaveableStateHolder()
    val content = @Composable { modifier: Modifier ->
        Box(modifier = modifier) {
            tabStates.SaveableStateProvider(tab.name) {
                when (tab) {
                    Tab.HOME -> HomeRoute(
                        onReceiptsClick = { select(Tab.RECEIPTS) },
                        onInsightsClick = { select(Tab.INSIGHTS) },
                        onReviewClick = onReviewClick,
                        onImportsClick = onImportsClick,
                        onReceiptClick = onReceiptClick,
                        onSettingsClick = onSettingsClick,
                    )

                    Tab.RECEIPTS -> ReceiptListRoute(
                        onReceiptClick = onReceiptClick,
                        onScanClick = onScanClick,
                        onImportsClick = onImportsClick,
                    )

                    Tab.INSIGHTS -> InsightsRoute(
                        onProductClick = onProductClick,
                        onProductsClick = { select(Tab.PRODUCTS) },
                        onMerchantsClick = onMerchantsClick,
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
    val scanLabel = stringResource(R.string.tab_scan)
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        if (maxWidth < RAIL_WIDTH) {
            Column(modifier = Modifier.fillMaxSize()) {
                // The bar pads itself for the navigation bar, so the screens above don't.
                content(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .consumeWindowInsets(WindowInsets.navigationBars),
                )
                EksodaNavigationBar {
                    TABS_BEFORE_SCAN.forEach { entry ->
                        TabItem(entry, selected = entry == tab) { select(entry) }
                    }
                    ScanNavigationBarItem(label = scanLabel, onClick = onScanClick)
                    TABS_AFTER_SCAN.forEach { entry ->
                        TabItem(entry, selected = entry == tab) { select(entry) }
                    }
                }
            }
        } else {
            // The rail pads itself for the start side of the system bars.
            val railInsets = WindowInsets.systemBars.only(WindowInsetsSides.Start)
            Row(modifier = Modifier.fillMaxSize()) {
                EksodaNavigationRail(
                    header = { ScanButton(label = scanLabel, onClick = onScanClick) },
                ) {
                    Tab.entries.forEach { entry ->
                        EksodaNavigationRailItem(
                            selected = entry == tab,
                            onClick = { select(entry) },
                            icon = if (entry == tab) entry.selectedIcon else entry.icon,
                            label = stringResource(entry.label),
                        )
                    }
                }
                content(
                    Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .consumeWindowInsets(railInsets),
                )
            }
        }
    }
}

@Composable
private fun RowScope.TabItem(entry: Tab, selected: Boolean, onClick: () -> Unit) {
    EksodaNavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = if (selected) entry.selectedIcon else entry.icon,
        label = stringResource(entry.label),
    )
}
