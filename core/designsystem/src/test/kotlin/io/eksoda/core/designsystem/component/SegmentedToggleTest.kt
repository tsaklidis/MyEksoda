package io.eksoda.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isGreaterThanOrEqualTo
import assertk.assertions.isLessThanOrEqualTo
import assertk.assertions.isTrue
import io.eksoda.core.designsystem.theme.EksodaTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
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

    /** The bounds of the segment (the selectable node that holds the label). */
    private fun bounds(label: String): DpRect = compose.onNodeWithText(label).getBoundsInRoot()

    /** How the label's text was actually laid out on screen. */
    private fun textLayout(label: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        val node = compose.onNodeWithText(label, useUnmergedTree = true).fetchSemanticsNode()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.single()
    }

    @Test
    @Config(qualifiers = "420dpi")
    fun `side by side, no label needs a third line or breaks a word, at any width`() {
        val options = listOf("Περισσότερα χρήματα", "Συχνότερες αγορές", "Ανά τεμάχιο")
        var width by mutableStateOf(120.dp)
        compose.setContent {
            EksodaTheme {
                Box(Modifier.width(width)) { SegmentedToggle(options, 0, {}) }
            }
        }
        val layouts = mutableSetOf<Boolean>()
        // 2.625 px per dp: most widths give the segments an odd pixel to share.
        for (dp in 120..480) {
            width = dp.dp
            compose.waitForIdle()
            val sideBySide = bounds(options[1]).top < bounds(options[0]).bottom
            layouts += sideBySide
            if (!sideBySide) continue
            options.forEach { label ->
                val layout = textLayout(label)
                assertThat(layout.lineCount, "$label at $dp dp").isLessThanOrEqualTo(2)
                assertThat(breaksAWord(layout, label), "$label at $dp dp").isFalse()
            }
        }
        // Both happened, so the sweep checked something.
        assertThat(layouts).isEqualTo(setOf(true, false))
    }

    @Test
    fun `a line that ends inside a word counts as a broken word`() {
        val label = "e-receipt totals"
        // 48 dp less the paddings is too narrow for "receipt": it is broken somewhere inside.
        show(48.dp, listOf(label))
        assertThat(breaksAWord(textLayout(label), label)).isTrue()
    }

    @Test
    fun `a line that ends at a space does not`() {
        val label = "e-receipt totals"
        // Wide enough for "e-receipt", not for both words: two lines, broken at the space.
        show(90.dp, listOf(label))
        val layout = textLayout(label)
        assertThat(layout.lineCount).isEqualTo(2)
        assertThat(breaksAWord(layout, label)).isFalse()
    }

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
