package io.eksoda.engine.text

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.Locale
import kotlin.math.abs

// Python datetime behavior the engine's stored times depend on.

private const val NANOS_PER_MICRO = 1_000
private const val SECONDS_PER_HOUR = 3_600
private const val SECONDS_PER_MINUTE = 60

/**
 * An aware Python `datetime` for a wall-clock time in [zone], with `fold=0` (the default).
 * In a DST gap Python keeps the wall time and uses the offset from before the change; in an
 * overlap it takes the earlier offset. `ZonedDateTime` would move a gap time forward, so the
 * offset is chosen here and the wall time is kept.
 */
internal fun pyLocalToOffset(local: LocalDateTime, zone: ZoneId): OffsetDateTime {
    val rules = zone.rules
    val offset = rules.getTransition(local)?.offsetBefore ?: rules.getOffset(local)
    return OffsetDateTime.of(local, offset)
}

/** `datetime.isoformat()`: seconds always, microseconds when non-zero, offset `+HH:MM[:SS]`. */
internal fun OffsetDateTime.pyIsoFormat(): String {
    val text = StringBuilder(toLocalDate().pyIsoFormat())
    text.append("T%02d:%02d:%02d".format(Locale.ROOT, hour, minute, second))
    val micros = nano / NANOS_PER_MICRO
    if (micros != 0) text.append(".%06d".format(Locale.ROOT, micros))
    val totalSeconds = offset.totalSeconds
    val absolute = abs(totalSeconds)
    val hours = absolute / SECONDS_PER_HOUR
    val minutes = absolute % SECONDS_PER_HOUR / SECONDS_PER_MINUTE
    text.append(if (totalSeconds < 0) '-' else '+')
    text.append("%02d:%02d".format(Locale.ROOT, hours, minutes))
    val seconds = absolute % SECONDS_PER_MINUTE
    if (seconds != 0) text.append(":%02d".format(Locale.ROOT, seconds))
    return text.toString()
}

/** `date.isoformat()`: `YYYY-MM-DD` with a four-digit year. */
internal fun LocalDate.pyIsoFormat(): String =
    "%04d-%02d-%02d".format(Locale.ROOT, year, monthValue, dayOfMonth)
