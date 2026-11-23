package io.eksoda.core.data.repository

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.Executors

/** The app's own settings, in the preferences DataStore. */
class SettingsRepositoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val settings by lazy {
        // The app's own DataStore setup (AppModule), so its corruption handling is tested too.
        SettingsRepositoryImpl(
            settingsDataStore(scope) { folder.root.resolve("settings.preferences_pb") },
        )
    }

    @After
    fun tearDown() {
        scope.cancel()
        dispatcher.close()
    }

    @Test
    fun `import notifications are off until turned on`() = runTest {
        assertThat(settings.notifyImports.first()).isFalse()
        settings.setNotifyImports(true)
        assertThat(settings.notifyImports.first()).isTrue()
        settings.setNotifyImports(false)
        assertThat(settings.notifyImports.first()).isFalse()
    }

    @Test
    fun `hiding the app's content is off until turned on`() = runTest {
        assertThat(settings.hideContent.first()).isFalse()
        settings.setHideContent(true)
        assertThat(settings.hideContent.first()).isTrue()
        // Its own preference: import notifications are left as they were.
        assertThat(settings.notifyImports.first()).isFalse()
        settings.setHideContent(false)
        assertThat(settings.hideContent.first()).isFalse()
    }

    @Test
    fun `a corrupt settings file is replaced with the defaults, and settings save again`() =
        runTest {
            // Not a preferences protobuf: DataStore fails the read with a CorruptionException.
            folder.root.resolve("settings.preferences_pb").writeBytes(byteArrayOf(-1, -1, -1, -1))
            assertThat(settings.hideContent.first()).isFalse()
            assertThat(settings.notifyImports.first()).isFalse()
            assertThat(settings.foldedInsightsCards.first()).isEqualTo(emptySet())
            // Writing reads the file first: it must not fail on the corrupt one either.
            settings.setHideContent(true)
            assertThat(settings.hideContent.first()).isTrue()
        }

    @Test
    fun `a corrupt settings file does not fail a write that comes first`() = runTest {
        folder.root.resolve("settings.preferences_pb").writeBytes(byteArrayOf(-1, -1, -1, -1))
        settings.setNotifyImports(true)
        assertThat(settings.notifyImports.first()).isTrue()
        assertThat(settings.hideContent.first()).isFalse()
    }

    @Test
    fun `an insights card folds and unfolds`() = runTest {
        assertThat(settings.foldedInsightsCards.first()).isEqualTo(emptySet())
        settings.toggleInsightsCardFolded("trend")
        settings.toggleInsightsCardFolded("shops")
        assertThat(settings.foldedInsightsCards.first()).isEqualTo(setOf("trend", "shops"))
        settings.toggleInsightsCardFolded("trend")
        assertThat(settings.foldedInsightsCards.first()).isEqualTo(setOf("shops"))
    }
}
