package io.eksoda.core.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotEmpty
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import io.eksoda.core.data.imports.ImportHarness
import io.eksoda.core.data.imports.ImportHarness.Companion.KOGIMOU
import io.eksoda.core.data.imports.ImportHarness.Companion.METRO_SAMPLE
import io.eksoda.core.data.imports.ImportHarness.Companion.MYMARKET
import io.eksoda.core.model.CatalogError
import io.eksoda.core.model.CatalogException
import io.eksoda.core.model.Category
import io.eksoda.core.model.MerchantSummary
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

/** Categories and merchant edits over three receipts: two from METRO, one from a grill house. */
@RunWith(AndroidJUnit4::class)
class CatalogRepositoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var app: ImportHarness
    private lateinit var catalog: CatalogRepositoryImpl

    @Before
    fun setUp() = runTest {
        app = ImportHarness(folder.root)
        listOf(MYMARKET, METRO_SAMPLE, KOGIMOU).forEach(app.fetcher::serve)
        listOf(MYMARKET, METRO_SAMPLE, KOGIMOU).forEach { app.import(it) }
        val database = app.database
        catalog = CatalogRepositoryImpl(
            database.categoryDao(),
            database.merchantDao(),
            database.tombstoneDao(),
            app.coordinator,
            HouseholdZone(database.householdDao()),
            app.clock,
        )
    }

    @After
    fun tearDown() {
        // A failed setUp keeps its own error.
        if (::app.isInitialized) app.close()
    }

    private suspend fun systemCategory(): Category =
        catalog.getCategoryList().first().first { it.isSystem }

    private suspend fun category(id: String): Category? =
        catalog.getCategoryList().first().firstOrNull { it.id == id }

    /** The merchant with the most receipts: METRO. */
    private suspend fun metro(): MerchantSummary = catalog.getMerchants().first().first()

    /** One of the imported products. */
    private fun someProduct(): String = app.database.openHelper.readableDatabase
        .query("SELECT id FROM product ORDER BY id LIMIT 1").use { cursor ->
            check(cursor.moveToFirst()) { "no products" }
            cursor.getString(0)
        }

    @Test
    fun `the categories are the seeded ones with their product counts`() = runTest {
        val summaries = catalog.getCategories().first()
        assertThat(summaries).isNotEmpty()
        assertThat(summaries.all { it.category.isSystem }).isTrue()
        val counted = summaries.sumOf { it.productCount }.toLong()
        assertThat(counted)
            .isEqualTo(app.count("SELECT COUNT(*) FROM product WHERE category_id IS NOT NULL"))
    }

    @Test
    fun `a new category has a trimmed name that no other category has`() = runTest {
        val id = catalog.createCategory("  Κατοικίδια  ").getOrThrow()
        val created = checkNotNull(category(id))
        assertThat(created.name).isEqualTo("Κατοικίδια")
        assertThat(created.isSystem).isFalse()
        assertThat(catalog.createCategory("κατοικίδια").error()).isEqualTo(CatalogError.NAME_TAKEN)
        assertThat(catalog.createCategory("   ").error()).isEqualTo(CatalogError.BLANK_NAME)
    }

    @Test
    fun `a household category is renamed, its new name checked`() = runTest {
        val pets = catalog.createCategory("Κατοικίδια").getOrThrow()
        val garden = catalog.createCategory("Κήπος").getOrThrow()
        catalog.renameCategory(garden, "Κήπος και φυτά").getOrThrow()
        assertThat(category(garden)?.name).isEqualTo("Κήπος και φυτά")
        // Its own name again is not taken.
        assertThat(catalog.renameCategory(garden, "Κήπος και φυτά").isSuccess).isTrue()

        assertThat(catalog.renameCategory(garden, "Κατοικίδια").error())
            .isEqualTo(CatalogError.NAME_TAKEN)
        assertThat(catalog.renameCategory(pets, " ").error()).isEqualTo(CatalogError.BLANK_NAME)
        assertThat(catalog.renameCategory("no-such-category", "x").error())
            .isEqualTo(CatalogError.NOT_FOUND)
    }

    @Test
    fun `a system category gets the household's name beside its own, and can go back`() = runTest {
        val system = systemCategory()
        catalog.renameCategory(system.id, "  Ψώνια της εβδομάδας ").getOrThrow()
        val renamed = checkNotNull(category(system.id))
        assertThat(renamed.customName).isEqualTo("Ψώνια της εβδομάδας")
        // Its slug and name stay: sync and the translated name depend on them.
        assertThat(renamed.slug).isEqualTo(system.slug)
        assertThat(renamed.name).isEqualTo(system.name)
        assertThat(catalog.getCategoryList().first().first { it.id == system.id }.customName)
            .isEqualTo("Ψώνια της εβδομάδας")

        catalog.renameCategory(system.id, " ").getOrThrow()
        assertThat(category(system.id)?.customName).isNull()
    }

    @Test
    fun `a category in use stays, an unused one is deleted with a tombstone`() = runTest {
        val pets = catalog.createCategory("Κατοικίδια").getOrThrow()
        val product = someProduct()
        app.database.openHelper.writableDatabase.execSQL(
            "UPDATE product SET category_id = ? WHERE id = ?",
            arrayOf(pets, product),
        )
        assertThat(catalog.deleteCategory(pets).error()).isEqualTo(CatalogError.CATEGORY_IN_USE)
        assertThat(catalog.deleteCategory(systemCategory().id).error())
            .isEqualTo(CatalogError.SYSTEM_CATEGORY)
        assertThat(catalog.deleteCategory("no-such-category").error())
            .isEqualTo(CatalogError.NOT_FOUND)

        val garden = catalog.createCategory("Κήπος").getOrThrow()
        catalog.deleteCategory(garden).getOrThrow()
        assertThat(category(garden)).isNull()
        assertThat(app.count("SELECT COUNT(*) FROM tombstone WHERE entity = 'category'"))
            .isEqualTo(1)
    }

    @Test
    fun `merchants are listed by their receipts, with address and stores`() = runTest {
        val merchants = catalog.getMerchants().first()
        assertThat(merchants.map { it.receiptCount }).isEqualTo(listOf(2, 1))
        val metro = merchants.first()
        assertThat(metro.vatNumber).isNotEmpty()
        assertThat(metro.lastReceiptAt).isNotNull()
        assertThat(metro.nameSetByUser).isFalse()
        assertThat(catalog.getMerchant(metro.id).first()).isEqualTo(metro)
        assertThat(catalog.getMerchant("no-such-merchant").first()).isNull()
        val stores = catalog.getStores(metro.id).first()
        assertThat(stores).isNotEmpty()
        assertThat(stores.all { it.label.isNotEmpty() }).isTrue()
    }

    @Test
    fun `a merchant renamed by the user keeps the name, and a blank name resets it`() = runTest {
        val metro = metro()
        catalog.renameMerchant(metro.id, "  Το METRO μας  ").getOrThrow()
        val renamed = checkNotNull(catalog.getMerchant(metro.id).first())
        assertThat(renamed.name).isEqualTo("Το METRO μας")
        assertThat(renamed.nameSetByUser).isTrue()

        catalog.renameMerchant(metro.id, " ").getOrThrow()
        val reset = checkNotNull(catalog.getMerchant(metro.id).first())
        assertThat(reset.nameSetByUser).isFalse()
        assertThat(reset.name).isNotEmpty()
        assertThat(catalog.renameMerchant("no-such-merchant", "x").error())
            .isEqualTo(CatalogError.NOT_FOUND)
    }

    @Test
    fun `a merchant's default category can be set and cleared`() = runTest {
        val metro = metro()
        val pets = catalog.createCategory("Κατοικίδια").getOrThrow()
        catalog.setDefaultCategory(metro.id, pets).getOrThrow()
        val withCategory = checkNotNull(catalog.getMerchant(metro.id).first()).defaultCategory
        assertThat(withCategory?.id).isEqualTo(pets)
        assertThat(withCategory?.name).isEqualTo("Κατοικίδια")
        assertThat(withCategory?.isSystem).isEqualTo(false)

        catalog.setDefaultCategory(metro.id, null).getOrThrow()
        assertThat(catalog.getMerchant(metro.id).first()?.defaultCategory).isNull()
        assertThat(catalog.setDefaultCategory(metro.id, "no-such-category").error())
            .isEqualTo(CatalogError.NOT_FOUND)
        assertThat(catalog.setDefaultCategory("no-such-merchant", pets).error())
            .isEqualTo(CatalogError.NOT_FOUND)
    }
}

private fun Result<*>.error(): CatalogError? = (exceptionOrNull() as? CatalogException)?.error
