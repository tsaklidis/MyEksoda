package io.eksoda.core.data.repository

import io.eksoda.core.model.Household
import kotlinx.coroutines.flow.Flow

/**
 * The one household in this database. There are no members, roles or invitations
 * (docs/android-standalone.md §6.1 and §11). Null until onboarding creates it.
 */
interface HouseholdRepository {
    val household: Flow<Household?>

    suspend fun createHousehold(name: String, defaultCurrency: String, timezone: String): Result<Household>

    suspend fun updateHousehold(name: String, defaultCurrency: String, timezone: String): Result<Unit>
}
