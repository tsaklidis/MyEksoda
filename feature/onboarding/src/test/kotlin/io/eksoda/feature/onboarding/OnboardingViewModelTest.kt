package io.eksoda.feature.onboarding

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import io.eksoda.core.testing.FakeHouseholdRepository
import io.eksoda.core.testing.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class OnboardingViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val households = FakeHouseholdRepository()
    private val viewModel = OnboardingViewModel(households)
    private val state get() = viewModel.state.value

    @Test
    fun `the household is created with its name, in euros and Athens time`() = runTest {
        viewModel.onEvent(OnboardingEvent.NameChanged("Home"))
        assertThat(state.canCreate).isTrue()
        viewModel.onEvent(OnboardingEvent.Create)
        assertThat(households.calls).containsExactly("createHousehold(Home, EUR, Europe/Athens)")
        assertThat(households.current.value).isNotNull()
        assertThat(state.creating).isFalse()
        assertThat(state.failed).isFalse()
    }

    @Test
    fun `without a name there is nothing to create`() = runTest {
        for (name in listOf("", "   ")) {
            viewModel.onEvent(OnboardingEvent.NameChanged(name))
            assertThat(state.canCreate, name = "'$name'").isFalse()
            viewModel.onEvent(OnboardingEvent.Create)
        }
        assertThat(households.calls).isEqualTo(emptyList())
    }

    @Test
    fun `a failed create says so, and editing the name clears it`() = runTest {
        households.failure = IllegalStateException("disk full")
        viewModel.onEvent(OnboardingEvent.NameChanged("Home"))
        viewModel.onEvent(OnboardingEvent.Create)
        assertThat(state.failed).isTrue()
        assertThat(state.creating).isFalse()
        // It can be tried again.
        assertThat(state.canCreate).isTrue()
        viewModel.onEvent(OnboardingEvent.NameChanged("Home 2"))
        assertThat(state.failed).isFalse()
        assertThat(state.name).isEqualTo("Home 2")
    }
}
