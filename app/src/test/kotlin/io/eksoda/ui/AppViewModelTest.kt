package io.eksoda.ui

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import dagger.Lazy
import io.eksoda.core.data.repository.HouseholdRepository
import io.eksoda.core.data.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * "Hide app content" as the launch screen sees it (MainActivity makes the window secure). What
 * an unreadable settings file gives is tested with the DataStore (core:data,
 * SettingsDataStoreTest).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(hideContent: Flow<Boolean>) = AppViewModel(
        // The household is not what these tests are about: its state ends as UNAVAILABLE.
        households = Lazy<HouseholdRepository> { error("not used") },
        settings = HideContentOnly(hideContent),
        ioDispatcher = dispatcher,
    )

    @Test
    fun `hiding the content is unknown until the preference is read, not off`() = runTest {
        val stored = MutableSharedFlow<Boolean>()
        val viewModel = viewModel(stored)
        // Null keeps the splash screen up: nothing is drawn before the window can be secure.
        assertThat(viewModel.hideContent.value).isNull()

        stored.emit(true)
        assertThat(viewModel.hideContent.value).isEqualTo(true)
        stored.emit(false)
        assertThat(viewModel.hideContent.value).isEqualTo(false)
    }

    @Test
    fun `a stored preference is known as soon as the view model exists`() = runTest {
        val viewModel = viewModel(flowOf(true))
        assertThat(viewModel.hideContent.value).isEqualTo(true)
    }
}

private class HideContentOnly(override val hideContent: Flow<Boolean>) : SettingsRepository {
    override val notifyImports: Flow<Boolean> = flowOf(false)
    override val foldedInsightsCards: Flow<Set<String>> = flowOf(emptySet())

    override suspend fun setNotifyImports(enabled: Boolean) = error("not used")

    override suspend fun setHideContent(enabled: Boolean) = error("not used")

    override suspend fun toggleInsightsCardFolded(key: String) = error("not used")
}
