package io.eksoda.core.ui.component

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import io.eksoda.core.designsystem.theme.tabular
import io.eksoda.core.model.Money
import io.eksoda.core.ui.text.appLocale
import io.eksoda.core.ui.text.formatMoney

/**
 * An amount in the app locale with tabular figures (android-app.md §7.4). [negative]: credit
 * notes and discounts, shown with a true minus sign.
 */
@Composable
fun MoneyText(
    money: Money,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    negative: Boolean = false,
    style: TextStyle = LocalTextStyle.current,
    textDecoration: TextDecoration? = null,
) {
    val locale = appLocale()
    val text = remember(money, locale, negative) { formatMoney(money, locale, negative) }
    Text(
        text = text,
        modifier = modifier,
        color = color,
        style = style.tabular(),
        fontWeight = fontWeight ?: FontWeight.Bold,
        textDecoration = textDecoration,
        maxLines = 1,
    )
}
