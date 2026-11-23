package io.eksoda.core.designsystem.text

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle

/**
 * A number with its separators, sign, percent or euro sign: "8%", "0,10 €", "€0.10", "−4,50",
 * "1.234,56 €". The no-break spaces are the ones number formats put between a number and €.
 */
private val NUMBER_RUN = Regex(
    "(?:€[\u00A0\u202F ]?)?" +
        "[−+-]?\\d(?:[\\d.,\u00A0\u202F]*\\d)?" +
        "(?:[\u00A0\u202F ]?[%€])?",
)

/**
 * [text] with its numbers in tabular digits (docs/design/ui-redesign.md §2), so figures in
 * sentences such as "Lidl is 0,13 €/l cheaper" or "8% less" line up with the amounts around
 * them. The words keep the surrounding font.
 */
fun monoNumbers(text: String): AnnotatedString = buildAnnotatedString {
    var last = 0
    NUMBER_RUN.findAll(text).forEach { match ->
        append(text.substring(last, match.range.first))
        withStyle(SpanStyle(fontFeatureSettings = "tnum")) { append(match.value) }
        last = match.range.last + 1
    }
    append(text.substring(last))
}
