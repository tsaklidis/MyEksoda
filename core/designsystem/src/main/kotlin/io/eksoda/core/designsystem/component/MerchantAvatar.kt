package io.eksoda.core.designsystem.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.eksoda.core.designsystem.R
import io.eksoda.core.designsystem.theme.EksodaTheme

/**
 * A shop in a circle: its logo when the app has one for its [vatNumber] (ΑΦΜ), else its
 * initials on a neutral tile with a line border. Logos are the one exception to the palette
 * (docs/design/ui-redesign.md §1): shops are otherwise told apart by name, not by color. Decorative:
 * the shop's name is always next to it. [id] is kept for callers and future use.
 */
@Suppress("UnusedParameter")
@Composable
fun MerchantAvatar(
    id: String,
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    vatNumber: String? = null,
) {
    val colors = EksodaTheme.colors
    val logo = vatNumber?.let(MERCHANT_LOGOS::get)
    if (logo != null) {
        Image(
            painter = painterResource(logo),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.size(size).clip(CircleShape).border(1.dp, colors.line, CircleShape),
        )
        return
    }
    val initials = remember(name) {
        name.split(' ', '.', '-')
            .filter { word -> word.firstOrNull()?.isLetterOrDigit() == true }
            .take(2)
            .joinToString("") { it.first().uppercase() }
    }
    Box(
        modifier = modifier
            .size(size)
            .background(colors.tile, CircleShape)
            .border(1.dp, colors.line, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials,
            color = colors.ink,
            style = if (size >= 56.dp) {
                MaterialTheme.typography.titleLarge
            } else {
                MaterialTheme.typography.titleSmall
            },
        )
    }
}

/**
 * The chains with a bundled logo, by VAT number (ΑΦΜ). My Market and METRO Cash & Carry share one
 * VAT number, so they share a logo.
 */
private val MERCHANT_LOGOS: Map<String, Int> = mapOf(
    "094025817" to R.drawable.logo_ab,
    "093683423" to R.drawable.logo_lidl,
    "094062259" to R.drawable.logo_mymarket,
    "800764388" to R.drawable.logo_sklavenitis,
)

@Preview
@Composable
private fun MerchantAvatarPreview() {
    EksodaTheme { MerchantAvatar(id = "1", name = "AB Vasilopoulos", vatNumber = "094025817") }
}
