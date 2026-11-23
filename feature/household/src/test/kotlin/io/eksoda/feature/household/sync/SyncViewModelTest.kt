package io.eksoda.feature.household.sync

import androidx.paging.PagingData
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import io.eksoda.core.data.repository.SyncRepository
import io.eksoda.core.model.SyncEvent
import io.eksoda.core.model.SyncJoinResult
import io.eksoda.core.model.SyncPeer
import io.eksoda.core.model.SyncStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.security.KeyStoreException
import java.time.Instant

/** The sync switch: a change that cannot be made leaves sync as it was, and says so. */
@OptIn(ExperimentalCoroutinesApi::class)
class SyncViewModelTest {
    private val sync = SwitchOnly()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `turning sync on changes it, with no message`() = runTest {
        val viewModel = SyncViewModel(sync)
        viewModel.onEvent(SyncScreenEvent.EnabledChanged(true))
        assertThat(sync.enabled.value).isEqualTo(true)
        assertThat(withTimeoutOrNull(1) { viewModel.effects.first() }).isNull()
    }

    @Test
    fun `a settings file that cannot be written leaves sync off, and says so`() = runTest {
        sync.failure = IOException("cannot write")
        val viewModel = SyncViewModel(sync)
        viewModel.onEvent(SyncScreenEvent.EnabledChanged(true))
        assertThat(viewModel.effects.first()).isEqualTo(SyncEffect.SwitchFailed)
        assertThat(sync.enabled.value).isEqualTo(false)
    }

    @Test
    fun `a household key the Keystore cannot make leaves sync off, and says so`() = runTest {
        sync.failure = KeyStoreException("no key")
        val viewModel = SyncViewModel(sync)
        viewModel.onEvent(SyncScreenEvent.EnabledChanged(true))
        assertThat(viewModel.effects.first()).isEqualTo(SyncEffect.SwitchFailed)
        assertThat(sync.enabled.value).isEqualTo(false)
    }
}

/** Sync off, never synced; only the switch is used. */
private class SwitchOnly : SyncRepository {
    override val enabled = MutableStateFlow(false)

    /** What turning the switch throws, or null when it works. */
    var failure: Exception? = null

    override suspend fun setEnabled(enabled: Boolean) {
        failure?.let { throw it }
        this.enabled.value = enabled
    }

    override val hasHouseholdKey: Flow<Boolean> = flowOf(false)
    override val peers: Flow<List<SyncPeer>> = flowOf(emptyList())
    override val lastSuccess: Flow<Instant?> = flowOf(null)
    override val status: Flow<SyncStatus> = flowOf(SyncStatus())

    override suspend fun pairingCode(): Result<String> = error("not used")

    override suspend fun join(text: String): SyncJoinResult = error("not used")

    override suspend fun leave(): Result<Unit> = error("not used")

    override fun getHistory(): Flow<PagingData<SyncEvent>> = emptyFlow()

    override suspend fun syncNow() = error("not used")
}
