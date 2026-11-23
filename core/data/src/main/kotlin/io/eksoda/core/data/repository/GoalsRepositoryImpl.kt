package io.eksoda.core.data.repository

import io.eksoda.core.data.db.WriteCoordinator
import io.eksoda.core.data.di.DefaultDispatcher
import io.eksoda.core.data.util.Fixed
import io.eksoda.core.database.dao.AnalyticsDao
import io.eksoda.core.database.dao.BudgetDao
import io.eksoda.core.database.dao.CategoryDao
import io.eksoda.core.database.dao.HouseholdDao
import io.eksoda.core.database.dao.TombstoneDao
import io.eksoda.core.database.entity.BudgetEntity
import io.eksoda.core.database.entity.CategoryEntity
import io.eksoda.core.database.entity.TombstoneEntity
import io.eksoda.core.model.BudgetLevel
import io.eksoda.core.model.BudgetProgress
import io.eksoda.core.model.Category
import io.eksoda.core.model.CategorySpending
import io.eksoda.core.model.EPaymentProgress
import io.eksoda.core.model.EverydayBudget
import io.eksoda.core.model.MonthBudgets
import io.eksoda.core.model.Period
import io.eksoda.core.model.SpendingBucketKind
import io.eksoda.engine.analytics.E_PAYMENT_CAP
import io.eksoda.engine.analytics.E_PAYMENT_SHARE
import io.eksoda.engine.analytics.amountFor
import io.eksoda.engine.analytics.budgetUse
import io.eksoda.engine.analytics.ePaymentPace
import io.eksoda.engine.analytics.ePaymentTarget
import io.eksoda.engine.analytics.electronicPartOf
import io.eksoda.engine.analytics.monthElapsedPercent
import io.eksoda.engine.analytics.monthsToFreeze
import io.eksoda.engine.sync.SyncSchema.monthScoped
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import io.eksoda.engine.analytics.BudgetLevel as EngineBudgetLevel

