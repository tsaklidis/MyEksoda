package io.eksoda.feature.settings.more

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.eksoda.core.designsystem.component.RowIcon
import io.eksoda.core.designsystem.component.SectionCard
import io.eksoda.core.designsystem.theme.EksodaTheme
import io.eksoda.feature.settings.R

private class Entry(
    val label: Int,
    val supporting: Int,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

/**
 * The More tab (docs/design/ui-redesign.md §5): what is not needed every day, in three groups.
 * Your money (receipts, budget, bills), what Eksoda knows (products, categories, shops), and the
 * app (imports, household sync, settings).
 */
@Composable
fun MoreScreen(actions: MoreActions) {
    val colors = EksodaTheme.colors
    val groups = listOf(
        R.string.more_money to listOf(
            Entry(
                R.string.more_receipts,
                R.string.more_receipts_hint,
                Icons.AutoMirrored.Outlined.ReceiptLong,
                actions::openReceipts,
            ),
            Entry(
                R.string.more_budget,
                R.string.more_budget_hint,
                Icons.Outlined.Savings,
                actions::openBudget,
            ),
            Entry(
                R.string.more_bills,
                R.string.more_bills_hint,
                Icons.Outlined.Home,
                actions::openBills,
            ),
        ),
        R.string.more_catalog to listOf(
            Entry(
                R.string.more_products,
                R.string.more_products_hint,
                Icons.Outlined.Inventory2,
                actions::openProducts,
            ),
            Entry(
                R.string.more_categories,
                R.string.more_categories_hint,
                Icons.Outlined.Category,
                actions::openCategories,
            ),
            Entry(
                R.string.more_merchants,
                R.string.more_merchants_hint,
                Icons.Outlined.Storefront,
                actions::openMerchants,
            ),
        ),
        R.string.more_app to listOf(
            Entry(
                R.string.more_imports,
                R.string.more_imports_hint,
                Icons.Outlined.CloudDownload,
                actions::openImports,
            ),
            Entry(
                R.string.more_sync,
                R.string.more_sync_hint,
                Icons.Outlined.Sync,
                actions::openSync,
            ),
            Entry(
                R.string.more_settings,
                R.string.more_settings_hint,
                Icons.Outlined.Settings,
                actions::openSettings,
            ),
        ),
    )
    // Without a Scaffold, text would draw black on the dark theme (OVERVIEW.md, UI pattern).
    CompositionLocalProvider(LocalContentColor provides colors.ink) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                stringResource(R.string.more_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp).semantics {
                    heading()
                },
            )
            groups.forEach { (title, entries) ->
                SectionCard(title = stringResource(title)) {
                    entries.forEachIndexed { index, entry ->
                        if (index > 0) HorizontalDivider(color = colors.line)
                        MoreRow(entry)
                    }
                }
            }
        }
    }
}

@Composable
private fun MoreRow(entry: Entry) {
    val colors = EksodaTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = entry.onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        RowIcon(entry.icon)
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(entry.label), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(entry.supporting),
                style = MaterialTheme.typography.bodySmall,
                color = colors.ink2,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = colors.ink2,
        )
    }
}

@Preview
@Composable
private fun MoreScreenPreview() {
    EksodaTheme {
        MoreScreen(object : MoreActions {})
    }
}
