package io.eksoda.core.data.repository

import io.eksoda.core.data.di.DefaultDispatcher
import io.eksoda.core.data.util.Fixed
import io.eksoda.core.database.EksodaDatabase
import io.eksoda.core.database.dao.AnalyticsDao
import io.eksoda.core.database.dao.CategoryDao
import io.eksoda.core.database.dao.HouseholdDao
import io.eksoda.core.database.dao.MerchantTotalRow
import io.eksoda.core.database.dao.TotalsRow
import io.eksoda.core.model.Category
import io.eksoda.core.model.CategoryDetail
import io.eksoda.core.model.CategoryProduct
import io.eksoda.core.model.CategoryShop
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
import io.eksoda.engine.analytics.KeyedTotal
import io.eksoda.engine.analytics.LineTotal
import io.eksoda.engine.analytics.categoryShopTotals
import io.eksoda.engine.analytics.combineCategoryBuckets
import io.eksoda.engine.analytics.runningTotals
import io.eksoda.engine.analytics.totalBySameDay
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext
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
 * The math and model building run on the Default dispatcher, never on the caller's (often Main).
 */
@Singleton
class AnalyticsRepositoryImpl @Inject constructor(
    private val database: EksodaDatabase,
    private val analytics: AnalyticsDao,
    private val categories: CategoryDao,
    private val households: HouseholdDao,
    private val householdZone: HouseholdZone,
    private val clock: Clock,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : AnalyticsRepository {

    override fun changes(): Flow<Unit> = database.invalidationTracker
        .createFlow(
            "receipt",
            "receipt_item",
            "product",
            "category",
            "merchant",
            // Budgets and the annual income (GoalsRepository) feed the same screens.
            "budget",
            "household",
            emitInitialState = false,
        )
        .map { }
        .onStart { emit(Unit) }

    override suspend fun today(): LocalDate = LocalDate.now(clock.withZone(householdZone.current()))

    private suspend fun currency(): String = households.get()?.defaultCurrency ?: DEFAULT_CURRENCY

    /** [block] on the Default dispatcher, its failure as a failed [Result]. */
    private suspend fun <T> compute(block: suspend () -> T): Result<T> =
        withContext(defaultDispatcher) { runCatching { block() } }

    override suspend fun getSummary(period: Period): Result<SpendingSummary> = compute {
        val currency = currency()
        val previous = previousOf(period)
        SpendingSummary(
            currency = currency,
            current = totals(period.start, period.end, currency),
            previous = totals(previous.start, previous.end, currency),
            needsReviewCount = analytics.needsReviewCount(),
        )
    }

    /** The period of equal length that ends the day before [period] starts. */
    private fun previousOf(period: Period): Period.Range {
        val last = period.start.minusDays(1)
        val days = period.end.toEpochDay() - period.start.toEpochDay()
        return Period.Range(last.minusDays(days), last)
    }

    private suspend fun totals(first: LocalDate, last: LocalDate, currency: String): PeriodTotals =
        analytics.totals(first.toString(), last.toString(), currency).toModel(first, last)

    override suspend fun getMonthlyTrend(months: Int): Result<MonthlyTrend> = compute {
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

    override suspend fun getSpendingByCategory(period: Period): Result<CategorySpending> = compute {
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
            it.id to Category(it.id, it.name, it.slug, it.isSystem, it.customName)
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

    override suspend fun getCategoryDetail(
        categoryId: String?,
        period: Period,
    ): Result<CategoryDetail> = compute {
        val currency = currency()
        val category = categoryId?.let { id ->
            val row = checkNotNull(categories.get(id)) { "no category" }
            Category(row.id, row.name, row.slug, row.isSystem, row.customName)
        }
        val start = period.start.toString()
        val end = period.end.toString()
        val current = categoryParts(categoryId, period, currency)
        val previous = previousOf(period)
        val total = current.total
        CategoryDetail(
            category = category,
            from = period.start,
            to = period.end,
            currency = currency,
            total = total,
            share = percentOf(total, money(analytics.totals(start, end, currency).spendingMinor)),
            previousTotal = categoryParts(categoryId, previous, currency).total,
            previousFrom = previous.start,
            previousTo = previous.end,
            products = analytics.categoryProducts(categoryId, start, end, currency).map {
                val amount = money(it.totalMinor)
                CategoryProduct(
                    it.productId,
                    it.name,
                    it.receipts,
                    amount,
                    percentOf(amount, total),
                )
            },
            unitemized = current.unitemized,
            shops = current.shops.map {
                CategoryShop(it.key, it.name, it.total, percentOf(it.total, total))
            },
        )
    }

    /**
     * A category's shops ([categoryShopTotals]) in [period], and the part not on its lines. The
     * uncategorized bucket (null [categoryId]) has no such part: receipts at shops with no default
     * category leave it to "unallocated".
     */
    private suspend fun categoryParts(
        categoryId: String?,
        period: Period,
        currency: String,
    ): CategoryParts {
        val start = period.start.toString()
        val end = period.end.toString()
        val remainders = categoryId
            ?.let { analytics.categoryRemaindersByMerchant(it, start, end, currency) }
            .orEmpty()
            .map { it.keyed() }
        val lines = analytics.categoryLinesByMerchant(categoryId, start, end, currency)
            .map { it.keyed() }
        return CategoryParts(
            shops = categoryShopTotals(lines, remainders),
            unitemized = remainders.fold(BigDecimal.ZERO) { sum, part -> sum + part.total },
        )
    }

    private fun MerchantTotalRow.keyed() = KeyedTotal(merchantId, merchantName, money(totalMinor))

    override suspend fun getSpendingByMerchant(period: Period): Result<List<MerchantSpending>> =
        compute {
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
    ): Result<List<TopProduct>> = compute {
        analytics.topProducts(
            period.start.toString(),
            period.end.toString(),
            currency(),
            byCount,
            limit,
        ).map { TopProduct(it.productId, it.productName, it.receipts, money(it.spendMinor)) }
    }

    override suspend fun getMonthPace(): Result<MonthPace> = compute {
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

    /** One category in a period, by shop; [total] is what its bucket in Insights shows. */
    private class CategoryParts(val shops: List<KeyedTotal>, val unitemized: BigDecimal) {
        val total: BigDecimal = shops.fold(BigDecimal.ZERO) { sum, shop -> sum + shop.total }
    }

    private companion object {
        const val DEFAULT_CURRENCY = "EUR"
    }
}
