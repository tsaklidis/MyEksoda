package io.eksoda.feature.settings

import android.net.Uri
import androidx.paging.PagingData
import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import io.eksoda.core.data.repository.BackupRepository
import io.eksoda.core.data.repository.HouseholdRepository
import io.eksoda.core.data.repository.RestorePlan
import io.eksoda.core.data.repository.SettingsRepository
import io.eksoda.core.data.repository.SyncRepository
import io.eksoda.core.model.BackupSummary
import io.eksoda.core.model.Household
import io.eksoda.core.model.SyncEvent
import io.eksoda.core.model.SyncJoinResult
import io.eksoda.core.model.SyncPeer
import io.eksoda.core.model.SyncStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant

/** The preference switches on the Settings screen: what they show and what they change. */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val settings = FakeSettings()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newViewModel() = SettingsViewModel(
        households = FakeHouseholds,
        settings = settings,
        backups = UnusedBackups,
        sync = FakeSync,
        clock = Clock.systemUTC(),
    )

    /** The view model, with its state collected (it is shared only while observed). */
    private fun TestScope.viewModel(): SettingsViewModel = newViewModel().also { observe(it) }

    private fun TestScope.observe(viewModel: SettingsViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
    }

    @Test
    fun `the switches take taps only once the stored settings are read`() = runTest {
        val viewModel = newViewModel()
        // Before the settings are read the switches show placeholders, so they stay disabled.
        assertThat(viewModel.uiState.value.preferencesLoaded).isFalse()
        observe(viewModel)
        assertThat(viewModel.uiState.value.preferencesLoaded).isTrue()
    }

    @Test
    fun `the hide content switch shows its own stored preference`() = runTest {
        settings.hideContent.value = true
        val viewModel = viewModel()
        assertThat(viewModel.uiState.value.hideContent).isTrue()
        assertThat(viewModel.uiState.value.notifyImports).isFalse()
    }

    @Test
    fun `the hide content switch changes only that preference`() = runTest {
        val viewModel = viewModel()
        viewModel.onEvent(SettingsEvent.HideContentChanged(true))
        assertThat(settings.hideContent.value).isTrue()
        assertThat(settings.notifyImports.value).isFalse()
        assertThat(viewModel.uiState.value.hideContent).isTrue()
        assertThat(viewModel.uiState.value.notifyImports).isFalse()

        viewModel.onEvent(SettingsEvent.HideContentChanged(false))
        assertThat(settings.hideContent.value).isFalse()
        assertThat(viewModel.uiState.value.hideContent).isFalse()
    }

    @Test
    fun `the import notifications switch leaves hiding the content alone`() = runTest {
        val viewModel = viewModel()
        viewModel.onEvent(SettingsEvent.NotifyImportsChanged(true))
        assertThat(settings.notifyImports.value).isTrue()
        assertThat(settings.hideContent.value).isFalse()
        assertThat(viewModel.uiState.value.notifyImports).isTrue()
        assertThat(viewModel.uiState.value.hideContent).isFalse()
    }
}

private class FakeSettings : SettingsRepository {
    override val notifyImports = MutableStateFlow(false)
    override val hideContent = MutableStateFlow(false)
    override val foldedInsightsCards = MutableStateFlow(emptySet<String>())

    override suspend fun setNotifyImports(enabled: Boolean) {
        notifyImports.value = enabled
    }

    override suspend fun setHideContent(enabled: Boolean) {
        hideContent.value = enabled
    }

    override suspend fun toggleInsightsCardFolded(key: String) {
        foldedInsightsCards.value = foldedInsightsCards.value.let {
            if (key in it) it - key else it + key
        }
    }
}

/** No household yet: these tests are about the preferences only. */
private object FakeHouseholds : HouseholdRepository {
    override val household: Flow<Household?> = flowOf(null)

    override suspend fun createHousehold(
        name: String,
        defaultCurrency: String,
        timezone: String,
    ): Result<Household> = error("not used")

    override suspend fun updateHousehold(
        name: String,
        defaultCurrency: String,
        timezone: String,
    ): Result<Unit> = error("not used")

    override suspend fun setAnnualIncome(income: BigDecimal?): Result<Unit> = error("not used")
}

private object UnusedBackups : BackupRepository {
    override suspend fun export(destination: Uri, passphrase: String): Result<BackupSummary> =
        error("not used")

    override suspend fun open(source: Uri, passphrase: String): Result<RestorePlan> =
        error("not used")

    override suspend fun restore(plan: RestorePlan): Result<Unit> = error("not used")
}

/** Sync off, never synced. */
private object FakeSync : SyncRepository {
    override val enabled: Flow<Boolean> = flowOf(false)
    override val hasHouseholdKey: Flow<Boolean> = flowOf(false)
    override val peers: Flow<List<SyncPeer>> = flowOf(emptyList())
    override val lastSuccess: Flow<Instant?> = flowOf(null)
    override val status: Flow<SyncStatus> = emptyFlow()

    override suspend fun setEnabled(enabled: Boolean) = error("not used")

    override suspend fun pairingCode(): Result<String> = error("not used")

    override suspend fun join(text: String): SyncJoinResult = error("not used")

    override suspend fun leave(): Result<Unit> = error("not used")

    override fun getHistory(): Flow<PagingData<SyncEvent>> = emptyFlow()

    override suspend fun syncNow() = error("not used")
}
