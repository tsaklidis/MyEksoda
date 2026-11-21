package io.eksoda.core.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import io.eksoda.core.data.imports.Fixtures
import io.eksoda.core.data.imports.ImportHarness
import io.eksoda.core.data.imports.ImportHarness.Companion.MYMARKET
import io.eksoda.core.data.imports.ProductMatcher
import io.eksoda.core.data.products.ReviewService
import io.eksoda.core.database.dao.ReceiptItemRow
import io.eksoda.core.model.CatalogError
import io.eksoda.core.model.CatalogException
import io.eksoda.core.model.LineType
import io.eksoda.core.model.ProductDraft
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.math.BigDecimal

/** The catalog corrections people make, on an imported receipt (METRO, 7 lines). */
@RunWith(AndroidJUnit4::class)
class ProductReviewTest {
    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var app: ImportHarness
    private lateinit var products: ProductRepositoryImpl
    private lateinit var review: ReviewRepositoryImpl
    private lateinit var receiptId: String

    @Before
    fun setUp() = runTest {
        app = ImportHarness(folder.root)
        app.fetcher.serve(MYMARKET)
        receiptId = app.receiptOf(app.import(MYMARKET))
        val dao = app.database.productDao()
        val zone = HouseholdZone(app.database.householdDao())
        val service = ReviewService(
            dao,
            app.database.tombstoneDao(),
            ProductMatcher(dao, app.clock),
            app.clock,
        )
        products =
            ProductRepositoryImpl(dao, service, app.coordinator, zone, app.dispatcher)
        review = ReviewRepositoryImpl(dao, service, app.coordinator, zone)
    }

    @After
    fun tearDown() = app.close()

    /** The receipt's lines by line number. */
    private suspend fun lines(): Map<Int, ReceiptItemRow> = app.database.receiptDao()
        .observeItems(receiptId).first().associateBy { it.item.lineNumber }

    @Test
    fun `a package size gives the product's lines a price per kilo`() = runTest {
        // Line 1: one bar of chocolate, 1.89.
        val line = checkNotNull(lines()[1]).item
        val productId = checkNotNull(line.productId)
        val draft = ProductDraft(
            name = "Crunch λευκή",
            packageQuantity = BigDecimal("250"),
            packageUnit = "g",
        )
        assertThat(products.updateProduct(productId, draft).isSuccess).isTrue()
        val product = checkNotNull(products.getProduct(productId).first())
        assertThat(product.name).isEqualTo("Crunch λευκή")
        assertThat(product.packageQuantity?.toPlainString()).isEqualTo("250")
        // 1.89 for 0.25 kg.
        val priced = checkNotNull(lines()[1]).item
        assertThat(priced.normalizedUnit).isEqualTo("kg")
        assertThat(priced.normalizedUnitPriceE4).isEqualTo(75_600L)
    }

    @Test
    fun `a product draft must have a name and a whole package size`() = runTest {
        val productId = checkNotNull(checkNotNull(lines()[1]).item.productId)
        assertThat(products.updateProduct(productId, ProductDraft(name = "  ")).error())
            .isEqualTo(CatalogError.BLANK_NAME)
        val noUnit = ProductDraft(name = "x", packageQuantity = BigDecimal("250"))
        assertThat(products.updateProduct(productId, noUnit).error())
            .isEqualTo(CatalogError.PACKAGE_INCOMPLETE)
        val zero = ProductDraft(name = "x", packageQuantity = BigDecimal.ZERO, packageUnit = "g")
        assertThat(products.updateProduct(productId, zero).error())
            .isEqualTo(CatalogError.PACKAGE_INCOMPLETE)
        assertThat(products.updateProduct("no-such-product", ProductDraft(name = "x")).error())
            .isEqualTo(CatalogError.NOT_FOUND)
        assertThat(products.createProduct(ProductDraft(name = "Νέο")).isSuccess).isTrue()
    }

    @Test
    fun `merging moves the lines to the target and deletes the sources`() = runTest {
        val target = checkNotNull(checkNotNull(lines()[1]).item.productId)
        val source = checkNotNull(checkNotNull(lines()[2]).item.productId)
        assertThat(products.mergeProducts(target, setOf(source)).isSuccess).isTrue()
        assertThat(checkNotNull(lines()[2]).item.productId).isEqualTo(target)
        assertThat(products.getProduct(source).first()).isNull()
        assertThat(products.getProduct(target).first()?.lineCount).isEqualTo(2)
        // The other phones point their copies at the target.
        assertThat(app.count("SELECT COUNT(*) FROM tombstone WHERE replaced_by IS NOT NULL"))
            .isEqualTo(1)
    }

