package io.eksoda.engine.text

import java.math.BigDecimal
import java.time.DateTimeException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.Locale

// Strict, culture-aware parsing of numbers and dates.
//
// Receipt pages render numbers and dates according to a culture (`el` or `en`). Parsing is
// strict per culture: ambiguous or malformed text raises instead of being guessed, because a
// misread amount silently corrupts financial history.

/** The cultures receipt pages are rendered in (codes "el" and "en"). */
public enum class Culture(public val code: String) {
    EL("el"),
    EN("en"),
    ;

    public companion object {
        /** The culture for [code], or null when it is not one of the known cultures. */
        public fun fromCode(code: String): Culture? = entries.firstOrNull { it.code == code }
    }
}

/** The order of day, month and year in a printed date. */
public enum class DateOrder {
    DMY,
    MDY,
    YMD,
}

/** `NumberFormatError(ValueError)`. */
public class NumberFormatError(message: String) : IllegalArgumentException(message)

/** `DateFormatError(ValueError)`. */
public class DateFormatError(message: String, cause: Throwable? = null) :
    IllegalArgumentException(message, cause)

/** culture → (thousands separator, decimal separator) */
private val SEPARATORS: Map<Culture, Pair<Char, Char>> =
    mapOf(Culture.EL to ('.' to ','), Culture.EN to (',' to '.'))

private val NUMBER_RE: Map<Culture, Regex> = SEPARATORS.mapValues { (_, separators) ->
    val (thousands, decimal) = separators
    pyRegex(
        """^(?:\d{1,3}(?:${Regex.escape(
            thousands.toString(),
        )}\d{3})+|\d+)(?:${Regex.escape(decimal.toString())}\d+)?$""",
    )
}
private const val MINUS_SIGNS = "-−"

private val DATE_RE = pyRegex("""^(\d{1,4})[/.-](\d{1,2})[/.-](\d{1,4})$""")
private val DATETIME_RE = pyRegex(
    """^(?<date>\d{1,4}[/.-]\d{1,2}[/.-]\d{1,4})\s+(?<hour>\d{1,2}):(?<minute>\d{2})""" +
        """(?::(?<second>\d{2}))?(?:\s*(?<marker>\S+))?$""",
)

private enum class Meridiem { AM, PM }

private val MERIDIEM: Map<Culture, Map<String, Meridiem>> = mapOf(
    Culture.EL to
        mapOf(
            "π.μ." to Meridiem.AM,
            "μ.μ." to Meridiem.PM,
            "πμ" to Meridiem.AM,
            "μμ" to Meridiem.PM,
        ),
    Culture.EN to
        mapOf(
            "am" to Meridiem.AM,
            "pm" to Meridiem.PM,
            "a.m." to Meridiem.AM,
            "p.m." to Meridiem.PM,
        ),
)

private const val YEAR_DIGITS = 4
private const val MAX_PART_DIGITS = 2
private const val HOURS_PER_HALF_DAY = 12

// Pages may use (narrow) no-break spaces, e.g. before "π.μ." or "AM".
private fun clean(text: String): String = text.replace(' ', ' ').replace(' ', ' ').pyStrip()

/** A quoted string for error messages. */
private fun repr(text: String): String = "'" + text.replace("\\", "\\\\").replace("'", "\\'") + "'"

/**
 * Parses a number rendered in [culture]. A trailing `%` is allowed and ignored.
 *
 * [parseDecimal] with a raw culture code rejects unknown codes.
 */
public fun parseDecimal(text: String, culture: Culture): BigDecimal {
    var value = clean(text)
    if (value.endsWith("%")) value = value.dropLast(1).pyRstrip()
    val negative = value.isNotEmpty() && value[0] in MINUS_SIGNS
    if (negative) value = value.substring(1).pyLstrip()
    if (!NUMBER_RE.getValue(culture).matches(value)) {
        throw NumberFormatError("${repr(text)} is not a valid ${culture.code} number")
    }
    val (thousands, decimal) = SEPARATORS.getValue(culture)
    val number = BigDecimal(value.replace(thousands.toString(), "").replace(decimal, '.'))
    return if (negative) number.negate() else number
}

