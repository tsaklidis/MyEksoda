package io.eksoda.feature.onboarding

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import org.junit.Test

/** The household page's rules: a name is needed, a budget is optional but must be an amount. */
class OnboardingUiStateTest {
    @Test
    fun `a name is enough to create the household`() {
        val state = OnboardingUiState(name = "Σπίτι")
        assertThat(state.canCreate).isTrue()
        assertThat(state.budgetAmount).isNull()
        assertThat(state.budgetInvalid).isFalse()
    }

    @Test
    fun `without a name nothing is created`() {
        assertThat(OnboardingUiState(name = "  ", budget = "600").canCreate).isFalse()
    }

    @Test
    fun `a budget is read in either convention`() {
        assertThat(
            OnboardingUiState(name = "Home", budget = "1.200,50").budgetAmount?.toPlainString(),
        )
            .isEqualTo("1200.50")
        assertThat(OnboardingUiState(name = "Home", budget = " 600 ").budgetAmount?.toPlainString())
            .isEqualTo("600")
    }

    @Test
    fun `a budget that is not an amount above 0 holds the household back`() {
        for (typed in listOf("abc", "0", "0,00")) {
            val state = OnboardingUiState(name = "Home", budget = typed)
            assertThat(state.budgetInvalid, typed).isTrue()
            assertThat(state.canCreate, typed).isFalse()
        }
    }

    @Test
    fun `nothing is created twice`() {
        assertThat(OnboardingUiState(name = "Home", creating = true).canCreate).isFalse()
    }
}
