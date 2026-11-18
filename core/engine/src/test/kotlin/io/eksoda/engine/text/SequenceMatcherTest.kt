package io.eksoda.engine.text

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isCloseTo
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test

/** Expected values are CPython 3.13's difflib results (some from its documentation). */
class SequenceMatcherTest {
    @Test
    fun `the longest match is the earliest, extended over equal neighbors`() {
        assertThat(SequenceMatcher(" abcd", "abcd abcd").findLongestMatch(0, 5, 0, 9))
            .isEqualTo(Match(0, 4, 5))
        assertThat(SequenceMatcher("abcd", "abcd abcd").findLongestMatch(0, 4, 0, 9))
            .isEqualTo(Match(0, 0, 4))
        assertThat(SequenceMatcher("abc", "xyz").findLongestMatch(0, 3, 0, 3))
            .isEqualTo(Match(0, 0, 0))
    }

    @Test
    fun `matching blocks end with the sentinel`() {
        assertThat(SequenceMatcher("abxcd", "abcd").matchingBlocks())
            .containsExactly(Match(0, 0, 2), Match(3, 2, 2), Match(5, 4, 0))
        assertThat(SequenceMatcher("", "").matchingBlocks()).containsExactly(Match(0, 0, 0))
    }

    @Test
    fun `ratio is twice the matches over the total length`() {
        assertThat(SequenceMatcher("abcd", "bcde").ratio()).isEqualTo(0.75)
        assertThat(SequenceMatcher("abxcd", "abcd").ratio()).isCloseTo(8.0 / 9, 1e-12)
        assertThat(SequenceMatcher("abcd", "abcd").ratio()).isEqualTo(1.0)
        assertThat(SequenceMatcher("", "").ratio()).isEqualTo(1.0)
        assertThat(SequenceMatcher("a", "").ratio()).isEqualTo(0.0)
        // Code points, not UTF-16 units.
        assertThat(SequenceMatcher("😀a", "😀b").ratio()).isEqualTo(0.5)
    }

    @Test
    fun `popular elements of a long second string do not start matches`() {
        // "a" is popular in b, so no match starts with it (and "x" ≠ "y" blocks extending one).
        assertThat(SequenceMatcher("xa", "y" + "a".repeat(199)).ratio()).isEqualTo(0.0)
        // Under 200 elements nothing is popular.
        assertThat(SequenceMatcher("xa", "y" + "a".repeat(198)).ratio())
            .isCloseTo(2.0 / 201, 1e-12)
        // A popular element still extends a match, as in CPython.
        assertThat(SequenceMatcher("a".repeat(10), "a".repeat(200)).ratio())
            .isCloseTo(20.0 / 210, 1e-12)
    }
}
