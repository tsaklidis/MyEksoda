@file:OptIn(ExperimentalCoroutinesApi::class)

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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
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
            // Not run again: categories the user removed stay removed.
            val backfill = stored[AutoCategoryBackfill.DONE_KEY]
            assertThat(backfill).isEqualTo(AutoCategoryBackfill.VERSION)
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
            val inner = FlakyStore(failures = 2)
            val settings = SettingsRepositoryImpl(FailSafePreferences(inner))
            // The pauses between attempts run on the test's virtual clock: 1 s, then 2 s.
            assertThat(settings.hideContent.take(2).toList()).isEqualTo(listOf(true, false))
            assertThat(inner.reads).isEqualTo(3)
            assertThat(currentTime).isEqualTo(3_000L)
        }

    @Test
    fun `a write that goes through during the pause shows at once`() = runTest {
        val store = FailSafePreferences(FlakyStore(failures = 1))
        val seen = record(SettingsRepositoryImpl(store).hideContent)
        runCurrent()
        assertThat(seen).isEqualTo(listOf(0L to true))

        // The user turns hiding off: the write reads the file, so it reads again now.
        store.edit { it[SettingsRepositoryImpl.HIDE_CONTENT_KEY] = false }
        runCurrent()
        assertThat(seen).isEqualTo(listOf(0L to true, 0L to false))
    }

    @Test
    fun `after a read that works, the pause starts again from one second`() = runTest {
        val inner = FlakyStore(failures = 2)
        val seen = record(SettingsRepositoryImpl(FailSafePreferences(inner)).hideContent)
        // advanceUntilIdle() would skip the background collector: step the clock instead.
        advanceTimeBy(3_000L)
        runCurrent()
        assertThat(seen).isEqualTo(listOf(0L to true, 3_000L to false))

        // The file stops reading again: one second, not the four that would come next.
        inner.outage()
        runCurrent()
        advanceTimeBy(1_000L)
        runCurrent()
        assertThat(seen)
            .isEqualTo(listOf(0L to true, 3_000L to false, 3_000L to true, 4_000L to false))
    }

    @Test
    fun `a failed read does not end the flow, so later changes still arrive`() = runTest {
        val inner = FlakyStore(failures = 1)
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

/** Each value of [flow] with the virtual time it arrived at, collected in the background. */
private fun <T> TestScope.record(flow: Flow<T>): List<Pair<Long, T>> {
    val seen = mutableListOf<Pair<Long, T>>()
    backgroundScope.launch { flow.collect { seen += currentTime to it } }
    return seen
}

/**
 * A store whose first [failures] reads fail with an I/O error, and whose open reads fail on
 * [outage]. Writes always go through.
 */
private class FlakyStore(private var failures: Int) : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    private val outages = MutableSharedFlow<Unit>()
    var reads = 0
        private set

    override val data: Flow<Preferences> = flow {
        reads++
        if (failures > 0) {
            failures--
            throw IOException("cannot read")
        }
        emitAll(merge(state, outages.map { throw IOException("stopped reading") }))
    }

    suspend fun outage() = outages.emit(Unit)

    override suspend fun updateData(
        transform: suspend (t: Preferences) -> Preferences,
    ): Preferences = transform(state.value).also { state.value = it }
}
