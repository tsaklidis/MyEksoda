package io.eksoda.core.data.repository

import io.eksoda.core.data.db.WriteCoordinator
import io.eksoda.core.database.dao.HouseholdDao
import io.eksoda.core.database.entity.HouseholdEntity
import io.eksoda.core.model.Household
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** The household timezone, for `issued_local_date` and display (§5.3). Europe/Athens until onboarding sets one. */
@Singleton
class HouseholdZone @Inject constructor(private val households: HouseholdDao) {
    suspend fun current(): ZoneId =
        households.observe().first()?.timezone?.let(ZoneId::of) ?: DEFAULT

    companion object {
        val DEFAULT: ZoneId = ZoneId.of("Europe/Athens")
    }
}

@Singleton
class HouseholdRepositoryImpl @Inject constructor(
    private val households: HouseholdDao,
    private val coordinator: WriteCoordinator,
    private val clock: Clock,
) : HouseholdRepository {
    override val household: Flow<Household?> = households.observe().map { it?.toModel() }

    override suspend fun createHousehold(
        name: String,
        defaultCurrency: String,
        timezone: String,
    ): Result<Household> = runCatching {
        ZoneId.of(timezone) // rejects an unknown zone
        coordinator.write {
            check(households.observe().first() == null) { "the household exists already" }
            val now = clock.millis()
            val entity =
                HouseholdEntity(
                    UUID.randomUUID().toString(),
                    name.trim(),
                    defaultCurrency,
                    timezone,
                    now,
                    now,
                )
            households.insert(entity)
            entity.toModel()
        }
    }

    /**
     * Renames or changes the currency or timezone. A timezone change should also recompute
     * `issued_local_date` (§5.3); that comes with household settings (L5).
     */
    override suspend fun updateHousehold(
        name: String,
        defaultCurrency: String,
        timezone: String,
    ): Result<Unit> = runCatching {
        ZoneId.of(timezone)
        coordinator.write {
            val current = checkNotNull(households.observe().first()) { "no household yet" }
            households.update(
                current.copy(
                    name = name.trim(),
                    defaultCurrency = defaultCurrency,
                    timezone = timezone,
                    updatedAt = clock.millis(),
                ),
            )
        }
    }

    private fun HouseholdEntity.toModel() = Household(id, name, defaultCurrency, timezone)
}
