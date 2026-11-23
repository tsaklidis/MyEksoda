package io.eksoda.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThanOrEqualTo
import io.eksoda.core.designsystem.theme.EksodaTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** Labels are never cut: they wrap side by side, or the options stack. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SegmentedToggleTest {
    @get:Rule
    val compose = createComposeRule()

    private fun show(width: Dp, options: List<String>, onSelect: (Int) -> Unit = {}) {
        compose.setContent {
            EksodaTheme {
                Box(Modifier.width(width)) { SegmentedToggle(options, 0, onSelect) }
            }
        }
    }

    private fun bounds(label: String): DpRect = compose.onNodeWithText(label).getBoundsInRoot()

    @Test
    fun `labels that fit sit side by side`() {
        show(360.dp, listOf("Most spent", "Most bought"))
        val first = bounds("Most spent")
        val second = bounds("Most bought")
        assertThat(second.left).isGreaterThanOrEqualTo(first.right)
        assertThat(second.top).isEqualTo(first.top)
    }

    @Test
    fun `a label that wraps keeps both segments the same height`() {
        show(300.dp, listOf("Περισσότερα χρήματα", "Συχνότερες αγορές"))
        val first = bounds("Περισσότερα χρήματα")
        val second = bounds("Συχνότερες αγορές")
        assertThat(second.left).isGreaterThanOrEqualTo(first.right)
        assertThat(second.bottom - second.top).isEqualTo(first.bottom - first.top)
    }

    @Test
    fun `labels that cannot fit side by side stack, each at the full width`() {
        show(160.dp, listOf("Ανά κιλό", "Ανά λίτρο", "Ανά τεμάχιο"))
        val first = bounds("Ανά κιλό")
        val second = bounds("Ανά λίτρο")
        val third = bounds("Ανά τεμάχιο")
        assertThat(second.top).isGreaterThanOrEqualTo(first.bottom)
        assertThat(third.top).isGreaterThanOrEqualTo(second.bottom)
        assertThat(third.right - third.left).isEqualTo(first.right - first.left)
    }

    @Test
    fun `tapping a stacked option selects it`() {
        var selected = -1
        show(160.dp, listOf("Ανά κιλό", "Ανά λίτρο", "Ανά τεμάχιο")) { selected = it }
        compose.onNodeWithText("Ανά τεμάχιο").performClick()
        assertThat(selected).isEqualTo(2)
    }
}
