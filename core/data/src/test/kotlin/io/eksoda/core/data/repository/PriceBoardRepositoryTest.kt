package io.eksoda.core.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import io.eksoda.core.data.imports.ImportHarness
import io.eksoda.core.data.imports.ImportHarness.Companion.KOGIMOU
import io.eksoda.core.data.imports.ImportHarness.Companion.METRO_SAMPLE
import io.eksoda.core.data.imports.ImportHarness.Companion.MYMARKET
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

/** The Prices tab over imported receipts from two shops. Today is 2026-11-22. */
@RunWith(AndroidJUnit4::class)
class PriceBoardRepositoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var app: ImportHarness
    private lateinit var board: PriceBoardRepositoryImpl

    @Before
    fun setUp() = runTest {
        app = ImportHarness(folder.root)
        listOf(MYMARKET, METRO_SAMPLE, KOGIMOU).forEach(app.fetcher::serve)
        app.import(MYMARKET)
        app.import(METRO_SAMPLE)
        app.import(KOGIMOU)
        val database = app.database
        board = PriceBoardRepositoryImpl(
            database.productDao(),
            HouseholdZone(database.householdDao()),
            app.clock,
            app.dispatcher,
        )
    }

    @After
    fun tearDown() {
        if (::app.isInitialized) app.close()
    }

    /** Two priced lines in the same unit at two different shops: (line id, product id). */
    private fun linesAtTwoShops(): Pair<Pair<String, String>, Pair<String, String>> {
        val rows = mutableListOf<List<String>>()
        app.database.openHelper.readableDatabase.query(
            """SELECT i.id, i.product_id, i.normalized_unit, r.merchant_id
               FROM receipt_item i JOIN receipt r ON r.id = i.receipt_id
               WHERE i.product_id IS NOT NULL AND i.normalized_unit_price_e4 > 0
               AND i.normalized_quantity_e6 > 0 AND i.line_type = 'product'""",
        ).use { cursor ->
            while (cursor.moveToNext()) rows += (0..3).map(cursor::getString)
        }
        for (first in rows) {
            val second = rows.firstOrNull { it[2] == first[2] && it[3] != first[3] } ?: continue
            return (first[0] to first[1]) to (second[0] to second[1])
        }
        error("the fixtures have no two priced lines in one unit at two shops")
    }

    @Test
    fun `products bought at one shop each are not compared, but the shops are counted`() = runTest {
        val shown = board.getBoard().first()
        assertThat(shown.currency).isEqualTo("EUR")
        assertThat(shown.byProduct).isEmpty()
        assertThat(shown.byShop).isEmpty()
        assertThat(shown.shops).isGreaterThan(1)
        assertThat(shown.saving.signum()).isEqualTo(0)
    }

    @Test
    fun `the same product at two shops shows where it costs least`() = runTest {
        val (first, second) = linesAtTwoShops()
        // The second line was the same product all along.
        app.database.openHelper.writableDatabase.execSQL(
            "UPDATE receipt_item SET product_id = ? WHERE id = ?",
            arrayOf(first.second, second.first),
        )
        val shown = board.getBoard().first()
        val deal = shown.byProduct.single()
        assertThat(deal.productId).isEqualTo(first.second)
        assertThat(deal.others.size).isEqualTo(1)
        assertThat(deal.cheapest.average <= deal.others.single().average).isTrue()
        assertThat(deal.cheapest.merchant.vatNumber).isNotNull()
        assertThat(shown.byShop.single().merchant).isEqualTo(deal.cheapest.merchant)
        assertThat(shown.saving).isEqualTo(deal.saving)
    }
}
