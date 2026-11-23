package io.eksoda.core.ui.text

import java.time.YearMonth
import java.util.Locale

/** "November", "Νοέμβριος": a month's standalone name, capitalized. */
fun monthName(month: YearMonth, locale: Locale): String =
    month.format(dateFormatter("LLLL", locale)).replaceFirstChar { it.titlecase(locale) }

/** "Nov", "Νοε": a month's short standalone name, capitalized, without a trailing dot. */
fun shortMonthName(month: YearMonth, locale: Locale): String =
    month.format(dateFormatter("LLL", locale)).trimEnd('.').replaceFirstChar {
        it.titlecase(locale)
    }
