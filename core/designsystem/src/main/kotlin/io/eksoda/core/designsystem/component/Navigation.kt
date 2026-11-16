package io.eksoda.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.eksoda.core.designsystem.theme.EksodaTheme

/** How far the Scan button rises above the bar (docs/design/ui-redesign.md §3). */
private val SCAN_LIFT = 22.dp
private val SCAN_SIZE = 54.dp
private val ScanShape = RoundedCornerShape(16.dp)
private val IndicatorShape = RoundedCornerShape(8.dp)
private val RAIL_SIDES = WindowInsetsSides.Vertical + WindowInsetsSides.Start

/**
 * The bottom bar on phones (docs/design/ui-redesign.md §3): the panel color with a line on top,
 * the tabs and the raised Scan button. The strip the Scan button rises into is transparent, so
 * the bar takes its height from the layout instead of covering the content.
 */
@Composable
fun EksodaNavigationBar(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    val colors = EksodaTheme.colors
    Box(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.matchParentSize().padding(top = SCAN_LIFT)) {
            HorizontalDivider(color = colors.line)
            Spacer(modifier = Modifier.fillMaxWidth().weight(1f).background(colors.panel))
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
                .padding(start = 6.dp, end = 6.dp, top = 8.dp, bottom = 10.dp)
                .selectableGroup(),
            verticalAlignment = Alignment.Bottom,
            content = content,
        )
    }
}

/** One tab of [EksodaNavigationBar]. */
@Composable
fun RowScope.EksodaNavigationBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
) {
    NavigationItem(selected, onClick, icon, label, Modifier.weight(1f))
}

/** The raised Scan button in the middle of [EksodaNavigationBar]. */
@Composable
fun RowScope.ScanNavigationBarItem(label: String, onClick: () -> Unit) {
    ScanButton(label = label, onClick = onClick, modifier = Modifier.weight(1f))
}

/**
 * The navigation rail on wide screens: the Scan button on top, then the tabs, on the panel color
 * with a line on the content side.
 */
@Composable
fun EksodaNavigationRail(
    modifier: Modifier = Modifier,
    header: @Composable ColumnScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = EksodaTheme.colors
    Row(modifier = modifier.fillMaxHeight()) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(88.dp)
                .background(colors.panel)
                .windowInsetsPadding(WindowInsets.systemBars.only(RAIL_SIDES))
                .padding(vertical = 16.dp)
                .selectableGroup(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            header()
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
        VerticalDivider(color = colors.line)
    }
}

/** One tab of [EksodaNavigationRail]. */
@Composable
fun ColumnScope.EksodaNavigationRailItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
) {
    NavigationItem(selected, onClick, icon, label, Modifier.fillMaxWidth())
}

/**
 * The Scan action: a raised 54 dp terracotta button with a dark icon, and its label in
 * terracotta. Scan is an action, not a tab, so it is never shown as selected.
 */
@Composable
fun ScanButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = EksodaTheme.colors
    Column(
        modifier = modifier.clickable(role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(SCAN_SIZE)
                .shadow(
                    elevation = 8.dp,
                    shape = ScanShape,
                    ambientColor = colors.spend,
                    spotColor = colors.spend,
                )
                .background(colors.spend, ScanShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.QrCodeScanner,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = colors.spend,
            maxLines = 1,
        )
    }
}

/** A tab: the icon in a pill (periwinkle on #1C1E33 when selected) over its label. */
@Composable
private fun NavigationItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
    modifier: Modifier,
) {
    val colors = EksodaTheme.colors
    Column(
        modifier = modifier
            .heightIn(min = 48.dp)
            .selectable(selected = selected, onClick = onClick, role = Role.Tab),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.Bottom),
    ) {
        Box(
            modifier = Modifier
                .size(width = 52.dp, height = 28.dp)
                .background(
                    if (selected) colors.navIndicator else Color.Transparent,
                    IndicatorShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) colors.compareText else colors.ink2,
                modifier = Modifier.size(22.dp),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) colors.ink else colors.ink2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Preview
@Composable
private fun NavigationBarPreview() {
    EksodaTheme {
        Box(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
            EksodaNavigationBar {
                EksodaNavigationBarItem(
                    selected = false,
                    onClick = {},
                    icon = Icons.AutoMirrored.Filled.ReceiptLong,
                    label = "Receipts",
                )
                ScanNavigationBarItem(label = "Scan", onClick = {})
                EksodaNavigationBarItem(
                    selected = true,
                    onClick = {},
                    icon = Icons.Filled.Inventory2,
                    label = "Products",
                )
            }
        }
    }
}

@Preview(widthDp = 120, heightDp = 400)
@Composable
private fun NavigationRailPreview() {
    EksodaTheme {
        EksodaNavigationRail(header = { ScanButton("Scan", {}) }) {
            EksodaNavigationRailItem(true, {}, Icons.AutoMirrored.Filled.ReceiptLong, "Receipts")
            EksodaNavigationRailItem(false, {}, Icons.Outlined.Inventory2, "Products")
        }
    }
}
