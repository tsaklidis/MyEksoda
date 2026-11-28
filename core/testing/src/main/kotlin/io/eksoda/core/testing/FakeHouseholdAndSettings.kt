package io.eksoda.core.testing

import io.eksoda.core.data.repository.HouseholdRepository
import io.eksoda.core.data.repository.SettingsRepository
import io.eksoda.core.model.Household
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.math.BigDecimal

/** [HouseholdRepository] in memory: no household until one is created or set. */
class FakeHouseholdRepository(initial: Household? = null) :
    Recorder(),
    HouseholdRepository {
    val current = MutableStateFlow(initial)
    override val household: Flow<Household?> = current

    override suspend fun createHousehold(
        name: String,
        defaultCurrency: String,
        timezone: String,
    ): Result<Household> = answer("createHousehold($name, $defaultCurrency, $timezone)") {
        Household("household", name, defaultCurrency, timezone).also { current.value = it }
    }

    override suspend fun updateHousehold(
        name: String,
        defaultCurrency: String,
        timezone: String,
    ): Result<Unit> = answer("updateHousehold($name, $defaultCurrency, $timezone)") {
        current.value = current.value?.copy(
            name = name,
            defaultCurrency = defaultCurrency,
            timezone = timezone,
        )
    }

    override suspend fun setAnnualIncome(income: BigDecimal?): Result<Unit> =
        answer("setAnnualIncome(${income?.toPlainString()})") {
            current.value = current.value?.copy(annualIncome = income)
        }
}

/** [SettingsRepository] in memory, with the app's defaults. */
class FakeSettingsRepository : SettingsRepository {
    val notify = MutableStateFlow(false)
    val folded = MutableStateFlow<Set<String>>(emptySet())

    override val notifyImports: Flow<Boolean> = notify

    override suspend fun setNotifyImports(enabled: Boolean) {
        notify.value = enabled
    }

    override val foldedInsightsCards: Flow<Set<String>> = folded

    override suspend fun toggleInsightsCardFolded(key: String) {
        folded.value = if (key in folded.value) folded.value - key else folded.value + key
    }
}

/** A household as onboarding creates it. */
fun testHousehold(name: String = "Home", income: BigDecimal? = null): Household =
    Household("household", name, "EUR", "Europe/Athens", annualIncome = income)
