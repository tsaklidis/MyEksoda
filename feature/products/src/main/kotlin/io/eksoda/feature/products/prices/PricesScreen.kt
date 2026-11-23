package io.eksoda.feature.products.prices

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.eksoda.core.designsystem.component.ChangeBadge
import io.eksoda.core.designsystem.component.ChangeDirection
import io.eksoda.core.designsystem.component.MerchantAvatar
import io.eksoda.core.designsystem.component.SectionCard
import io.eksoda.core.designsystem.text.monoNumbers
import io.eksoda.core.designsystem.theme.EksodaTheme
import io.eksoda.core.model.Money
import io.eksoda.core.model.PriceBoard
import io.eksoda.core.model.ProductDeal
import io.eksoda.core.model.ProductTrend
import io.eksoda.core.model.ShopDeals
import io.eksoda.core.model.ShopTrend
import io.eksoda.core.ui.component.DelayedProgressIndicator
import io.eksoda.core.ui.component.EmptyState
import io.eksoda.core.ui.text.appLocale
import io.eksoda.core.ui.text.formatMoney
import io.eksoda.core.ui.text.formatPercent
import io.eksoda.feature.products.R
import io.eksoda.feature.products.common.unitLabel
import java.math.BigDecimal

private enum class View(val label: Int) {
    BY_PRODUCT(R.string.prices_by_product),
    BY_SHOP(R.string.prices_by_shop),
    TRENDS(R.string.prices_trends),
}

@Composable
fun PricesRoute(
    onProductClick: (String) -> Unit,
    onAllProductsClick: () -> Unit,
    viewModel: PricesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // No Scaffold here: the text color is set by hand.
    CompositionLocalProvider(LocalContentColor provides EksodaTheme.colors.ink) {
        PricesScreen(state, onProductClick, onAllProductsClick)
    }
}

/**
 * Prices (docs/design/ui-redesign.md §5), from the household's own receipts: by product (where
 * each costs least), by shop (what each is cheapest for, which scales to any number of shops),
 * and trends (how prices moved over the last month).
 */
@Composable
fun PricesScreen(
    state: PricesUiState,
    onProductClick: (String) -> Unit,
    onAllProductsClick: () -> Unit,
) {
    var view by rememberSaveable { mutableStateOf(View.BY_PRODUCT) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.prices_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            TextButton(onClick = onAllProductsClick) {
                Text(
                    stringResource(R.string.prices_all_products),
                    color = EksodaTheme.colors.compareText,
                )
            }
        }
        when (state) {
            PricesUiState.Loading -> Box(Modifier.fillMaxWidth().padding(48.dp), Alignment.Center) {
                DelayedProgressIndicator()
            }

            PricesUiState.Failed -> Text(
                stringResource(R.string.prices_failed),
                style = MaterialTheme.typography.bodyMedium,
                color = EksodaTheme.colors.ink2,
                modifier = Modifier.padding(20.dp),
            )

            is PricesUiState.Loaded -> Loaded(state.board, view, { view = it }, onProductClick)
        }
    }
}

@Composable
private fun Loaded(
    board: PriceBoard,
    view: View,
    onView: (View) -> Unit,
    onProductClick: (String) -> Unit,
) {
    val colors = EksodaTheme.colors
    val locale = appLocale()
    val currency = board.currency.orEmpty()
    Column(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (board.saving.signum() > 0) {
            Text(
                monoNumbers(
                    stringResource(
                        R.string.prices_story,
                        formatMoney(Money(board.saving, currency), locale),
                    ),
                ),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Text(
            pluralStringResource(R.plurals.prices_source, board.shops, board.shops),
            style = MaterialTheme.typography.bodySmall,
            color = colors.ink2,
        )
    }
    Tabs(view, onView)
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (view) {
            View.BY_PRODUCT -> if (board.byProduct.isEmpty()) {
                Empty()
            } else {
                SectionCard {
                    board.byProduct.forEachIndexed { index, deal ->
                        if (index > 0) HorizontalDivider(color = colors.line)
                        DealRow(deal, currency) { onProductClick(deal.productId) }
                    }
                }
            }

            View.BY_SHOP -> if (board.byShop.isEmpty()) {
                Empty()
            } else {
                board.byShop.forEach { shop -> ShopCard(shop, currency, onProductClick) }
            }

            View.TRENDS -> Trends(board, currency, onProductClick)
        }
    }
}

@Composable
private fun Empty() {
    EmptyState(
        title = stringResource(R.string.prices_empty_title),
        description = stringResource(R.string.prices_empty_body),
        icon = Icons.AutoMirrored.Outlined.ReceiptLong,
    )
}

/** By product · By shop · Trends as text tabs with an underline (§1: no boxes). */
@Composable
private fun Tabs(selected: View, onSelect: (View) -> Unit) {
    val colors = EksodaTheme.colors
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).selectableGroup()) {
        View.entries.forEach { view ->
            val on = view == selected
            Column(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .selectable(selected = on, role = Role.Tab, onClick = { onSelect(view) }),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
            ) {
                Text(
                    stringResource(view.label),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                    color = if (on) colors.ink else colors.ink2,
                    maxLines = 1,
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 3.dp)
                        .background(if (on) colors.ink else colors.line),
                )
            }
        }
    }
}