@Singleton
class GoalsRepositoryImpl @Inject constructor(
    private val budgets: BudgetDao,
    private val categories: CategoryDao,
    private val households: HouseholdDao,
    private val tombstones: TombstoneDao,
    private val analyticsDao: AnalyticsDao,
    private val analytics: AnalyticsRepository,
    private val householdZone: HouseholdZone,
    private val coordinator: WriteCoordinator,
    private val clock: Clock,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : GoalsRepository {

    override fun getEveryday(): Flow<EverydayBudget> = budgets.observeAll().map { rows ->
        val month = YearMonth.from(analytics.today())
        val scope = rows.filter { it.categoryId == null }
        val own = scope.firstOrNull { it.month == month.toString() }
        EverydayBudget(
            currency = currency(),
            month = month,
            default = scope.firstOrNull { it.month == null }?.amountMinor?.let(::money),
            changed = own != null,
            thisMonth = own?.amountMinor?.let(::money),
        )
    }

    override suspend fun setEveryday(amount: BigDecimal?): Result<Unit> = setDefault(null, amount)

    override suspend fun setEverydayThisMonth(amount: BigDecimal?): Result<Unit> = runCatching {
        require(amount == null || amount.signum() > 0) { "a budget must be above 0" }
        coordinator.write {
            val month = YearMonth.from(analytics.today())
            val id = monthScoped(BudgetEntity.TOTAL_ID, month.toString())
            val now = clock.millis()
            if (amount == null) {
                if (budgets.delete(id) == 1) tombstones.insert(TombstoneEntity(BUDGET, id, now))
            } else {
                budgets.upsert(
                    BudgetEntity(
                        id = id,
                        categoryId = null,
                        amountMinor = Fixed.toLong(amount, Fixed.MINOR),
                        createdAt = budgets.get(id)?.createdAt ?: now,
                        updatedAt = now,
                        month = month.toString(),
                    ),
                )
            }
        }
    }

    override fun getCategoryLimit(categoryId: String): Flow<BigDecimal?> =
        budgets.observeAll().map { rows ->
            val month = YearMonth.from(analytics.today())
            amountIn(rows.filter { it.categoryId == categoryId }, month)
        }

    override suspend fun setCategoryLimit(categoryId: String, amount: BigDecimal?): Result<Unit> =
        setDefault(categoryId, amount)

    /**
     * Sets the default of [categoryId]'s scope (null: the everyday budget) from this month on.
     * The past months without a row of their own first get one with the old default, so they
     * keep it.
     */
    private suspend fun setDefault(categoryId: String?, amount: BigDecimal?): Result<Unit> =
        runCatching {
            require(amount == null || amount.signum() > 0) { "a budget must be above 0" }
            val scope = categoryId ?: BudgetEntity.TOTAL_ID
            val zone = householdZone.current()
            coordinator.write {
                if (categoryId != null) checkNotNull(categories.get(categoryId)) { "no category" }
                val now = clock.millis()
                val current = YearMonth.from(analytics.today())
                val rows = budgets.scope(categoryId)
                val default = rows.firstOrNull { it.month == null }
                val minor = amount?.let { Fixed.toLong(it, Fixed.MINOR) }
                if (default?.amountMinor == minor) return@write
                val withRows = rows.mapNotNull { row -> row.month?.let(YearMonth::parse) }.toSet()
                val frozen = monthsToFreeze(firstMonth(zone), current, withRows).map { month ->
                    BudgetEntity(
                        id = monthScoped(scope, month.toString()),
                        categoryId = categoryId,
                        amountMinor = default?.amountMinor,
                        createdAt = now,
                        updatedAt = now,
                        month = month.toString(),
                    )
                }
                if (frozen.isNotEmpty()) budgets.insertAll(frozen)
                if (minor == null) {
                    if (budgets.delete(scope) == 1) {
                        tombstones.insert(TombstoneEntity(BUDGET, scope, now))
                    }
                } else {
                    budgets.upsert(
                        BudgetEntity(
                            id = scope,
                            categoryId = categoryId,
                            amountMinor = minor,
                            createdAt = default?.createdAt ?: now,
                            updatedAt = now,
                        ),
                    )
                }
            }
        }

    /** The first month amounts can concern: the household's creation or its oldest receipt. */
    private suspend fun firstMonth(zone: ZoneId): YearMonth {
        val created = households.get()?.createdAt?.let { millis ->
            YearMonth.from(Instant.ofEpochMilli(millis).atZone(zone))
        }
        val firstReceipt = budgets.firstReceiptMonth()?.let(YearMonth::parse)
        return listOfNotNull(created, firstReceipt).minOrNull()
            ?: YearMonth.now(clock.withZone(zone))
    }

    override suspend fun getMonthBudgets(
        month: YearMonth?,
        spending: CategorySpending?,
    ): Result<MonthBudgets> = compute { monthBudgets(month, spending) }

    private suspend fun monthBudgets(asked: YearMonth?, known: CategorySpending?): MonthBudgets {
        val today = analytics.today()
        val current = YearMonth.from(today)
        val month = asked ?: current
        val isCurrent = month == current
        val byScope = budgets.all().groupBy { it.categoryId }
        val totalAmount = byScope[null]?.let { amountIn(it, month) }
        val limits = byScope.mapNotNull { (id, rows) ->
            id?.let { amountIn(rows, month)?.let { amount -> id to amount } }
        }.toMap()
        val spending = if (totalAmount == null && limits.isEmpty()) {
            null
        } else {
            spendingIn(month, if (isCurrent) today else month.atEndOfMonth(), known)
        }
        return MonthBudgets(
            currency = spending?.currency ?: currency(),
            month = month,
            current = isCurrent,
            changed = isCurrent && byScope[null].orEmpty().any { it.month == month.toString() },
            elapsedPercent = if (isCurrent) monthElapsedPercent(today) else HUNDRED,
            total = totalAmount?.let { progress(null, it, spending?.total ?: BigDecimal.ZERO) },
            categories = categoryProgress(limits, spending),
        )
    }

    /** [month]'s spending up to [end]: [known] when it covers just that, else read. */
    private suspend fun spendingIn(
        month: YearMonth,
        end: LocalDate,
        known: CategorySpending?,
    ): CategorySpending {
        val start = month.atDay(1)
        return known?.takeIf { it.from == start && it.to == end }
            ?: analytics.getSpendingByCategory(Period.Range(start, end)).getOrThrow()
    }

    /** Each category with a limit against its spending, the most used first. */
    private suspend fun categoryProgress(
        limits: Map<String, BigDecimal>,
        spending: CategorySpending?,
    ): List<BudgetProgress> {
        if (limits.isEmpty()) return emptyList()
        val spent = spending?.buckets.orEmpty()
            .filter { it.kind == SpendingBucketKind.CATEGORY }
            .mapNotNull { bucket -> bucket.category?.let { it.id to bucket.total } }
            .toMap()
        val byId = categories.all().associateBy { it.id }
        return limits.mapNotNull { (id, limit) ->
            byId[id]?.toModel()?.let { progress(it, limit, spent[id] ?: BigDecimal.ZERO) }
        }.sortedWith(
            compareByDescending<BudgetProgress> { it.usedPercent ?: BigDecimal.ZERO }
                .thenBy { it.category?.slug },
        )
    }

    override suspend fun getEPayments(): Result<EPaymentProgress> = compute {
        val today = analytics.today()
        val household = households.get()
        val currency = household?.defaultCurrency ?: DEFAULT_CURRENCY
        val start = LocalDate.of(today.year, 1, 1).toString()
        val end = today.toString()
        val whole = money(analyticsDao.electronicTotal(start, end, currency))
        // Receipts paid in several ways count with their card and bank part only.
        val split = analyticsDao.mixedPayments(start, end, currency)
            .fold(BigDecimal.ZERO) { sum, row ->
                sum + electronicPartOf(row.sourceData, creditNote = row.documentKind == CREDIT_NOTE)
            }
        val paid = whole + split
        val income = household?.annualIncomeMinor?.let(::money)
        val target = income?.let(::ePaymentTarget)
        val pace = ePaymentPace(target ?: BigDecimal.ZERO, paid, today)
        EPaymentProgress(
            currency = currency,
            year = today.year,
            income = income,
            target = target,
            paid = paid,
            remaining = target?.let { pace.remaining },
            perMonth = target?.let { pace.perMonth },
            projected = pace.projected,
            paidPercent = target?.let { pace.paidPercent },
            sharePercent = E_PAYMENT_SHARE.movePointRight(2),
            cap = E_PAYMENT_CAP,
        )
    }

    /** [block] on the Default dispatcher, its failure as a failed [Result]. */
    private suspend fun <T> compute(block: suspend () -> T): Result<T> =
        withContext(defaultDispatcher) { runCatching { block() } }

    private suspend fun currency(): String = households.get()?.defaultCurrency ?: DEFAULT_CURRENCY

    private fun progress(
        category: Category?,
        budget: BigDecimal,
        spent: BigDecimal,
    ): BudgetProgress {
        val use = budgetUse(budget, spent)
        return BudgetProgress(
            category = category,
            budget = budget,
            spent = spent,
            left = use.left,
            usedPercent = use.usedPercent,
            level = when (use.level) {
                EngineBudgetLevel.UNDER -> BudgetLevel.UNDER
                EngineBudgetLevel.NEAR -> BudgetLevel.NEAR
                EngineBudgetLevel.OVER -> BudgetLevel.OVER
            },
        )
    }

    private companion object {
        val HUNDRED = BigDecimal("100")
        const val BUDGET = "budget"
        const val CREDIT_NOTE = "credit_note"
        const val DEFAULT_CURRENCY = "EUR"
    }
}

private fun money(minor: Long): BigDecimal = Fixed.toDecimal(minor, Fixed.MINOR)

/** What one scope's [rows] (its default and months) hold in [month]; null for none. */
private fun amountIn(rows: List<BudgetEntity>, month: YearMonth): BigDecimal? = amountFor(
    month = month,
    default = rows.firstOrNull { it.month == null }?.amountMinor?.let(::money),
    rows = rows.mapNotNull { row ->
        row.month?.let { YearMonth.parse(it) to row.amountMinor?.let(::money) }
    }.toMap(),
)

private fun CategoryEntity.toModel() = Category(id, name, slug, isSystem, customName)
