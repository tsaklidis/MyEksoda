package io.eksoda.feature.products.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import io.eksoda.core.model.Money
import io.eksoda.core.model.Product
import io.eksoda.core.model.MerchantPrices
import io.eksoda.core.model.PriceComparison
import io.eksoda.core.model.PriceEntry
import io.eksoda.core.ui.component.MoneyText
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductDetailScreen(
    onBackClick: () -> Unit,
    onEditClick: (String) -> Unit,
    onReceiptClick: (String) -> Unit,
    viewModel: ProductDetailViewModel = hiltViewModel()
) {
    val uiState = viewModel.uiState

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.product?.name ?: "Product Detail") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { uiState.product?.id?.let(onEditClick) }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                    }
                    IconButton(onClick = { /* More actions: Merge */ }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (uiState.isLoading && uiState.product == null) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (uiState.product != null) {
                ProductContent(
                    product = uiState.product!!,
                    prices = uiState.prices,
                    comparison = uiState.comparison,
                    onReceiptClick = onReceiptClick
                )
            } else if (uiState.error != null) {
                Text(
                    text = uiState.error!!,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}

@Composable
fun ProductContent(
    product: Product,
    prices: List<PriceEntry>,
    comparison: PriceComparison?,
    onReceiptClick: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp)
    ) {
        item {
            ProductHeader(product, lastPurchase = prices.firstOrNull())
            Spacer(modifier = Modifier.height(24.dp))
        }

        if (comparison != null && comparison.comparable) {
            item {
                Text(
                    text = "Where it's cheapest",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                ComparisonCard(comparison)
                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        item {
            Text(
                text = "Purchase History",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        items(prices) { price ->
            PriceHistoryRow(
                price = price,
                onClick = { onReceiptClick(price.receiptId) }
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        }
    }
}

@Composable
fun ProductHeader(product: Product, lastPurchase: PriceEntry?) {
    val packageSize = product.packageQuantity?.let { quantity ->
        "${quantity.stripTrailingZeros().toPlainString()} ${product.packageUnit.orEmpty()}".trim()
    }
    Column {
        Text(
            text = product.name,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = listOfNotNull(product.brand, product.category?.name, packageSize).joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (lastPurchase != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Last paid", style = MaterialTheme.typography.labelSmall)
                    MoneyText(money = lastPurchase.grossAmount)
                }
            }
        }
    }
}

@Composable
fun ComparisonCard(comparison: PriceComparison) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            comparison.merchants.forEach { row ->
                MerchantComparisonRow(row, comparison.normalizedUnit)
            }
        }
    }
}

@Composable
fun MerchantComparisonRow(row: MerchantPrices, normalizedUnit: String?) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = row.rank?.toString() ?: "–",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.width(24.dp)
            )
            Text(text = row.merchant.name, style = MaterialTheme.typography.bodyLarge)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Average price per normalized unit (e.g. per kg), quantity-weighted by the server.
            MoneyText(money = Money(row.stats.average, row.stats.currency))
            if (normalizedUnit != null) {
                Text(
                    text = " / $normalizedUnit",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun PriceHistoryRow(price: PriceEntry, onClick: () -> Unit) {
    val dateFormatter = DateTimeFormatter.ofPattern("MMM dd, yyyy")
    val date = price.issuedAt
    val quantity = price.quantity?.stripTrailingZeros()?.toPlainString()

    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = {
            Text(text = price.merchant.name)
        },
        supportingContent = {
            Text(
                text = listOfNotNull(
                    date.format(dateFormatter),
                    quantity?.let { "$it ${price.unit ?: "x"}" }
                ).joinToString(" · ")
            )
        },
        trailingContent = {
            MoneyText(money = price.grossAmount)
        }
    )
}
