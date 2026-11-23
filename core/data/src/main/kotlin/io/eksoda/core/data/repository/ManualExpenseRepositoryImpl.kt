package io.eksoda.core.data.repository

import io.eksoda.core.data.db.WriteCoordinator
import io.eksoda.core.data.util.Fixed
import io.eksoda.core.database.dao.CategoryDao
import io.eksoda.core.database.dao.HouseholdDao
import io.eksoda.core.database.dao.ManualReceiptUpdate
import io.eksoda.core.database.dao.MerchantDao
import io.eksoda.core.database.dao.ReceiptDao
import io.eksoda.core.database.entity.MerchantEntity
import io.eksoda.core.database.entity.ReceiptEntity
import io.eksoda.core.model.DocumentKind
import io.eksoda.core.model.ManualExpense
import io.eksoda.core.model.PaymentMethod
import io.eksoda.engine.text.collapseWhitespace
import io.eksoda.engine.text.normalizeText
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manual expenses as receipts without lines. The description becomes a merchant keyed by its
 * normalized text ([ManualExpense.PAYEE_VAT_PREFIX]), so the same one typed on two phones of a
 * household merges into one by sync, and it is suggested next time. The receipt has no mark,
 * UID or source key: it is matched by id only, and its tombstone has no natural key.
 */
@Singleton
class ManualExpenseRepositoryImpl @Inject constructor(
    private val receipts: ReceiptDao,
    private val merchants: MerchantDao,
    private val households: HouseholdDao,
    private val categories: CategoryDao,
    private val coordinator: WriteCoordinator,
    private val householdZone: HouseholdZone,
    private val clock: Clock,
) : ManualExpenseRepository {
    override val descriptions: Flow<List<String>> = receipts.observeManualDescriptions()

    override suspend fun add(expense: ManualExpense): Result<String> = runCatching {
        val checked = checked(expense)
        val zone = householdZone.current()
        coordinator.write {
            val household = checkNotNull(households.get()) { "no household" }
            checkCategory(checked.categoryId)
            val now = clock.millis()
            val merchant = payee(checked.description, now)
            val id = UUID.randomUUID().toString()
            receipts.insert(
                ReceiptEntity(
                    id = id,
                    merchantId = merchant.id,
                    storeId = null,
                    providerCode = PROVIDER,
                    parserVersion = PARSER_VERSION,
                    issuerCountry = merchant.countryCode,
                    issuerVat = merchant.vatNumber,
                    uid = null,
                    mark = null,
                    cancellationMark = null,
                    sourceKey = null,
                    documentKind = DocumentKind.SALE.code,
                    documentTypeCode = null,
                    documentNumber = "",
                    issuedAt = issuedAt(checked.date, zone, now),
                    issuedLocalDate = checked.date.toString(),
                    currency = household.defaultCurrency,
                    grossTotalMinor = Fixed.toLong(checked.amount, Fixed.MINOR),
                    netTotalMinor = null,
                    vatTotalMinor = null,
                    discountTotalMinor = null,
                    // Not "card" or "bank": a typed-in expense never counts toward the e-payment
                    // target, which the tax office checks against real payments.
                    paymentMethod = PaymentMethod.UNKNOWN.code,
                    status = STATUS_ACTIVE,
                    validationWarnings = "[]",
                    userNote = checked.note,
                    sourceUrl = null,
                    sourceUrlFingerprint = "",
                    sourceData = SOURCE_DATA,
                    createdAt = now,
                    updatedAt = now,
                    origin = ReceiptEntity.ORIGIN_MANUAL,
                    categoryId = checked.categoryId,
                    manualKind = checked.kind.code,
                ),
            )
            id
        }
    }

    override suspend fun update(id: String, expense: ManualExpense): Result<Unit> = runCatching {
        val checked = checked(expense)
        val zone = householdZone.current()
        coordinator.write {
            checkCategory(checked.categoryId)
            val now = clock.millis()
            val merchant = payee(checked.description, now)
            check(receipts.isManual(id) == true) { "not a manual expense" }
            receipts.updateManual(
                ManualReceiptUpdate(
                    id = id,
                    merchantId = merchant.id,
                    issuerVat = merchant.vatNumber,
                    categoryId = checked.categoryId,
                    manualKind = checked.kind.code,
                    grossTotalMinor = Fixed.toLong(checked.amount, Fixed.MINOR),
                    issuedAt = issuedAt(checked.date, zone, now),
                    issuedLocalDate = checked.date.toString(),
                    userNote = checked.note,
                    updatedAt = now,
                ),
            )
        }
    }

    private suspend fun checkCategory(id: String?) {
        if (id != null) checkNotNull(categories.get(id)) { "no such category" }
    }

    /** The description's merchant, created on first use. */
    private suspend fun payee(name: String, now: Long): MerchantEntity {
        val vat = ManualExpense.PAYEE_VAT_PREFIX + normalizeText(name).take(MAX_KEY_LENGTH)
        merchants.find(COUNTRY, vat)?.let { return it }
        val created = MerchantEntity(
            id = UUID.randomUUID().toString(),
            countryCode = COUNTRY,
            vatNumber = vat,
            legalName = name,
            displayName = name,
            normalizedDisplayName = normalizeText(name),
            detailsAsOf = null,
            createdAt = now,
            updatedAt = now,
        )
        merchants.insert(created)
        return created
    }

    /**
     * The day's time for the list's order: now when it is today, else midday, so a past expense
     * never moves to another day in any time zone of the household's country.
     */
    private fun issuedAt(date: LocalDate, zone: ZoneId, now: Long): Long {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return if (date == today) {
            now
        } else {
            date.atTime(LocalTime.NOON).atZone(zone).toInstant().toEpochMilli()
        }
    }

    private fun checked(expense: ManualExpense): ManualExpense {
        val description = collapseWhitespace(expense.description).trim()
        require(description.isNotEmpty()) { "a description is needed" }
        require(description.length <= MAX_PAYEE_LENGTH) { "the description is too long" }
        val amount = expense.amount.setScale(2, RoundingMode.HALF_UP)
        require(amount.signum() > 0) { "the amount must be above 0" }
        require(amount <= MAX_AMOUNT) { "the amount is too large" }
        return expense.copy(
            amount = amount,
            description = description,
            note = expense.note.trim().take(MAX_NOTE_LENGTH),
        )
    }

    private companion object {
        const val PROVIDER = "manual"
        const val PARSER_VERSION = "1"
        const val COUNTRY = "GR"
        const val STATUS_ACTIVE = "active"
        const val MAX_PAYEE_LENGTH = 80
        const val MAX_KEY_LENGTH = 100
        const val MAX_NOTE_LENGTH = 2000

        /** Ten million: far above any household expense, far below the column's limit. */
        val MAX_AMOUNT = BigDecimal("10000000")

        /** No lines, the date only: the detail says "totals only" and shows no time. */
        const val SOURCE_DATA = """{"coverage":{"line_items":false,"issue_time":"date_only"}}"""
    }
}
