package io.eksoda.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.eksoda.core.database.dao.OptionRow
import io.eksoda.core.database.dao.ReceiptDao
import io.eksoda.core.model.DocumentKind
import io.eksoda.core.model.Option
import io.eksoda.core.model.ReceiptFilter
import io.eksoda.core.model.ReceiptOrdering
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the filters in the preferences DataStore. That file is not encrypted, so it holds ids,
 * dates, amounts and flags only: merchant, store and product names are read back from the
 * encrypted database (§8.1).
 */
@Singleton
class ReceiptFilterStoreImpl @Inject constructor(
    private val preferences: DataStore<Preferences>,
    private val receipts: ReceiptDao,
    private val householdZone: HouseholdZone,
    private val clock: Clock,
) : ReceiptFilterStore {

    override suspend fun load(): ReceiptFilter {
        val saved = preferences.data.first()[KEY]
            ?.let { runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull() }
            ?: return ReceiptFilter()
        val today = LocalDate.now(clock.withZone(householdZone.current()))
        return withCurrentPeriod(decode(saved), saved.string(MONTH), today)
    }

    override suspend fun save(filter: ReceiptFilter) {
        val month = YearMonth.now(clock.withZone(householdZone.current()))
        val text = encode(filter, month).toString()
        preferences.edit { it[KEY] = text }
    }

    private suspend fun decode(saved: JsonObject): ReceiptFilter {
        val merchant = saved.lookup(MERCHANT) { receipts.merchantOption(it) }
        val store = merchant?.let { m -> saved.lookup(STORE) { receipts.storeOption(it, m.id) } }
        return ReceiptFilter(
            merchant = merchant,
            store = store,
            category = saved.lookup(CATEGORY) { receipts.categoryOption(it) },
            product = saved.lookup(PRODUCT) { receipts.productOption(it) },
            issuedFrom = saved.date(FROM),
            issuedTo = saved.date(TO),
            minTotal = saved.decimal(MIN),
            maxTotal = saved.decimal(MAX),
            needsReview = saved.flag(NEEDS_REVIEW),
            hasWarnings = saved.flag(HAS_WARNINGS),
            documentKind = DocumentKind.entries.firstOrNull { it.code == saved.string(KIND) },
            hideCancelled = saved.flag(HIDE_CANCELLED),
            ordering = ReceiptOrdering.entries.firstOrNull { it.name == saved.string(ORDERING) }
                ?: ReceiptOrdering.NEWEST,
        )
    }

    private companion object {
        val KEY = stringPreferencesKey("receipt_filter")
        const val MONTH = "month"
        const val MERCHANT = "merchant"
        const val STORE = "store"
        const val CATEGORY = "category"
        const val PRODUCT = "product"
        const val FROM = "issued_from"
        const val TO = "issued_to"
        const val MIN = "min_total"
        const val MAX = "max_total"
        const val NEEDS_REVIEW = "needs_review"
        const val HAS_WARNINGS = "has_warnings"
        const val KIND = "document_kind"
        const val HIDE_CANCELLED = "hide_cancelled"
        const val ORDERING = "ordering"

        fun encode(filter: ReceiptFilter, month: YearMonth): JsonObject = buildJsonObject {
            put(MONTH, month.toString())
            filter.merchant?.let { put(MERCHANT, it.id) }
            filter.store?.let { put(STORE, it.id) }
            filter.category?.let { put(CATEGORY, it.id) }
            filter.product?.let { put(PRODUCT, it.id) }
            filter.issuedFrom?.let { put(FROM, it.toString()) }
            filter.issuedTo?.let { put(TO, it.toString()) }
            filter.minTotal?.let { put(MIN, it.toPlainString()) }
            filter.maxTotal?.let { put(MAX, it.toPlainString()) }
            put(NEEDS_REVIEW, filter.needsReview)
            put(HAS_WARNINGS, filter.hasWarnings)
            filter.documentKind?.let { put(KIND, it.code) }
            put(HIDE_CANCELLED, filter.hideCancelled)
            put(ORDERING, filter.ordering.name)
        }

        /**
         * A period saved in an earlier month becomes this month (android-app.md §6.4.2). With no
         * period chosen, the list stays unfiltered by date.
         */
        fun withCurrentPeriod(
            filter: ReceiptFilter,
            savedMonth: String?,
            today: LocalDate,
        ): ReceiptFilter {
            val hasPeriod = filter.issuedFrom != null || filter.issuedTo != null
            if (!hasPeriod || savedMonth == YearMonth.from(today).toString()) return filter
            return filter.copy(
                issuedFrom = today.withDayOfMonth(1),
                issuedTo = today.with(TemporalAdjusters.lastDayOfMonth()),
            )
        }
    }
}

private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

private fun JsonObject.flag(key: String): Boolean = this[key]?.jsonPrimitive?.booleanOrNull == true

private fun JsonObject.date(key: String): LocalDate? =
    string(key)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

private fun JsonObject.decimal(key: String): BigDecimal? =
    string(key)?.let { runCatching { BigDecimal(it) }.getOrNull() }

/** An id saved under [key], looked up in the database; null when it is gone. */
private inline fun JsonObject.lookup(key: String, find: (String) -> OptionRow?): Option? =
    string(key)?.let { find(it) }?.toModel()

private fun OptionRow.toModel() = Option(id, name)
