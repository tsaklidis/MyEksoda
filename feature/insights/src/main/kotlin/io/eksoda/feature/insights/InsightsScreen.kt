package io.eksoda.feature.insights

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import io.eksoda.core.model.Money
import io.eksoda.core.model.Period
import io.eksoda.core.designsystem.chart.percentToFraction
import io.eksoda.core.model.CategorySpending
import io.eksoda.core.model.SpendingBucket
import io.eksoda.core.model.SpendingSummary
import io.eksoda.core.ui.component.MoneyText
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsScreen(
    onNavigateToReceipts: (String?, Period) -> Unit,
    onProductClick: (String) -> Unit,
    viewModel: InsightsViewModel = hiltViewModel()
) {
    val uiState = viewModel.uiState

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Insights") },
                actions = {
                    IconButton(onClick = { /* TODO: Show Period Picker */ }) {
                        Icon(Icons.Default.DateRange, contentDescription = "Select Period")
                    }
                }
            )
        }
    ) { padding ->
        if (uiState.isLoading && uiState.summary == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                uiState.summary?.let { summary ->
                    SummaryCard(summary = summary)
                    Spacer(modifier = Modifier.height(24.dp))
                }

                uiState.categorySpending?.let { spending ->
                    CategorySpendingCard(
                        spending = spending,
                        onCategoryClick = { id -> onNavigateToReceipts(id, uiState.selectedPeriod) }
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                }

                // TODO: Merchant Spending Card
                // TODO: Monthly Trend Card
                // TODO: Top Products Card

                if (uiState.hasError) {
                    Text(
                        text = "Could not load some insights.",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                    Button(onClick = viewModel::loadInsights) {
                        Text("Retry")
                    }
                }
            }
        }
    }
}

@Composable
fun SummaryCard(summary: SpendingSummary) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Summary",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatItem("Spending", summary.current.spending.toPlainString(), summary.currency)
                StatItem("Receipts", summary.current.receiptCount.toString(), "")
            }
            
            val changePercent = summary.changePercent
            if (changePercent != null) {
                val isIncrease = changePercent.signum() > 0
                Text(
                    text = "${if (isIncrease) "Up" else "Down"} ${changePercent.abs().toPlainString()}% from last period",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isIncrease) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
fun StatItem(label: String, value: String, currency: String) {
    Column {
        Text(text = label, style = MaterialTheme.typography.labelSmall)
        if (currency.isNotEmpty()) {
            MoneyText(money = Money.fromString(value, currency))
        } else {
            Text(text = value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun CategorySpendingCard(
    spending: CategorySpending,
    onCategoryClick: (String?) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "By Category",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            // Simplified list for now instead of a donut chart
            spending.buckets.forEach { bucket ->
                CategoryRow(
                    bucket = bucket,
                    currency = spending.currency,
                    onClick = { onCategoryClick(bucket.categoryId) }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun CategoryRow(bucket: SpendingBucket, currency: String, onClick: () -> Unit) {
    val share = bucket.share
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = bucket.name, style = MaterialTheme.typography.bodyLarge)
            LinearProgressIndicator(
                progress = { percentToFraction(share) },
                modifier = Modifier.fillMaxWidth(0.6f).height(4.dp)
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            MoneyText(money = Money(bucket.total, currency))
            if (share != null) {
                Text(text = "${share.toPlainString()}%", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
