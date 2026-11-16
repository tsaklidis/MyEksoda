package io.eksoda.core.ui.component

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay

private const val PROGRESS_DELAY_MILLIS = 300L

/**
 * A spinner that appears only when loading takes longer than [delayMillis]. Most local reads
 * finish sooner, and a spinner that flashes for a frame looks slower than none.
 */
@Composable
fun DelayedProgressIndicator(
    modifier: Modifier = Modifier,
    delayMillis: Long = PROGRESS_DELAY_MILLIS,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(delayMillis)
        visible = true
    }
    if (visible) CircularProgressIndicator(modifier = modifier)
}