/** [parseDecimal] for a culture given as its code ("el", "en"). */
public fun parseDecimal(text: String, culture: String): BigDecimal {
    val known =
        Culture.fromCode(culture) ?: throw NumberFormatError("unknown culture ${repr(culture)}")
    return parseDecimal(text, known)
}

/** Like [parseDecimal], but blank text means "not provided" (null). */
public fun parseOptionalDecimal(text: String?, culture: Culture): BigDecimal? {
    if (text == null || clean(text).isEmpty()) return null
    return parseDecimal(text, culture)
}

/** Parses a date in the given part order; separators may be "/", "-" or ".". */
public fun parseDate(text: String, order: DateOrder): LocalDate {
    val match =
        DATE_RE.matchEntire(clean(text)) ?: throw DateFormatError("${repr(text)} is not a date")
    val parts = match.groupValues.drop(1)
    val yearIndex = if (order == DateOrder.YMD) 0 else 2
    val wrongWidths = parts.withIndex().any { (index, part) ->
        index != yearIndex &&
            part.pyLen() > MAX_PART_DIGITS
    }
    if (parts[yearIndex].pyLen() != YEAR_DIGITS || wrongWidths) {
        throw DateFormatError(
            "${repr(text)} does not match the ${order.name.lowercase(Locale.ROOT)} order",
        )
    }
    val (first, second, third) = parts.map { it.toInt() }
    val (year, month, day) = when (order) {
        DateOrder.YMD -> Triple(first, second, third)
        DateOrder.DMY -> Triple(third, second, first)
        DateOrder.MDY -> Triple(third, first, second)
    }
    return pyDate(year, month, day) {
        DateFormatError(
            "${repr(text)} is not a valid ${order.name.lowercase(Locale.ROOT)} date",
            it,
        )
    }
}

/**
 * Parses `d/m/yyyy h:mm[:ss] [am/pm marker]` as a local time in [zone].
 *
 * Greek pages use `π.μ.` / `μ.μ.`; English pages `AM` / `PM`. Without a marker the time is read
 * as 24-hour. Ambiguous DST times resolve with fold=0 ([pyLocalToOffset]).
 */
public fun parseLocalDatetime(
    text: String,
    culture: Culture,
    order: DateOrder,
    zone: ZoneId,
): OffsetDateTime {
    val match =
        DATETIME_RE.matchEntire(clean(text))
            ?: throw DateFormatError("${repr(text)} is not a date and time")
    val groups = match.groups
    val day = parseDate(checkNotNull(groups["date"]).value, order)
    var hour = checkNotNull(groups["hour"]).value.toInt()
    val minute = checkNotNull(groups["minute"]).value.toInt()
    val second = groups["second"]?.value?.toInt() ?: 0
    val marker = groups["marker"]?.value
    if (marker != null) {
        val meridiem = MERIDIEM[culture]?.get(marker.lowercase(Locale.ROOT))
        if (meridiem == null || hour !in 1..HOURS_PER_HALF_DAY) {
            throw DateFormatError("${repr(text)} has an invalid 12-hour time")
        }
        hour = hour % HOURS_PER_HALF_DAY + if (meridiem == Meridiem.PM) HOURS_PER_HALF_DAY else 0
    }
    val local = try {
        LocalDateTime.of(day.year, day.month, day.dayOfMonth, hour, minute, second)
    } catch (exception: DateTimeException) {
        throw DateFormatError("${repr(text)} has an invalid time", exception)
    }
    return pyLocalToOffset(local, zone)
}

/** `datetime.date(year, month, day)`: years 1–9999 only (LocalDate also accepts year 0). */
internal inline fun pyDate(
    year: Int,
    month: Int,
    day: Int,
    error: (Throwable?) -> Throwable,
): LocalDate {
    if (year !in 1..MAX_YEAR) throw error(null)
    return try {
        LocalDate.of(year, month, day)
    } catch (exception: DateTimeException) {
        throw error(exception)
    }
}

private const val MAX_YEAR = 9999
