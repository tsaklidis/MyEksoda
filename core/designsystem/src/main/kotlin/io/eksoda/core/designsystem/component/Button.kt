package io.eksoda.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.eksoda.core.designsystem.theme.EksodaTheme
import io.eksoda.core.designsystem.theme.PillShape

/**
 * The main action of a screen (docs/design/ui-redesign.md §3): a full-width ink pill with the
 * page color as text, neutral so it never reads as "spending" or "previous period", with a
 * progress state.
 */
@Composable
fun EksodaButton(
    onClick: () -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
) {
    val colors = EksodaTheme.colors
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp),
        enabled = enabled && !isLoading,
        shape = PillShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.ink,
            contentColor = colors.page,
            disabledContainerColor = colors.tile,
            disabledContentColor = colors.ink2,
        ),
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = LocalContentColor.current,
                strokeWidth = 2.dp,
            )
        } else {
            Text(text = text)
        }
    }
}

/**
 * A secondary action: an outlined pill in ink ("Add by hand", "Skip"). Its width follows the
 * label unless [modifier] says otherwise.
 */
@Composable
fun EksodaOutlinedButton(
    onClick: () -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = EksodaTheme.colors
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        enabled = enabled,
        shape = PillShape,
        border = BorderStroke(1.5.dp, if (enabled) colors.ink else colors.line),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = colors.ink,
            disabledContentColor = colors.ink2,
        ),
    ) {
        Text(text = text)
    }
}

@Preview
@Composable
private fun ButtonsPreview() {
    EksodaTheme(darkTheme = false) {
        Column {
            EksodaButton(onClick = {}, text = "Create household")
            EksodaOutlinedButton(onClick = {}, text = "Add by hand")
        }
    }
}
