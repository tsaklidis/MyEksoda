package io.eksoda.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.eksoda.core.designsystem.theme.EksodaTheme
import io.eksoda.core.designsystem.theme.HeroNumberStyle

/**
 * The big number of a card (docs/design/ui-redesign.md §3): [number] in IBM Plex Mono 44 sp, then
 * its [unit] smaller in ink-2 ("342,60" "€", "1,29" "€ / l"). Terracotta ([color] default) for
 * spending, periwinkle for a unit price. Read aloud as one phrase.
 */
@Composable
fun HeroAmount(
    number: String,
    unit: String?,
    modifier: Modifier = Modifier,
    color: Color = EksodaTheme.colors.spend,
) {
    val spoken = listOfNotNull(number, unit).joinToString(" ")
    Row(
        modifier = modifier.clearAndSetSemantics { contentDescription = spoken },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = number,
            style = HeroNumberStyle,
            color = color,
            maxLines = 1,
            modifier = Modifier.alignByBaseline(),
        )
        if (unit != null) {
            Text(
                text = unit,
                style = MaterialTheme.typography.bodyLarge,
                color = EksodaTheme.colors.ink2,
                maxLines = 1,
                modifier = Modifier.alignByBaseline(),
            )
        }
    }
}

@Preview
@Composable
private fun HeroAmountPreview() {
    EksodaTheme {
        Column {
            HeroAmount("342,60", "€")
            HeroAmount("1,29", "€ / l", color = EksodaTheme.colors.compare)
        }
    }
}
