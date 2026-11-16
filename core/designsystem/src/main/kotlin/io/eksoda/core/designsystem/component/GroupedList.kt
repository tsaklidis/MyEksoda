package io.eksoda.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import io.eksoda.core.designsystem.theme.EksodaTheme

private val OUTER_CORNER = 16.dp
private val INNER_CORNER = 4.dp

/**
 * Rows of one group drawn as a single rounded block with small gaps between rows: the first
 * row has large top corners, the last large bottom corners.
 */
fun groupedItemShape(index: Int, count: Int): Shape {
    val top = if (index == 0) OUTER_CORNER else INNER_CORNER
    val bottom = if (index == count - 1) OUTER_CORNER else INNER_CORNER
    return RoundedCornerShape(
        topStart = top,
        topEnd = top,
        bottomStart = bottom,
        bottomEnd = bottom,
    )
}

/** Clips a row to its place in a group and gives it the panel color of the redesign's cards. */
@Composable
fun Modifier.groupedItem(index: Int, count: Int): Modifier {
    val shape = groupedItemShape(index, count)
    return clip(shape).background(EksodaTheme.colors.panel, shape)
}
