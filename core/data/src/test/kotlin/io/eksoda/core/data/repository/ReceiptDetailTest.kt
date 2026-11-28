package io.eksoda.core.data.repository

import androidx.paging.testing.asSnapshot
import androidx.test.ext.junit.runners.AndroidJUnit4
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import io.eksoda.core.data.imports.ImportHarness
import io.eksoda.core.data.imports.ImportHarness.Companion.MYMARKET
import io.eksoda.core.model.IssueTime
import io.eksoda.core.model.LineType
import io.eksoda.core.model.MatchStatus
import io.eksoda.core.model.PaymentMethod
import io.eksoda.core.model.ReceiptFilter
import io.eksoda.core.model.ReceiptWarning
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.math.BigDecimal

/**
 * How the receipt screens read what is stored: every field of the detail from the receipt's
 * stored data, including data older versions wrote or another phone sent (the import list is
 * [ImportJobsTest]). Rows are edited in SQL to the shapes the parsers do not produce today.
 */
@RunWith(AndroidJUnit4::class)
class ReceiptDetailTest {
    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var app: ImportHarness
    private lateinit var receiptId: String

    private val repository get() = app.repository
    private val db get() = app.database.openHelper.writableDatabase

    @Before
    fun setUp() = runTest {
        app = ImportHarness(folder.root)
        app.fetcher.serve(MYMARKET)
        receiptId = app.receiptOf(app.import(MYMARKET))
    }

    @After
    fun tearDown() {
        if (::app.isInitialized) app.close()
    }

    private suspend fun detail() = checkNotNull(repository.getReceipt(receiptId).first())

    private fun setSourceData(json: String) =
        db.execSQL("UPDATE receipt SET source_data = ? WHERE id = ?", arrayOf(json, receiptId))

    @Test
    fun `stored data without coverage is an exact time, with its lines`() = runTest {
        setSourceData("{}")
        val detail = detail()
        assertThat(detail.issueTime).isEqualTo(IssueTime.EXACT)
        assertThat(detail.hasLineItems).isTrue()
        assertThat(detail.payments).isEqualTo(emptyList())
        assertThat(detail.vatBreakdown).isEqualTo(emptyList())
    }

    @Test
    fun `the coverage says how exact the time is and whether the lines are printed`() = runTest {
        val cases = mapOf(
            """{"issue_time":"approximate"}""" to (IssueTime.APPROXIMATE to true),
            """{"issue_time":"date_only","line_items":false}""" to (IssueTime.DATE_ONLY to false),
            """{"issue_time":"exact","line_items":true}""" to (IssueTime.EXACT to true),
            """{"issue_time":"something_new"}""" to (IssueTime.EXACT to true),
        )
        for ((coverage, expected) in cases) {
            setSourceData("""{"coverage":$coverage}""")
            val detail = detail()
            assertThat(detail.issueTime to detail.hasLineItems, name = coverage)
                .isEqualTo(expected)
        }
    }

    @Test
    fun `payments and the VAT analysis are read field by field, with what is missing as zero`() =
        runTest {
            setSourceData(
                """
                {"payments":[
                  {"amount":"12.50","method":"card","method_label":"VISA","derived":true},
                  {"method":"cash"},
                  {"amount":"1.00","method":"barter"}
                 ],
                 "vat_breakdown":[
                  {"rate":"13","net":"10.00","vat":"1.30"},
                  {"net":"2.00"}
                 ]}
                """.trimIndent(),
            )
            val detail = detail()
            val (card, cash, unknown) = detail.payments
            assertThat(card.amount.amount).isEqualTo(BigDecimal("12.50"))
            assertThat(card.method).isEqualTo(PaymentMethod.CARD)
            assertThat(card.label).isEqualTo("VISA")
            assertThat(card.estimated).isTrue()
            assertThat(card.amount.currency).isEqualTo("EUR")
            assertThat(cash.amount.amount.signum()).isEqualTo(0)
            assertThat(cash.label).isNull()
            assertThat(cash.estimated).isFalse()
            assertThat(unknown.method).isEqualTo(PaymentMethod.UNKNOWN)

            val (thirteen, noRate) = detail.vatBreakdown
            assertThat(thirteen.rate).isEqualTo(BigDecimal("13"))
            assertThat(thirteen.vat.amount).isEqualTo(BigDecimal("1.30"))
            assertThat(noRate.rate).isNull()
            assertThat(noRate.net.amount).isEqualTo(BigDecimal("2.00"))
            assertThat(noRate.vat.amount.signum()).isEqualTo(0)
        }

