package io.eksoda.core.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.eksoda.core.ui.R
import io.eksoda.core.ui.text.appLocale
import io.eksoda.core.ui.text.dateFormatter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Picks a range of days (the receipt filter's and Insights' "Custom range"), full screen as
 * Material recommends for ranges on phones. The heading is our own short "3 Nov 2026 – 17 Nov
 * 2026": the picker's default ("Start date – End date") is too long in Greek and, in a small
 * dialog, wrapped letter by letter and left no room for the calendar. Days after today cannot
 * be picked (there are no receipts yet). OK needs a first day; [onConfirm] gets the last day
 * as null when only one day was picked.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRangeDialog(
    from: LocalDate?,
    to: LocalDate?,
    onConfirm: (LocalDate, LocalDate?) -> Unit,
    onDismiss: () -> Unit,
) {
    val today = remember { LocalDate.now() }
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = from?.toPickerMillis(),
        initialSelectedEndDateMillis = to?.toPickerMillis(),
        yearRange = FIRST_YEAR..today.year,
        selectableDates = remember(today) { UpTo(today) },
    )
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.date_range_cancel),
                        )
                    }
                    Text(
                        stringResource(R.string.date_range_title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = {
                            state.selectedStartDateMillis?.let { start ->
                                onConfirm(
                                    start.toPickerDate(),
                                    state.selectedEndDateMillis?.toPickerDate(),
                                )
                            }
                        },
                        enabled = state.selectedStartDateMillis != null,
                    ) { Text(stringResource(R.string.date_range_ok)) }
                }
                DateRangePicker(
                    state = state,
                    modifier = Modifier.weight(1f),
                    title = null,
                    headline = {
                        RangeHeadline(
                            state.selectedStartDateMillis?.toPickerDate(),
                            state.selectedEndDateMillis?.toPickerDate(),
                        )
                    },
                    // Typing dates brings back the long labels; tapping days is enough.
                    showModeToggle = false,
                )
            }
        }
    }
}

/** "3 Νοε 2026 – 17 Νοε 2026", with "Από" and "Έως" while a day is still missing. */
@Composable
private fun RangeHeadline(start: LocalDate?, end: LocalDate?) {
    val format = dateFormatter("d MMM yyyy", appLocale())
    val first = start?.format(format) ?: stringResource(R.string.date_range_from)
    val last = end?.format(format) ?: stringResource(R.string.date_range_to)
    Text(
        text = "$first – $last",
        style = MaterialTheme.typography.headlineSmall,
        maxLines = 2,
        modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 8.dp, bottom = 12.dp),
    )
}

/** Days up to [today] only. */
@OptIn(ExperimentalMaterial3Api::class)
private class UpTo(today: LocalDate) : SelectableDates {
    private val lastMillis = today.toPickerMillis()
    private val lastYear = today.year

    override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= lastMillis

    override fun isSelectableYear(year: Int): Boolean = year <= lastYear
}

private const val FIRST_YEAR = 2000

/** The date picker works in UTC midnights. */
private fun LocalDate.toPickerMillis(): Long =
    atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toPickerDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
