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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.eksoda.core.designsystem.theme.EksodaTheme
import io.eksoda.core.designsystem.theme.PillShape

private val ADD_SIZE = 52.dp
private val AddShape = CircleShape
private val IndicatorShape = PillShape
private val RAIL_SIDES = WindowInsetsSides.Vertical + WindowInsetsSides.Start

/**
 * The bottom bar on phones (docs/design/ui-redesign.md §3): docked on the panel color with a line
 * on top, the tabs and the round Add button in the middle. Nothing is drawn under it.
 */
@Composable
fun EksodaNavigationBar(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    val colors = EksodaTheme.colors
    Column(modifier = modifier.fillMaxWidth().background(colors.panel)) {
        HorizontalDivider(color = colors.line)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
                .padding(start = 6.dp, end = 6.dp, top = 8.dp, bottom = 8.dp)
                .selectableGroup(),
            verticalAlignment = Alignment.CenterVertically,
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

/** The round Add button in the middle of [EksodaNavigationBar]; [label] is read by TalkBack. */
@Composable
fun RowScope.AddNavigationBarItem(label: String, onClick: () -> Unit) {
    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
        AddButton(label = label, onClick = onClick)
    }
}

/**
 * The navigation rail on wide screens: the Add button on top, then the tabs, on the panel color
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
 * The Add action: a 52 dp ink circle with a plus. It opens the ways to add (scan, by hand, a
 * link). An action, not a tab, so it is never shown as selected; [label] is its description.
 */
@Composable
fun AddButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = EksodaTheme.colors
    Box(
        modifier = modifier
            .size(ADD_SIZE)
            .clip(AddShape)
            .background(colors.ink)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = null,
            tint = colors.page,
            modifier = Modifier.size(26.dp),
        )
    }
}

/** A tab: the icon in a pill (on the indicator color when selected) over its label. */
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
                tint = if (selected) colors.ink else colors.ink2,
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
                AddNavigationBarItem(label = "Add", onClick = {})
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
        EksodaNavigationRail(header = { AddButton("Add", {}) }) {
            EksodaNavigationRailItem(true, {}, Icons.AutoMirrored.Filled.ReceiptLong, "Receipts")
            EksodaNavigationRailItem(false, {}, Icons.Outlined.Inventory2, "Products")
        }
    }
}
