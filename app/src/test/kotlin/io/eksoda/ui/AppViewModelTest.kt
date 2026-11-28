package io.eksoda.ui

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import dagger.Lazy
import io.eksoda.core.data.repository.HouseholdRepository
import io.eksoda.core.model.Household
import io.eksoda.core.testing.FakeHouseholdRepository
import io.eksoda.core.testing.MainDispatcherRule
import io.eksoda.core.testing.testHousehold
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/** Which screen the app starts on, and what it was opened with. */
class AppViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private fun viewModel(households: HouseholdRepository) =
        AppViewModel(Lazy { households }, main.dispatcher)

    @Test
    fun `without a household the app opens onboarding, and leaves it once one exists`() = runTest {
        val households = FakeHouseholdRepository()
        val viewModel = viewModel(households)
        assertThat(viewModel.state.value).isEqualTo(AppState.ONBOARDING)
        households.current.value = testHousehold()
        assertThat(viewModel.state.value).isEqualTo(AppState.READY)
    }

    @Test
    fun `with a household the app is ready`() = runTest {
        assertThat(viewModel(FakeHouseholdRepository(testHousehold())).state.value)
            .isEqualTo(AppState.READY)
    }

    @Test
    fun `a database that cannot be opened is unavailable, never onboarding`() = runTest {
        // Unwrapping the key fails: the app must never offer to start over on top of the data.
        val unopenable = object : HouseholdRepository by FakeHouseholdRepository() {
            override val household: Flow<Household?> =
                flow { throw IllegalStateException("the database key cannot be unwrapped") }
        }
        assertThat(viewModel(unopenable).state.value).isEqualTo(AppState.UNAVAILABLE)

        // Creating the repository itself fails the same way.
        val failing = AppViewModel(Lazy { error("no key") }, main.dispatcher)
        assertThat(failing.state.value).isEqualTo(AppState.UNAVAILABLE)
    }

    @Test
    fun `a shared link or a notification is kept until it is handled`() = runTest {
        val viewModel = viewModel(FakeHouseholdRepository(testHousehold()))
        assertThat(viewModel.launch.value).isNull()
        viewModel.onLaunch(AppLaunch.Share("look https://receipts.example.gr/v/1"))
        assertThat(viewModel.launch.value)
            .isEqualTo(AppLaunch.Share("look https://receipts.example.gr/v/1"))
        viewModel.onLaunch(AppLaunch.OpenReceipt("receipt-1"))
        assertThat(viewModel.launch.value).isEqualTo(AppLaunch.OpenReceipt("receipt-1"))
        viewModel.onLaunchHandled()
        assertThat(viewModel.launch.value).isNull()
    }
}
