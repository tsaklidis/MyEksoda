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
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.Sell
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
import io.eksoda.core.designsystem.component.AddButton
import io.eksoda.core.designsystem.component.AddNavigationBarItem
import io.eksoda.core.designsystem.component.EksodaNavigationBar
import io.eksoda.core.designsystem.component.EksodaNavigationBarItem
import io.eksoda.core.designsystem.component.EksodaNavigationRail
import io.eksoda.core.designsystem.component.EksodaNavigationRailItem
import io.eksoda.core.model.Period
import io.eksoda.feature.home.HomeActions
import io.eksoda.feature.home.HomeRoute
import io.eksoda.feature.insights.InsightsRoute
import io.eksoda.feature.products.list.ProductListRoute
import io.eksoda.feature.scan.share.PasteLinkImportDialog
import io.eksoda.feature.settings.more.MoreActions
import io.eksoda.feature.settings.more.MoreScreen

/**
 * The top-level sections in the design's order: Overview · Spending · (+) · Prices · More
 * (docs/design/ui-redesign.md §5).
 */
private enum class Tab(val label: Int, val icon: ImageVector, val selectedIcon: ImageVector) {
    OVERVIEW(R.string.tab_overview, Icons.Outlined.Home, Icons.Filled.Home),
    SPENDING(R.string.tab_spending, Icons.Outlined.PieChart, Icons.Filled.PieChart),
    PRICES(R.string.tab_prices, Icons.Outlined.Sell, Icons.Filled.Sell),
    MORE(R.string.tab_more, Icons.Outlined.Menu, Icons.Filled.Menu),
}

/** The tabs before and after the Add button in the bar. */
private val TABS_BEFORE_ADD = listOf(Tab.OVERVIEW, Tab.SPENDING)
private val TABS_AFTER_ADD = listOf(Tab.PRICES, Tab.MORE)

/** Wider than this, the tabs move to a rail on the side (Material's medium width class). */
private val RAIL_WIDTH = 600.dp

/** Where the tabs lead outside themselves: screens pushed over the tabs. */
interface MainActions : MoreActions {
    fun openReceipt(receiptId: String)

    fun openProduct(productId: String)

    fun scan()

    fun addByHand()

    fun openReview()

    fun openCategory(categoryId: String?, period: Period)
}

/**
 * The tabs with the redesign's docked bottom bar on phones (the round "+" in the middle) and a
 * rail on wider screens ("+" on top). "+" opens the ways to add. Each tab keeps its own saved
 * state (scroll position, search text) while another is shown.
 */
@Composable
fun MainTabs(actions: MainActions) {
    var tab by rememberSaveable { mutableStateOf(Tab.OVERVIEW) }
    // The tabs visited before this one, most recent last: Back returns to them in turn, then to
    // the Overview, and only leaves the app from the Overview.
    var history by rememberSaveable { mutableStateOf(listOf<Tab>()) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var pasting by rememberSaveable { mutableStateOf(false) }
    val select: (Tab) -> Unit = { entry ->
        if (entry != tab) {
            // The Overview starts the history again: Back there always leaves the app.
            history = if (entry == Tab.OVERVIEW) emptyList() else (history - entry) + tab
            tab = entry
        }
    }
    // The Overview's budget alert opens Spending on "This month", whatever period it showed.
    var spendingThisMonth by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = tab != Tab.OVERVIEW) {
        val previous = history.lastOrNull() ?: Tab.OVERVIEW
        history = history.dropLast(1)
        tab = previous
    }
    val tabStates = rememberSaveableStateHolder()
    val content = @Composable { modifier: Modifier ->
        Box(modifier = modifier) {
            tabStates.SaveableStateProvider(tab.name) {
                TabContent(
                    tab = tab,
                    actions = actions,
                    showSpending = { thisMonth ->
                        spendingThisMonth = thisMonth
                        select(Tab.SPENDING)
                    },
                    spendingThisMonth = spendingThisMonth,
                    onThisMonthShown = { spendingThisMonth = false },
                )
            }
        }
    }
    val addLabel = stringResource(R.string.tab_add)
    val add = { adding = true }
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
                    TABS_BEFORE_ADD.forEach { entry ->
                        TabItem(entry, selected = entry == tab) { select(entry) }
                    }
                    AddNavigationBarItem(label = addLabel, onClick = add)
                    TABS_AFTER_ADD.forEach { entry ->
                        TabItem(entry, selected = entry == tab) { select(entry) }
                    }
                }
            }
        } else {
            // The rail pads itself for the start side of the system bars.
            val railInsets = WindowInsets.systemBars.only(WindowInsetsSides.Start)
            Row(modifier = Modifier.fillMaxSize()) {
                EksodaNavigationRail(header = { AddButton(label = addLabel, onClick = add) }) {
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
    if (adding) {
        AddSheet(
            onDismiss = { adding = false },
            onScan = {
                adding = false
                actions.scan()
            },
            onByHand = {
                adding = false
                actions.addByHand()
            },
            onPaste = {
                adding = false
                pasting = true
            },
        )
    }
    if (pasting) {
        PasteLinkImportDialog(
            onDismiss = { pasting = false },
            onViewReceipt = { receiptId ->
                pasting = false
                actions.openReceipt(receiptId)
            },
        )
    }
}

@Composable
private fun TabContent(
    tab: Tab,
    actions: MainActions,
    showSpending: (thisMonth: Boolean) -> Unit,
    spendingThisMonth: Boolean,
    onThisMonthShown: () -> Unit,
) {
    when (tab) {
        Tab.OVERVIEW -> HomeRoute(
            HomeActions(
                onBudgetClick = actions::openBudget,
                onBillsClick = actions::openBills,
                onCategoryClick = actions::openCategory,
                onSpendingClick = { showSpending(true) },
                onReviewClick = actions::openReview,
                onImportsClick = actions::openImports,
            ),
        )

        Tab.SPENDING -> InsightsRoute(
            onProductClick = actions::openProduct,
            onMerchantsClick = actions::openMerchants,
            onBudgetsClick = actions::openBudget,
            onCategoryClick = actions::openCategory,
            showThisMonth = spendingThisMonth,
            onThisMonthShown = onThisMonthShown,
        )

        Tab.PRICES -> ProductListRoute(
            onProductClick = actions::openProduct,
            onReviewClick = actions::openReview,
            onCategoriesClick = actions::openCategories,
            onMerchantsClick = actions::openMerchants,
        )

        Tab.MORE -> MoreScreen(actions)
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
