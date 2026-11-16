package io.eksoda.core.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.eksoda.core.designsystem.component.HeroAmount
import io.eksoda.core.designsystem.theme.EksodaTheme
import io.eksoda.core.model.Money
import io.eksoda.core.ui.text.appLocale
import io.eksoda.core.ui.text.currencySymbol
import io.eksoda.core.ui.text.formatAmount

/**
 * A hero amount: "342,60" in IBM Plex Mono with "€" (or [unit], "€ / l") after it, smaller
 * (docs/design/ui-redesign.md §3). Terracotta for spending; periwinkle for a unit price.
 */
@Composable
fun HeroMoney(
    money: Money,
    modifier: Modifier = Modifier,
    unit: String? = null,
    color: Color = EksodaTheme.colors.spend,
) {
    val locale = appLocale()
    val number = remember(money, locale) { formatAmount(money.amount, locale) }
    val sign = unit ?: currencySymbol(money.currency, locale)
    HeroAmount(number = number, unit = sign, modifier = modifier, color = color)
}
