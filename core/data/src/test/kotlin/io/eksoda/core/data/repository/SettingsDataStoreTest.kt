package io.eksoda.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import assertk.assertions.isTrue
import io.eksoda.core.data.imports.ImportNotifier
import io.eksoda.core.data.products.AutoCategoryBackfill
import io.eksoda.core.data.sync.SyncPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.util.concurrent.Executors

/** The shared preferences file when it cannot be read: never a crash, never less privacy. */
class SettingsDataStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val file by lazy { folder.root.resolve("settings.preferences_pb") }

    @After
    fun tearDown() {
        scope.cancel()
        dispatcher.close()
    }

    private fun corruptFile() {
        // Not a preferences protobuf: DataStore fails the read with a CorruptionException.
        file.writeBytes(byteArrayOf(-1, -1, -1, -1))
    }

    @Test
    fun `a corrupt file hides the content and does not run the category backfill again`() =
        runTest {
            corruptFile()
            val preferences = settingsDataStore(scope) { file }
            val settings = SettingsRepositoryImpl(preferences)
            assertThat(settings.hideContent.first()).isTrue()
            assertThat(settings.notifyImports.first()).isFalse()
            val stored = preferences.data.first()
            assertThat(stored[AutoCategoryBackfill.DONE_KEY]).isEqualTo(AutoCategoryBackfill.VERSION)
            // Household sync is off until the user turns it back on: no network meanwhile.
            assertThat(stored[SyncPreferences.ENABLED]).isNull()
        }

    @Test
    fun `after a corrupt file the user can turn hiding off, and it is saved`() = runTest {
        corruptFile()
        val settings = SettingsRepositoryImpl(settingsDataStore(scope) { file })
        settings.setHideContent(false)
        assertThat(settings.hideContent.first()).isFalse()
    }

    @Test
    fun `a corrupt file does not fail a write that comes first`() = runTest {
        corruptFile()
        val settings = SettingsRepositoryImpl(settingsDataStore(scope) { file })
        settings.setNotifyImports(true)
        assertThat(settings.notifyImports.first()).isTrue()
        assertThat(settings.hideContent.first()).isTrue()
    }

    @Test
    fun `a failed read gives the safe values, then the stored ones once the file reads again`() =
        runTest {
            val inner = FlakyStore(failures = 2, stored = emptyPreferences())
            val settings = SettingsRepositoryImpl(FailSafePreferences(inner))
            // The pauses between attempts run on the test's virtual clock.
            assertThat(settings.hideContent.take(2).toList()).isEqualTo(listOf(true, false))
            assertThat(inner.reads).isEqualTo(3)
        }

    @Test
    fun `a failed read does not end the flow, so later changes still arrive`() = runTest {
        val inner = FlakyStore(failures = 1, stored = emptyPreferences())
        val store = FailSafePreferences(inner)
        val settings = SettingsRepositoryImpl(store)
        val seen = mutableListOf<Boolean>()
        settings.hideContent.take(3).collect { value ->
            seen += value
            if (seen.size == 2) store.edit { it[SettingsRepositoryImpl.HIDE_CONTENT_KEY] = true }
        }
        assertThat(seen).isEqualTo(listOf(true, false, true))
    }

    @Test
    fun `an error that is not about reading the file is not hidden`() = runTest {
        val broken = object : DataStore<Preferences> {
            override val data: Flow<Preferences> = flow { error("a bug") }

            override suspend fun updateData(
                transform: suspend (t: Preferences) -> Preferences,
            ): Preferences = error("not used")
        }
        assertFailure { FailSafePreferences(broken).data.first() }
            .isInstanceOf(IllegalStateException::class)
    }

    @Test
    fun `the safe values hide the content and leave sync and notifications at their defaults`() {
        val safe = safePreferences()
        assertThat(safe[SettingsRepositoryImpl.HIDE_CONTENT_KEY]).isEqualTo(true)
        assertThat(safe[AutoCategoryBackfill.DONE_KEY]).isEqualTo(AutoCategoryBackfill.VERSION)
        assertThat(safe[SyncPreferences.ENABLED]).isNull()
        assertThat(safe[ImportNotifier.NOTIFY_IMPORTS_KEY]).isNull()
        assertThat(safe.asMap().size).isEqualTo(2)
    }
}

/** A store whose first [failures] reads fail with an I/O error. */
private class FlakyStore(private var failures: Int, stored: Preferences) : DataStore<Preferences> {
    private val state = MutableStateFlow(stored)
    var reads = 0
        private set

    override val data: Flow<Preferences> = flow {
        reads++
        if (failures-- > 0) throw IOException("cannot read")
        state.collect { emit(it) }
    }

    override suspend fun updateData(
        transform: suspend (t: Preferences) -> Preferences,
    ): Preferences = transform(state.value).also { state.value = it }
}