    @Test
    fun `a merge needs existing sources other than the target, and not too many`() = runTest {
        val target = checkNotNull(checkNotNull(lines()[1]).item.productId)
        assertThat(products.mergeProducts(target, emptySet()).error())
            .isEqualTo(CatalogError.NOT_FOUND)
        assertThat(products.mergeProducts(target, setOf(target)).error())
            .isEqualTo(CatalogError.NOT_FOUND)
        assertThat(products.mergeProducts(target, setOf("no-such-product")).error())
            .isEqualTo(CatalogError.NOT_FOUND)
        assertThat(products.mergeProducts("no-such-product", setOf(target)).error())
            .isEqualTo(CatalogError.NOT_FOUND)
        val many = (0..ReviewService.MAX_MERGE_SOURCES).map { "p$it" }.toSet()
        assertThat(products.mergeProducts(target, many).error())
            .isEqualTo(CatalogError.TOO_MANY_PRODUCTS)
    }

    @Test
    fun `a line linked to another product is remembered for the next import`() = runTest {
        val line = checkNotNull(lines()[1]).item
        val old = checkNotNull(line.productId)
        val other = checkNotNull(checkNotNull(lines()[2]).item.productId)
        // Remembered for the merchant's item: the old product still has its mapping.
        assertThat(review.linkItem(line.id, other).isSuccess).isTrue()
        assertThat(checkNotNull(lines()[1]).item.productId).isEqualTo(other)
        assertThat(products.getProduct(old).first()).isNull()

        // The receipt again: line 1 now matches the linked product.
        app.repository.deleteReceipt(receiptId).getOrThrow()
        app.service.submit(Fixtures.url(MYMARKET))
        app.runWorker(checkNotNull(app.jobs.pending().single()).id)
        receiptId = checkNotNull(app.repository.getRecentReceipts(1).first().single()).id
        assertThat(checkNotNull(lines()[1]).item.productId).isEqualTo(other)
        assertThat(review.linkItem(line.id, "no-such-product").error())
            .isEqualTo(CatalogError.NOT_FOUND)
    }

    @Test
    fun `a line reclassified away from products and back is matched again`() = runTest {
        val bag = checkNotNull(lines()[4]).item
        val product = bag.productId
        assertThat(review.reclassifyItem(bag.id, LineType.FEE).isSuccess).isTrue()
        val fee = checkNotNull(lines()[4]).item
        assertThat(listOf(fee.lineType, fee.matchStatus)).isEqualTo(listOf("fee", "not_a_product"))
        assertThat(fee.productId).isNull()
        // Only a product line can be linked.
        assertThat(review.createProductForItem(bag.id, ProductDraft(name = "Τσάντα")).error())
            .isEqualTo(CatalogError.NOT_A_PRODUCT_LINE)

        assertThat(review.reclassifyItem(bag.id, LineType.PRODUCT).isSuccess).isTrue()
        val again = checkNotNull(lines()[4]).item
        assertThat(again.matchStatus).isEqualTo("matched")
        assertThat(again.productId).isEqualTo(product)
    }

    @Test
    fun `a new product can be made for a line, and a line gets a note`() = runTest {
        val line = checkNotNull(lines()[3]).item
        val draft = ProductDraft(name = "Παξιμάδι χαρουπιού")
        assertThat(review.createProductForItem(line.id, draft).isSuccess).isTrue()
        val linked = checkNotNull(lines()[3])
        assertThat(linked.productName).isEqualTo("Παξιμάδι χαρουπιού")
        assertThat(linked.item.productId).isNotEqualTo(line.productId)

        assertThat(review.setItemNote(line.id, "  for the office  ").isSuccess).isTrue()
        assertThat(checkNotNull(lines()[3]).item.userNote).isEqualTo("for the office")
        assertThat(review.setItemNote("no-such-line", "x").error())
            .isEqualTo(CatalogError.NOT_FOUND)
    }

    @Test
    fun `a product's prices come from its lines`() = runTest {
        // Line 7: 1.918 kg for 7.37.
        val productId = checkNotNull(checkNotNull(lines()[7]).item.productId)
        val prices = products.getPrices(productId, flowOf(null)).first()
        val entry = prices.history.entries.single()
        assertThat(entry.grossAmount.amount.toPlainString()).isEqualTo("7.37")
        assertThat(entry.normalizedUnit).isEqualTo("kg")
        assertThat(prices.history.stats.single().count).isEqualTo(1)
        // One shop: nothing to compare.
        assertThat(prices.comparison.comparable).isEqualTo(false)
        assertThat(prices.comparison.reason).isNotNull()
        val needsReview = "SELECT COUNT(*) FROM receipt_item WHERE match_status = 'needs_review'"
        assertThat(review.getReviewCount().first().toLong()).isEqualTo(app.count(needsReview))
    }
}

private fun Result<*>.error(): CatalogError? = (exceptionOrNull() as? CatalogException)?.error
