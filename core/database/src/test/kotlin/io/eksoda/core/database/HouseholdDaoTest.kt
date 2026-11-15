package io.eksoda.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import io.eksoda.core.database.entity.HouseholdEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Plain in-memory Room on Robolectric, without SQLCipher (docs/android-standalone.md §8.2). */
@RunWith(AndroidJUnit4::class)
class HouseholdDaoTest {
    private lateinit var database: EksodaDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            EksodaDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `household is null until inserted, then read back`() = runTest {
        val dao = database.householdDao()
        assertThat(dao.observe().first()).isNull()

        val household = HouseholdEntity(
            id = "8a1f0c4e-6d2b-4b1e-9a57-0c2d7f3e9b10",
            name = "Home",
            defaultCurrency = "EUR",
            timezone = "Europe/Athens",
            createdAt = 1_763_200_000_000,
            updatedAt = 1_763_200_000_000,
        )
        dao.insert(household)
        assertThat(dao.observe().first()).isEqualTo(household)

        val renamed = household.copy(name = "Σπίτι", updatedAt = 1_763_200_100_000)
        dao.update(renamed)
        assertThat(dao.observe().first()).isEqualTo(renamed)
    }
}
