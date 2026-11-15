package io.eksoda.core.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import io.eksoda.core.data.db.WriteCoordinator
import io.eksoda.core.data.imports.ImportService
import io.eksoda.core.data.util.Fixed
import io.eksoda.core.database.dao.ImportJobDao
import io.eksoda.core.database.dao.ReceiptDao
import io.eksoda.core.database.dao.ReceiptRow
import io.eksoda.core.database.dao.TombstoneDao
import io.eksoda.core.database.entity.ImportJobEntity
import io.eksoda.core.database.entity.ReceiptItemEntity
import io.eksoda.core.database.entity.TombstoneEntity
import io.eksoda.core.model.ImportJob
import io.eksoda.core.model.ImportOutcome
import io.eksoda.core.model.ImportStatus
import io.eksoda.core.model.ImportSubmission
import io.eksoda.core.model.Merchant
import io.eksoda.core.model.Money
import io.eksoda.core.model.Receipt
import io.eksoda.core.model.ReceiptFilter
import io.eksoda.core.model.ReceiptItem
import io.eksoda.core.model.Store
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
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
    private val clock: Clock,
) : ReceiptRepository {

    /** All receipts, newest first. Search and filters (§6.5, android-app.md §6.4.2) come in L4. */
    override fun getReceipts(filter: ReceiptFilter): Flow<PagingData<Receipt>> =
        Pager(PagingConfig(pageSize = PAGE_SIZE)) { receipts.pagingRows() }.flow
            .map { page -> page.map { row -> row.toModel(emptyList()) } }

    override fun getReceipt(id: String): Flow<Receipt?> =
        combine(receipts.observeRow(id), receipts.observeItems(id)) { row, items ->
            row?.toModel(items)
        }

    override suspend fun importReceipt(url: String): Result<ImportSubmission> = runCatching {
        importService.submit(url)
    }

    override fun getImportJobs(): Flow<List<ImportJob>> = jobs.observeAll().map { list ->
        list.map { it.toModel() }
    }

    override suspend fun retryImport(id: String): Result<Unit> = runCatching {
        check(importService.retry(id)) { "only a failed import can be retried" }
    }

    /**
     * `delete_receipt`: the receipt and its lines go; its import jobs stay, marked
     * `receipt_deleted`, so resubmitting the link imports it again. A tombstone is kept for sync (§6.1).
     */
    override suspend fun deleteReceipt(id: String): Result<Unit> = runCatching {
        coordinator.write {
            val now = clock.millis()
            jobs.markReceiptDeleted(id, now)
            check(receipts.delete(id) == 1) { "no such receipt" }
            tombstones.insert(TombstoneEntity(entity = "receipt", id = id, deletedAt = now))
        }
    }

    private suspend fun ReceiptRow.toModel(items: List<ReceiptItemEntity>): Receipt {
        val zone = householdZone.current()
        val currency = receipt.currency
        return Receipt(
            id = receipt.id,
            merchant = Merchant(id = receipt.merchantId, name = merchantName),
            store = receipt.storeId?.let {
                Store(
                    it,
                    storeName.orEmpty(),
                    storeStreet?.ifEmpty {
                        null
                    },
                    storeCity?.ifEmpty { null },
                )
            },
            issuedAt = OffsetDateTime.ofInstant(Instant.ofEpochMilli(receipt.issuedAt), zone),
            totalAmount = Money(Fixed.toDecimal(receipt.grossTotalMinor, Fixed.MINOR), currency),
            items = items.map { item ->
                ReceiptItem(
                    id = item.id,
                    productId = item.productId,
                    productName = null,
                    description = item.description,
                    quantity = Fixed.toDecimalOrNull(item.quantityE4, Fixed.E4),
                    unit = item.unit,
                    unitPrice = Fixed.toDecimalOrNull(item.unitPriceE4, Fixed.E4)?.let {
                        Money(it, currency)
                    },
                    totalPrice = Money(
                        Fixed.toDecimal(item.grossAmountMinor, Fixed.MINOR),
                        currency,
                    ),
                    isProduct = item.lineType == "product",
                )
            },
            createdAt = OffsetDateTime.ofInstant(Instant.ofEpochMilli(receipt.createdAt), zone),
        )
    }

    private fun ImportJobEntity.toModel() = ImportJob(
        id = id,
        status = ImportStatus.entries.first { it.code == status },
        outcome = outcome?.let { code -> ImportOutcome.entries.firstOrNull { it.code == code } },
        providerCode = providerCode,
        sourceHost = sourceHost,
        createdAt = Instant.ofEpochMilli(createdAt),
        finishedAt = finishedAt?.let(Instant::ofEpochMilli),
        receiptId = receiptId,
        errorCode = lastErrorCode,
    )

    private companion object {
        const val PAGE_SIZE = 25
    }
}