/** "Olive oil · cheapest at LIDL 6,90 €/l, AB 8,27 €/l" and what buying it there saves. */
@Composable
private fun DealRow(deal: ProductDeal, currency: String, onClick: () -> Unit) {
    val colors = EksodaTheme.colors
    val locale = appLocale()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MerchantAvatar(
            deal.cheapest.merchant.id,
            deal.cheapest.merchant.name,
            vatNumber = deal.cheapest.merchant.vatNumber,
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                deal.productName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                monoNumbers(
                    stringResource(
                        R.string.prices_cheapest_at,
                        deal.cheapest.merchant.name,
                        perUnit(deal.cheapest.average, currency, deal.unit),
                    ),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = colors.compareText,
            )
            deal.others.forEach { other ->
                Text(
                    monoNumbers(
                        other.merchant.name + " " + perUnit(other.average, currency, deal.unit),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.ink2,
                )
            }
        }
        if (deal.saving.signum() > 0) {
            Text(
                monoNumbers(
                    stringResource(
                        R.string.prices_save,
                        formatMoney(Money(deal.saving, currency), locale),
                    ),
                ),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/** A shop and what it is cheapest for. */
@Composable
private fun ShopCard(shop: ShopDeals, currency: String, onProductClick: (String) -> Unit) {
    val colors = EksodaTheme.colors
    val locale = appLocale()
    SectionCard {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MerchantAvatar(
                shop.merchant.id,
                shop.merchant.name,
                vatNumber = shop.merchant.vatNumber,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(shop.merchant.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    monoNumbers(
                        pluralStringResource(
                            R.plurals.prices_shop_cheapest,
                            shop.products.size,
                            shop.products.size,
                            formatMoney(Money(shop.saving, currency), locale),
                        ),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.ink2,
                )
            }
        }
        shop.products.forEach { deal ->
            HorizontalDivider(color = colors.line)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onProductClick(deal.productId) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    deal.productName,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    monoNumbers(perUnit(deal.cheapest.average, currency, deal.unit)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.compareText,
                )
            }
        }
    }
}

/** How each shop's prices moved, then each product's: the last 30 days against the 30 before. */
@Composable
private fun Trends(board: PriceBoard, currency: String, onProductClick: (String) -> Unit) {
    val colors = EksodaTheme.colors
    if (board.productTrends.isEmpty()) {
        EmptyState(
            title = stringResource(R.string.prices_trends_empty_title),
            description = stringResource(R.string.prices_trends_empty_body),
            icon = Icons.AutoMirrored.Outlined.ReceiptLong,
        )
        return
    }
    SectionCard(title = stringResource(R.string.prices_trends_shops)) {
        board.shopTrends.forEachIndexed { index, trend ->
            if (index > 0) HorizontalDivider(color = colors.line)
            ShopTrendRow(trend)
        }
    }
    SectionCard(title = stringResource(R.string.prices_trends_products)) {
        board.productTrends.forEachIndexed { index, trend ->
            if (index > 0) HorizontalDivider(color = colors.line)
            ProductTrendRow(trend, currency) { onProductClick(trend.productId) }
        }
    }
}

@Composable
private fun ShopTrendRow(trend: ShopTrend) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MerchantAvatar(trend.merchant.id, trend.merchant.name, vatNumber = trend.merchant.vatNumber)
        Column(modifier = Modifier.weight(1f)) {
            Text(trend.merchant.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                pluralStringResource(
                    R.plurals.prices_trend_products,
                    trend.products,
                    trend.products,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = EksodaTheme.colors.ink2,
            )
        }
        Change(trend.changePercent)
    }
}

@Composable
private fun ProductTrendRow(trend: ProductTrend, currency: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(trend.productName, style = MaterialTheme.typography.bodyLarge)
            Text(
                monoNumbers(
                    stringResource(
                        R.string.prices_trend_from_to,
                        perUnit(trend.previous, currency, trend.unit),
                        perUnit(trend.latest, currency, trend.unit),
                    ),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = EksodaTheme.colors.ink2,
            )
        }
        Change(trend.changePercent)
    }
}

/** "6,90 €/l": an average unit price. */
@Composable
private fun perUnit(amount: BigDecimal, currency: String, unit: String): String = stringResource(
    R.string.prices_per_unit,
    formatMoney(Money(amount, currency), appLocale()),
    unitLabel(unit),
)

/** "▲ 4,2%" up (pricier, ink) or "▼ 3%" down (cheaper, blue); "—" unchanged (§1). */
@Composable
private fun Change(percent: BigDecimal) {
    val locale = appLocale()
    val text = formatPercent(percent.abs(), locale)
    when (percent.signum()) {
        1 -> ChangeBadge(ChangeDirection.UP, text)
        -1 -> ChangeBadge(ChangeDirection.DOWN, text)
        else -> ChangeBadge(ChangeDirection.NONE, "")
    }
}
