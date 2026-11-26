package io.eksoda.core.ui.text

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import io.eksoda.core.model.Money
import io.eksoda.core.ui.R
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Currency
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

private const val MINUS_SIGN = '−'
private const val DAYS_IN_A_WEEK = 7L
private const val MAX_QUANTITY_DECIMALS = 3

/** The app's locale, for numbers and dates. */
@Composable
@ReadOnlyComposable
fun appLocale(): Locale = LocalConfiguration.current.locales[0]

/**
 * Number formats are slow to create and not thread-safe, so each thread keeps one per locale
 * and currency. Lists format an amount for every row they show.
 */
private val currencyFormats: ThreadLocal<HashMap<Pair<Locale, String>, NumberFormat>> =
    ThreadLocal.withInitial { HashMap() }
private val quantityFormats: ThreadLocal<HashMap<Locale, NumberFormat>> =
    ThreadLocal.withInitial { HashMap() }
private val amountFormats: ThreadLocal<HashMap<Locale, NumberFormat>> =
    ThreadLocal.withInitial { HashMap() }

/** Date formatters are immutable and thread-safe: one per pattern and locale. */
private val dateFormatters = ConcurrentHashMap<Pair<String, Locale>, DateTimeFormatter>()

/** A cached `DateTimeFormatter.ofPattern(pattern, locale)`. */
fun dateFormatter(pattern: String, locale: Locale): DateTimeFormatter =
    dateFormatters.getOrPut(pattern to locale) { DateTimeFormatter.ofPattern(pattern, locale) }

/**
 * `12,40 €` in Greek, `€12.40` in English. [negative] shows credit notes and discounts with a
 * true minus sign. BigDecimal throughout, never Double. A unit price with more places rounds
 * half up, as the price statistics do (9,945 → 9,95), so every screen shows the same cents.
 */
fun formatMoney(money: Money, locale: Locale, negative: Boolean = false): String {
    val format = currencyFormats.get().getOrPut(locale to money.currency) {
        NumberFormat.getCurrencyInstance(locale).also { created ->
            runCatching { Currency.getInstance(money.currency) }.getOrNull()
                ?.let { created.currency = it }
            created.roundingMode = RoundingMode.HALF_UP
        }
    }
    val amount = if (negative) money.amount.abs().negate() else money.amount
    return format.format(amount).replace('-', MINUS_SIGN)
}

/**
 * An amount without its currency sign, "342,60" in Greek and "342.60" in English, for a hero
 * number with the currency beside it (docs/design/ui-redesign.md §2). A true minus sign.
 */
fun formatAmount(amount: BigDecimal, locale: Locale): String {
    val format = amountFormats.get().getOrPut(locale) {
        NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
            roundingMode = RoundingMode.HALF_UP
        }
    }
    return format.format(amount).replace('-', MINUS_SIGN)
}

/**
 * A percentage for the locale with [decimals] places, without a sign: "18,7%" in Greek, "18.7%"
 * in English. The caller says "more" or "less" in words.
 */
fun formatPercent(percent: BigDecimal, locale: Locale, decimals: Int = 1): String {
    val format = NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = decimals
        maximumFractionDigits = decimals
        roundingMode = RoundingMode.HALF_UP
    }
    return format.format(percent.abs()) + "%"
}

/** The currency's sign in the locale: "€" for EUR. */
fun currencySymbol(currency: String, locale: Locale): String =
    runCatching { Currency.getInstance(currency).getSymbol(locale) }.getOrDefault(currency)

/** Up to 3 decimals for kg and l, none for whole pieces. */
fun formatQuantity(quantity: BigDecimal, locale: Locale): String {
    val format = quantityFormats.get().getOrPut(locale) {
        NumberFormat.getNumberInstance(locale).apply {
            maximumFractionDigits = MAX_QUANTITY_DECIMALS
            minimumFractionDigits = 0
        }
    }
    return format.format(quantity)
}

/** A plain decimal for editing (no grouping, the locale's decimal separator). */
fun formatDecimalInput(value: BigDecimal, locale: Locale): String {
    val format = NumberFormat.getNumberInstance(locale)
    format.isGroupingUsed = false
    format.maximumFractionDigits = 2
    return format.format(value)
}

/** Parses an amount typed with either decimal separator; null when it is not a number. */
fun parseDecimalInput(text: String): BigDecimal? =
    text.trim().replace(" ", "").replace(',', '.').takeIf { it.isNotEmpty() }
        ?.let { runCatching { BigDecimal(it) }.getOrNull() }
        ?.takeIf { it.signum() >= 0 }

private const val MONEY_DECIMALS = 2
private const val GROUP_DIGITS = 3

/**
 * Parses a money amount as people type it, with thousands separators in either convention:
 * "18.000" and "18,000" are 18000, "18.000,50" and "18,000.50" are 18000.50, "12,5" is 12.50.
 * With both separators the last one is the decimal point. A single separator before exactly three
 * digits groups thousands (money never has three decimals); any other is the decimal point.
 * Null when it is not a non-negative amount with at most two decimals.
 */
fun parseMoneyInput(text: String): BigDecimal? {
    val value = text.filterNot { it.isWhitespace() }
    if (value.isEmpty() || !value.all(::isAmountChar)) return null
    val point = decimalPointIndex(value)
    val whole = if (point < 0) value else value.substring(0, point)
    // After the last separator of either kind, so digits only.
    val fraction = if (point < 0) "" else value.substring(point + 1)
    if (!isGrouped(whole) || fraction.length > MONEY_DECIMALS) return null
    val digits = whole.filter(Char::isDigit)
    return BigDecimal(if (fraction.isEmpty()) digits else "$digits.$fraction")
}

private fun isAmountChar(char: Char): Boolean = char.isDigit() || char == '.' || char == ','

/** Where the decimal point of a typed amount is, or -1 when it has none (see [parseMoneyInput]). */
private fun decimalPointIndex(value: String): Int {
    if ('.' in value && ',' in value) return maxOf(value.lastIndexOf('.'), value.lastIndexOf(','))
    val separator = value.firstOrNull { it == '.' || it == ',' } ?: return -1
    val index = value.lastIndexOf(separator)
    val single = value.indexOf(separator) == index
    return if (single && value.length - index - 1 != GROUP_DIGITS) index else -1
}

/**
 * Plain digits, or one to three digits (not starting with 0) and then groups of exactly three:
 * "18000" and "18.000" are, "12,345,6", "25000.000" and "0,500" are not.
 */
private fun isGrouped(whole: String): Boolean {
    val groups = whole.split('.', ',')
    val lead = groups.first()
    if (groups.size == 1) return lead.isNotEmpty()
    val leadFits = lead.length in 1..GROUP_DIGITS && !lead.startsWith('0')
    return leadFits && groups.drop(1).all { it.length == GROUP_DIGITS }
}

/**
 * "Today", "Yesterday", a weekday within the last week, then "11 Nov", and "11 Nov 2025" for
 * other years.
 */
@Composable
fun dayLabel(date: LocalDate, today: LocalDate): String {
    val locale = appLocale()
    return when {
        date == today -> stringResource(R.string.day_today)

        date == today.minusDays(1) -> stringResource(R.string.day_yesterday)

        date.isAfter(today.minusDays(DAYS_IN_A_WEEK)) && date.isBefore(today) ->
            date.dayOfWeek.getDisplayName(TextStyle.FULL_STANDALONE, locale)
                .replaceFirstChar { it.titlecase(locale) }

        date.year == today.year -> date.format(dateFormatter("d MMM", locale))

        else -> date.format(dateFormatter("d MMM yyyy", locale))
    }
}