    @Test
    fun `stored warnings are listed with their line, when they have one`() = runTest {
        db.execSQL(
            "UPDATE receipt SET validation_warnings = ? WHERE id = ?",
            arrayOf(
                """[{"code":"line_total_mismatch","line_number":3},{"code":"total_mismatch"},{}]""",
                receiptId,
            ),
        )
        assertThat(detail().warnings).containsExactly(
            ReceiptWarning("line_total_mismatch", 3),
            ReceiptWarning("total_mismatch", null),
            ReceiptWarning("", null),
        )
    }

    @Test
    fun `a cancelled receipt says so in the detail and the lists`() = runTest {
        db.execSQL("UPDATE receipt SET status = 'cancelled' WHERE id = ?", arrayOf(receiptId))
        assertThat(detail().receipt.cancelled).isTrue()
        assertThat(repository.getRecentReceipts(1).first().single().cancelled).isTrue()
        assertThat(repository.getReceipts(ReceiptFilter()).asSnapshot().single().cancelled)
            .isTrue()
    }

    @Test
    fun `a receipt from another phone has no link here`() = runTest {
        db.execSQL("UPDATE receipt SET source_url = NULL WHERE id = ?", arrayOf(receiptId))
        val detail = detail()
        assertThat(detail.hasLink).isFalse()
        assertThat(detail.receipt.fromOtherPhone).isTrue()
        assertThat(repository.getReceipts(ReceiptFilter()).asSnapshot().single().fromOtherPhone)
            .isTrue()
        assertThat(repository.originalLink(receiptId)).isNull()
        // Its own phone's receipt keeps its link.
        db.execSQL("UPDATE receipt SET source_url = 'x' WHERE id = ?", arrayOf(receiptId))
        assertThat(detail().hasLink).isTrue()
    }

    @Test
    fun `a store without a street or city shows only its name`() = runTest {
        val storeId = checkNotNull(detail().receipt.store).id
        db.execSQL("UPDATE store SET street = '', city = '' WHERE id = ?", arrayOf(storeId))
        val store = checkNotNull(detail().receipt.store)
        assertThat(store.address).isNull()
        assertThat(store.city).isNull()
        val listed =
            checkNotNull(repository.getReceipts(ReceiptFilter()).asSnapshot().single().store)
        assertThat(listed.address).isNull()
        assertThat(listed.city).isNull()
    }

    @Test
    fun `lines with codes this version does not know are shown as other`() = runTest {
        db.execSQL(
            "UPDATE receipt_item SET line_type = 'voucher', match_status = 'something', " +
                "unit_price_e4 = NULL, quantity_e4 = NULL, normalized_unit_price_e4 = NULL " +
                "WHERE receipt_id = ? AND line_number = 1",
            arrayOf(receiptId),
        )
        db.execSQL(
            "UPDATE receipt_item SET line_type = 'DISCOUNT' WHERE receipt_id = ? AND line_number = 2",
            arrayOf(receiptId),
        )
        val items = detail().items.associateBy { it.lineNumber }
        val first = items.getValue(1)
        assertThat(first.lineType).isEqualTo(LineType.OTHER)
        assertThat(first.matchStatus).isEqualTo(MatchStatus.NOT_A_PRODUCT)
        assertThat(first.unitPrice).isNull()
        assertThat(first.quantity).isNull()
        assertThat(first.normalizedUnitPrice).isNull()
        // Stored codes are matched whatever their case.
        assertThat(items.getValue(2).lineType).isEqualTo(LineType.DISCOUNT)
    }
}
