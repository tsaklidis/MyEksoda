package io.eksoda.core.ui.text

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import org.junit.Test

/** Which palette a theme choice draws, given the phone's own dark theme setting. */
class AppThemeTest {
    @Test
    fun `following the phone draws what the phone is set to`() {
        assertThat(AppTheme.isDark(AppTheme.SYSTEM, systemDark = true)).isTrue()
        assertThat(AppTheme.isDark(AppTheme.SYSTEM, systemDark = false)).isFalse()
    }

    @Test
    fun `a fixed choice wins over the phone`() {
        assertThat(AppTheme.isDark(AppTheme.LIGHT, systemDark = true)).isFalse()
        assertThat(AppTheme.isDark(AppTheme.DARK, systemDark = false)).isTrue()
    }

    @Test
    fun `an unknown value follows the phone`() {
        assertThat(AppTheme.isDark("sepia", systemDark = true)).isTrue()
        assertThat(AppTheme.isDark("sepia", systemDark = false)).isFalse()
    }
}
