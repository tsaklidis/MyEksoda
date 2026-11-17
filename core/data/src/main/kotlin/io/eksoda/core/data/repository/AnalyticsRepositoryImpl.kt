package io.eksoda.core.data.repository

import io.eksoda.core.data.util.Fixed
import io.eksoda.core.database.EksodaDatabase
import io.eksoda.core.database.dao.AnalyticsDao
import io.eksoda.core.database.dao.CategoryDao
import io.eksoda.core.database.dao.HouseholdDao
import io.eksoda.core.database.dao.TotalsRow
import io.eksoda.core.model.Category
import io.eksoda.core.model.CategorySpending
import io.eksoda.core.model.MerchantSpending
import io.eksoda.core.model.MonthPace
import io.eksoda.core.model.MonthTotal
import io.eksoda.core.model.MonthlyTrend
import io.eksoda.core.model.Period
import io.eksoda.core.model.PeriodTotals
import io.eksoda.core.model.SpendingBucket
import io.eksoda.core.model.SpendingBucketKind
import io.eksoda.core.model.SpendingSummary
import io.eksoda.core.model.TopProduct
import io.eksoda.core.model.percentOf
import io.eksoda.engine.analytics.DefaultCategoryTotal
import io.eksoda.engine.analytics.LineTotal
import io.eksoda.engine.analytics.combineCategoryBuckets
import io.eksoda.engine.analytics.runningTotals
import io.eksoda.engine.analytics.totalBySameDay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Spending analytics over the local database: the included receipts are
 * active, not excluded, and in the household's currency; credit notes count as negative.
 */
@Singleton
class AnalyticsRepositoryImpl @Inject constructor(
    private val database: EksodaDatabase,
    private val analytics: AnalyticsDao,
    private val categories: CategoryDao,
    private val households: HouseholdDao,
    private val householdZone: HouseholdZone,
    private val clock: Clock,
) : AnalyticsRepository {

    override fun changes(): Flow<Unit> = database.invalidationTracker
        .createFlow(
            "receipt",
            "receipt_item",
            "product",
            "category",
            "merchant",
            emitInitialState = false,
        )
        .map { }
        .onStart { emit(Unit) }

    override suspend fun today(): LocalDate = LocalDate.now(clock.withZone(householdZone.current()))

    private suspend fun currency(): String =
        households.observe().first()?.defaultCurrency ?: DEFAULT_CURRENCY

    override suspend fun getSummary(period: Period): Result<SpendingSummary> = runCatching {
        val currency = currency()
        val previousLast = period.start.minusDays(1)
        val days = period.end.toEpochDay() - period.start.toEpochDay()
        val previousFirst = previousLast.minusDays(days)
        SpendingSummary(
            currency = currency,
            current = totals(period.start, period.end, currency),
            previous = totals(previousFirst, previousLast, currency),
            needsReviewCount = analytics.needsReviewCount(),
        )
    }

    private suspend fun totals(first: LocalDate, last: LocalDate, currency: String): PeriodTotals =
        analytics.totals(first.toString(), last.toString(), currency).toModel(first, last)

    override suspend fun getMonthlyTrend(months: Int): Result<MonthlyTrend> = runCatching {
        val currency = currency()
        val thisMonth = YearMonth.from(today())
        val firstMonth = thisMonth.minusMonths((months - 1).toLong())
        val rows = analytics.monthTotals(
            firstMonth.atDay(1).toString(),
            thisMonth.atEndOfMonth().toString(),
            currency,
        ).associateBy { YearMonth.parse(it.key) }
        MonthlyTrend(
            currency = currency,
            months = (0 until months).map { index ->
                val month = firstMonth.plusMonths(index.toLong())
                val row = rows[month]
                MonthTotal(month, money(row?.totalMinor ?: 0), row?.receipts ?: 0)
            },
        )
    }

    override suspend fun getSpendingByCategory(period: Period): Result<CategorySpending> =
        runCatching {
            val currency = currency()
            val start = period.start.toString()
            val end = period.end.toString()
            val (buckets, spending) = combineCategoryBuckets(
                lines = analytics.categoryLines(start, end, currency).map {
                    LineTotal(it.lineType, it.categoryId, money(it.totalMinor))
                },
                receiptsByDefault = analytics.receiptTotalsByDefaultCategory(start, end, currency)
                    .map { DefaultCategoryTotal(it.defaultCategoryId, money(it.totalMinor)) },
                linesByDefault = analytics.lineTotalsByDefaultCategory(start, end, currency)
                    .map { DefaultCategoryTotal(it.defaultCategoryId, money(it.totalMinor)) },
            )
            val byId = categories.all().associate {
                it.id to Category(it.id, it.name, it.slug, it.isSystem)
            }
            CategorySpending(
                from = period.start,
                to = period.end,
                currency = currency,
                total = spending,
                buckets = buckets.map { bucket ->
                    SpendingBucket(
                        kind = SpendingBucketKind.entries.first { it.code == bucket.kind },
                        category = bucket.categoryId?.let(byId::get),
                        total = bucket.total,
                        share = percentOf(bucket.total, spending),
                    )
                }.sortedWith(
                    compareByDescending<SpendingBucket> { it.total }
                        .thenBy { it.category?.slug ?: it.kind.code },
                ),
            )
        }

    override suspend fun getSpendingByMerchant(period: Period): Result<List<MerchantSpending>> =
        runCatching {
            val rows = analytics.merchantTotals(
                period.start.toString(),
                period.end.toString(),
                currency(),
            )
            val spending = money(rows.sumOf { it.totalMinor })
            rows.map {
                val total = money(it.totalMinor)
                MerchantSpending(
                    it.merchantId,
                    it.merchantName,
                    total,
                    it.receipts,
                    percentOf(total, spending),
                )
            }
        }

    override suspend fun getTopProducts(
        period: Period,
        byCount: Boolean,
        limit: Int,
    ): Result<List<TopProduct>> = runCatching {
        analytics.topProducts(
            period.start.toString(),
            period.end.toString(),
            currency(),
            byCount,
            limit,
        ).map { TopProduct(it.productId, it.productName, it.receipts, money(it.spendMinor)) }
    }

    override suspend fun getMonthPace(): Result<MonthPace> = runCatching {
        val currency = currency()
        val today = today()
        val month = YearMonth.from(today)
        val previousMonth = month.minusMonths(1)
        val daily = analytics.dayTotals(
            previousMonth.atDay(1).toString(),
            today.toString(),
            currency,
        ).associate { LocalDate.parse(it.key) to money(it.totalMinor) }
        val current = runningTotals(daily, month, through = today)
        val previous = runningTotals(daily, previousMonth).map { it ?: BigDecimal.ZERO }
        MonthPace(
            currency = currency,
            month = month,
            today = today,
            current = current,
            previous = previous,
            previousBySameDay = totalBySameDay(previous, today.dayOfMonth),
        )
    }

    private fun TotalsRow.toModel(first: LocalDate, last: LocalDate) = PeriodTotals(
        from = first,
        to = last,
        spending = money(spendingMinor),
        receiptCount = receiptCount,
        salesCount = salesCount,
        averageSale = if (salesCount == 0) {
            null
        } else {
            money(salesTotalMinor).divide(BigDecimal(salesCount), 2, RoundingMode.HALF_UP)
        },
        creditNotesTotal = money(creditNotesMinor),
        otherCurrencyReceipts = otherCurrencyReceipts,
    )

    private fun money(minor: Long): BigDecimal = Fixed.toDecimal(minor, Fixed.MINOR)

    private companion object {
        const val DEFAULT_CURRENCY = "EUR"
    }
}
