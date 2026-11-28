package io.eksoda.feature.household.sync

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isTrue
import io.eksoda.core.data.repository.SyncRepository
import io.eksoda.core.model.SyncJoinResult
import io.eksoda.core.model.SyncPeer
import io.eksoda.core.testing.FakeSyncRepository
import io.eksoda.core.testing.MainDispatcherRule
import io.eksoda.core.testing.keepCollecting
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.time.Instant

class SyncViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val sync = FakeSyncRepository()
    private val viewModel = SyncViewModel(sync)
    private val state get() = viewModel.state.value

    @Test
    fun `turning sync on makes the key, and sync now goes to every phone`() = runTest {
        keepCollecting(viewModel.state)
        assertThat(state.loading).isFalse()
        assertThat(state.enabled).isFalse()
        viewModel.onEvent(SyncScreenEvent.EnabledChanged(true))
        assertThat(state.enabled).isTrue()
        assertThat(state.hasKey).isTrue()
        viewModel.onEvent(SyncScreenEvent.SyncNowClicked)
        assertThat(sync.calls).containsExactly("setEnabled(true)", "syncNow()")
    }

    @Test
    fun `the household code shows only while asked for, and never in a log line`() = runTest {
        keepCollecting(viewModel.state)
        sync.code = "SECRET-KEY"
        viewModel.onEvent(SyncScreenEvent.AddPhoneClicked)
        val dialog = state.dialog
        assertThat(dialog).isInstanceOf<SyncDialog.Code>()
        assertThat((dialog as SyncDialog.Code).text).isEqualTo("SECRET-KEY")
        assertThat(dialog.toString()).doesNotContain("SECRET")
        assertThat(state.toString()).doesNotContain("SECRET")
        viewModel.onEvent(SyncScreenEvent.DialogDismissed)
        assertThat(state.dialog).isEqualTo(SyncDialog.None)
    }

    @Test
    fun `a code that cannot be made says so`() = runTest {
        sync.failure = IllegalStateException("keystore")
        viewModel.effects.test {
            viewModel.onEvent(SyncScreenEvent.AddPhoneClicked)
            assertThat(awaitItem()).isEqualTo(SyncEffect.CodeFailed)
        }
    }

    @Test
    fun `joining scans at once, unless this phone already has paired phones`() = runTest {
        keepCollecting(viewModel.state)
        viewModel.onEvent(SyncScreenEvent.JoinClicked)
        assertThat(state.scanning).isTrue()
        viewModel.onEvent(SyncScreenEvent.ScanCancelled)
        assertThat(state.scanning).isFalse()

        sync.phones.value = listOf(SyncPeer("d1", "Anna's phone", Instant.EPOCH, null, null))
        viewModel.onEvent(SyncScreenEvent.JoinClicked)
        assertThat(state.dialog).isEqualTo(SyncDialog.ConfirmJoin)
        assertThat(state.scanning).isFalse()
        viewModel.onEvent(SyncScreenEvent.JoinConfirmed)
        assertThat(state.scanning).isTrue()
        assertThat(state.dialog).isEqualTo(SyncDialog.None)
    }

    @Test
    fun `each answer to a scanned code is told, and the camera stops`() = runTest {
        keepCollecting(viewModel.state)
        val answers = listOf(
            SyncJoinResult.Joined("Anna's phone") to SyncEffect.Joined("Anna's phone"),
            SyncJoinResult.NotACode to SyncEffect.NotACode,
            SyncJoinResult.OwnCode to SyncEffect.OwnCode,
            SyncJoinResult.Failed to SyncEffect.JoinFailed,
        )
        viewModel.effects.test {
            for ((result, effect) in answers) {
                sync.joinResult = result
                viewModel.onEvent(SyncScreenEvent.JoinClicked)
                viewModel.onEvent(SyncScreenEvent.CodeScanned("code"))
                assertThat(awaitItem()).isEqualTo(effect)
                assertThat(state.scanning).isFalse()
            }
        }
    }

    @Test
    fun `a code is joined only while scanning, and only once`() = runTest {
        keepCollecting(viewModel.state)
        viewModel.onEvent(SyncScreenEvent.CodeScanned("not scanning"))
        assertThat(sync.calls).isEqualTo(emptyList())

        val gate = CompletableDeferred<Unit>()
        val slow = FakeSyncRepository()
        val joining = SyncViewModel(
            object : SyncRepository by slow {
                override suspend fun join(text: String): SyncJoinResult {
                    gate.await()
                    return slow.join(text)
                }
            },
        )
        keepCollecting(joining.state)
        joining.onEvent(SyncScreenEvent.JoinClicked)
        // The camera reports the same code many times a second.
        repeat(3) { joining.onEvent(SyncScreenEvent.CodeScanned("code")) }
        gate.complete(Unit)
        assertThat(slow.calls).containsExactly("join(code)")
    }

    @Test
    fun `leaving asks first, then says whether it worked`() = runTest {
        keepCollecting(viewModel.state)
        viewModel.onEvent(SyncScreenEvent.LeaveClicked)
        assertThat(state.dialog).isEqualTo(SyncDialog.ConfirmLeave)
        viewModel.effects.test {
            viewModel.onEvent(SyncScreenEvent.LeaveConfirmed)
            assertThat(awaitItem()).isEqualTo(SyncEffect.Left)
            assertThat(state.dialog).isEqualTo(SyncDialog.None)
            sync.failure = IllegalStateException("keystore")
            viewModel.onEvent(SyncScreenEvent.LeaveConfirmed)
            assertThat(awaitItem()).isEqualTo(SyncEffect.LeaveFailed)
        }
    }
}
