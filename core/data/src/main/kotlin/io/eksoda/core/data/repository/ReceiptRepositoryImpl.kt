package io.eksoda.core.data.repository

import androidx.paging.Pager
import androidx.paging.PagingData
import androidx.paging.map
import io.eksoda.core.data.db.WriteCoordinator
import io.eksoda.core.data.di.DefaultDispatcher
import io.eksoda.core.data.imports.ImportService
import io.eksoda.core.data.util.Fixed
import io.eksoda.core.data.util.NetworkMonitor
import io.eksoda.core.database.dao.ImportJobDao
import io.eksoda.core.database.dao.ImportJobRow
import io.eksoda.core.database.dao.ReceiptDao
import io.eksoda.core.database.dao.ReceiptItemRow
import io.eksoda.core.database.dao.ReceiptListRow
import io.eksoda.core.database.dao.ReceiptRow
import io.eksoda.core.database.dao.TombstoneDao
import io.eksoda.core.database.entity.TombstoneEntity
import io.eksoda.core.model.DocumentKind
import io.eksoda.core.model.ImportJob
import io.eksoda.core.model.ImportOutcome
import io.eksoda.core.model.ImportStatus
import io.eksoda.core.model.ImportSubmission
import io.eksoda.core.model.IssueTime
import io.eksoda.core.model.LineType
import io.eksoda.core.model.MatchStatus
import io.eksoda.core.model.Merchant
import io.eksoda.core.model.Money
import io.eksoda.core.model.PaymentMethod
import io.eksoda.core.model.Receipt
import io.eksoda.core.model.ReceiptDetail
import io.eksoda.core.model.ReceiptFilter
import io.eksoda.core.model.ReceiptItem
import io.eksoda.core.model.ReceiptPayment
import io.eksoda.core.model.ReceiptWarning
import io.eksoda.core.model.Store
import io.eksoda.core.model.VatAmount
import io.eksoda.engine.ReceiptException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReceiptRepositoryImpl @Inject constructor(
    private val receipts: ReceiptDao,
    private val jobs: ImportJobDao,
    private val tombstones: TombstoneDao,
    private val importService: ImportService,
    private val coordinator: WriteCoordinator,
    private val householdZone: HouseholdZone,
    private val network: NetworkMonitor,
    private val clock: Clock,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : ReceiptRepository {

    override fun getReceipts(filter: ReceiptFilter): Flow<PagingData<Receipt>> {
        val query = ReceiptQuery.list(filter)
        return Pager(listPagingConfig(PAGE_SIZE)) { receipts.pagingRows(query) }.flow
            .map { page ->
                val zone = householdZone.current()
                page.map { it.toModel(zone) }
            }
    }

    override fun getRecentReceipts(limit: Int): Flow<List<Receipt>> =
        receipts.observeRecent(limit).map { rows ->
            val zone = householdZone.current()
            rows.map { it.toModel(zone) }
        }

    /** Parsing `source_data` and mapping the lines run off the main thread. */
    override fun getReceipt(id: String): Flow<ReceiptDetail?> = combine(
        receipts.observeRow(ReceiptQuery.one(id)),
        receipts.observeItems(id),
    ) { row, items ->
        row?.toDetail(items, householdZone.current())
    }.flowOn(defaultDispatcher)

    override suspend fun importReceipt(url: String): Result<ImportSubmission> = runCatching {
        try {
            when (val submission = importService.submit(url)) {
                is ImportSubmission.Queued -> submission.copy(offline = !network.isOnline())
                else -> submission
            }
        } catch (exception: ReceiptException) {
            ImportSubmission.Rejected(exception.code.code)
        }
    }

    override fun getImportJobs(): Flow<List<ImportJob>> =
        jobs.observeAll().map { rows -> rows.map { it.toModel() } }

    override suspend fun retryImport(id: String): Result<Unit> = runCatching {
        check(importService.retry(id)) { "only a failed import can be retried" }
    }

    /**
     * `delete_receipt`: the receipt and its lines go; its import jobs stay, marked
     * `receipt_deleted`, so scanning the receipt again imports it again. A tombstone is kept for
     * sync later.
     */
    override suspend fun deleteReceipt(id: String): Result<Unit> = runCatching {
        coordinator.write {
            val now = clock.millis()
            jobs.markReceiptDeleted(id, now)
            check(receipts.delete(id) == 1) { "no such receipt" }
            tombstones.insert(TombstoneEntity(entity = "receipt", id = id, deletedAt = now))
        }
    }

    override suspend fun setNote(id: String, note: String): Result<Unit> = runCatching {
        val text = note.trim().take(MAX_NOTE_LENGTH)
        coordinator.write { check(receipts.setNote(id, text, clock.millis()) == 1) }
    }

    override suspend fun setExcludedFromAnalytics(id: String, excluded: Boolean): Result<Unit> =
        runCatching {
            coordinator.write { check(receipts.setExcluded(id, excluded, clock.millis()) == 1) }
        }

    override suspend fun setStore(id: String, storeId: String?): Result<Unit> = runCatching {
        coordinator.write {
            check(receipts.setStore(id, storeId, clock.millis()) == 1) {
                "not a store of this merchant"
            }
        }
    }

    override suspend fun originalLink(id: String): String? = receipts.sourceUrl(id)

    // --- mapping --------------------------------------------------------------------------------

    private fun ReceiptRow.toModel(zone: ZoneId): Receipt = ReceiptListRow(
        id = receipt.id,
        merchantId = receipt.merchantId,
        storeId = receipt.storeId,
        issuedAt = receipt.issuedAt,
        issuedLocalDate = receipt.issuedLocalDate,
        currency = receipt.currency,
        grossTotalMinor = receipt.grossTotalMinor,
        paymentMethod = receipt.paymentMethod,
        documentKind = receipt.documentKind,
        status = receipt.status,
        excludedFromAnalytics = receipt.excludedFromAnalytics,
        merchantName = merchantName,
        storeName = storeName,
        storeStreet = storeStreet,
        storeCity = storeCity,
        itemCount = itemCount,
        needsReview = needsReview,
        hasWarnings = hasWarnings,
    ).toModel(zone)

    private fun ReceiptListRow.toModel(zone: ZoneId): Receipt = Receipt(
        id = id,
        merchant = Merchant(id = merchantId, name = merchantName),
        store = storeId?.let {
            Store(
                id = it,
                name = storeName.orEmpty(),
                address = storeStreet?.ifEmpty { null },
                city = storeCity?.ifEmpty { null },
            )
        },
        issuedAt = OffsetDateTime.ofInstant(Instant.ofEpochMilli(issuedAt), zone),
        issuedDate = LocalDate.parse(issuedLocalDate),
        totalAmount = money(grossTotalMinor, currency),
        itemCount = itemCount,
        paymentMethod = PaymentMethod.fromCode(paymentMethod),
        documentKind = DocumentKind.fromCode(documentKind),
        cancelled = status == STATUS_CANCELLED,
        excludedFromAnalytics = excludedFromAnalytics,
        needsReview = needsReview,
        hasWarnings = hasWarnings,
    )

    private fun ReceiptRow.toDetail(items: List<ReceiptItemRow>, zone: ZoneId): ReceiptDetail {
        val source = json.parseToJsonElement(receipt.sourceData).jsonObject
        val coverage = source["coverage"] as? JsonObject
        val currency = receipt.currency
        return ReceiptDetail(
            receipt = toModel(zone),
            issueTime = issueTime(coverage?.get("issue_time")?.jsonPrimitive?.contentOrNull),
            hasLineItems = coverage?.get("line_items")?.jsonPrimitive?.booleanOrNull != false,
            items = items.map { it.toModel(currency) },
            payments = (source["payments"] as? JsonArray).orEmpty().map {
                payment(it.jsonObject, currency)
            },
            vatBreakdown = (source["vat_breakdown"] as? JsonArray).orEmpty().map {
                vatAmount(it.jsonObject, currency)
            },
            warnings = json.parseToJsonElement(receipt.validationWarnings).jsonArray.map {
                warning(it.jsonObject)
            },
            userNote = receipt.userNote,
            documentNumber = receipt.documentNumber,
            documentTypeCode = receipt.documentTypeCode,
            mark = receipt.mark,
            uid = receipt.uid,
            cancellationMark = receipt.cancellationMark,
            providerCode = receipt.providerCode,
            parserVersion = receipt.parserVersion,
        )
    }

    private fun ReceiptItemRow.toModel(currency: String): ReceiptItem = ReceiptItem(
        id = item.id,
        lineNumber = item.lineNumber,
        lineType = LineType.entries.firstOrNull { it.name.equals(item.lineType, ignoreCase = true) }
            ?: LineType.OTHER,
        description = item.description,
        quantity = Fixed.toDecimalOrNull(item.quantityE4, Fixed.E4),
        unit = item.unit,
        unitPrice = Fixed.toDecimalOrNull(item.unitPriceE4, Fixed.E4)?.let { Money(it, currency) },
        totalPrice = money(item.grossAmountMinor, currency),
        matchStatus = MatchStatus.entries
            .firstOrNull { it.name.equals(item.matchStatus, ignoreCase = true) }
            ?: MatchStatus.NOT_A_PRODUCT,
        productId = item.productId,
        productName = productName,
        categorySlug = categorySlug,
        normalizedUnitPrice = Fixed.toDecimalOrNull(item.normalizedUnitPriceE4, Fixed.E4)
            ?.let { Money(it, currency) },
        normalizedUnit = item.normalizedUnit,
    )

    private fun ImportJobRow.toModel() = ImportJob(
        id = id,
        status = ImportStatus.entries.first { it.code == status },
        outcome = outcome?.let { code ->
            ImportOutcome.entries.firstOrNull { it.code == code }
        },
        providerCode = providerCode,
        sourceHost = sourceHost,
        createdAt = Instant.ofEpochMilli(createdAt),
        finishedAt = finishedAt?.let(Instant::ofEpochMilli),
        receiptId = receiptId,
        errorCode = lastErrorCode,
        // A pending job with an error is waiting for its next attempt.
        nextAttemptAt = nextAttemptAt
            ?.takeIf { status == ImportService.STATUS_PENDING && lastErrorCode != null }
            ?.let(Instant::ofEpochMilli),
        merchantName = merchantName,
        total = totalMinor?.let { money(it, currency ?: DEFAULT_CURRENCY) },
    )

    private companion object {
        const val PAGE_SIZE = 40

        /** `user_note` is up to 2000 characters. */
        const val MAX_NOTE_LENGTH = 2000
        const val STATUS_CANCELLED = "cancelled"
        const val DEFAULT_CURRENCY = "EUR"
        val json = Json { ignoreUnknownKeys = true }
    }
}

private fun money(minor: Long, currency: String) =
    Money(Fixed.toDecimal(minor, Fixed.MINOR), currency)

private fun decimal(json: JsonObject, key: String): BigDecimal? =
    json[key]?.jsonPrimitive?.contentOrNull?.let(::BigDecimal)

private fun issueTime(code: String?): IssueTime = when (code) {
    "approximate" -> IssueTime.APPROXIMATE
    "date_only" -> IssueTime.DATE_ONLY
    else -> IssueTime.EXACT
}

private fun payment(payment: JsonObject, currency: String) = ReceiptPayment(
    amount = Money(decimal(payment, "amount") ?: BigDecimal.ZERO, currency),
    method = PaymentMethod.fromCode(payment["method"]?.jsonPrimitive?.contentOrNull),
    label = payment["method_label"]?.jsonPrimitive?.contentOrNull,
    estimated = payment["derived"]?.jsonPrimitive?.booleanOrNull == true,
)

private fun vatAmount(entry: JsonObject, currency: String) = VatAmount(
    rate = decimal(entry, "rate"),
    net = Money(decimal(entry, "net") ?: BigDecimal.ZERO, currency),
    vat = Money(decimal(entry, "vat") ?: BigDecimal.ZERO, currency),
)

private fun warning(issue: JsonObject) = ReceiptWarning(
    code = issue["code"]?.jsonPrimitive?.contentOrNull.orEmpty(),
    lineNumber = issue["line_number"]?.jsonPrimitive?.intOrNull,
)
