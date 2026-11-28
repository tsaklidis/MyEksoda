package io.eksoda.core.testing

import io.eksoda.core.model.BudgetLevel
import io.eksoda.core.model.BudgetProgress
import io.eksoda.core.model.Category
import io.eksoda.core.model.CategorySpending
import io.eksoda.core.model.CategorySummary
import io.eksoda.core.model.DocumentKind
import io.eksoda.core.model.Merchant
import io.eksoda.core.model.MerchantSummary
import io.eksoda.core.model.Money
import io.eksoda.core.model.MonthBudgets
import io.eksoda.core.model.PaymentMethod
import io.eksoda.core.model.PeriodTotals
import io.eksoda.core.model.PriceComparison
import io.eksoda.core.model.PriceHistory
import io.eksoda.core.model.Product
import io.eksoda.core.model.ProductPrices
import io.eksoda.core.model.Receipt
import io.eksoda.core.model.SpendingSummary
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.YearMonth
import java.time.ZoneOffset

// Model values for ViewModel tests: only what a test names differs from a plain default.

fun testReceipt(
    id: String,
    date: String = "2026-11-20",
    total: String = "10.00",
    merchant: String = "Shop",
): Receipt {
    val issued = LocalDate.parse(date)
    return Receipt(
        id = id,
        merchant = Merchant("merchant-$merchant", merchant),
        store = null,
        issuedAt = OffsetDateTime.of(issued.atStartOfDay(), ZoneOffset.ofHours(2)),
        issuedDate = issued,
        totalAmount = Money(BigDecimal(total), "EUR"),
        itemCount = 1,
        paymentMethod = PaymentMethod.CARD,
        documentKind = DocumentKind.SALE,
        cancelled = false,
        excludedFromAnalytics = false,
        needsReview = false,
        hasWarnings = false,
    )
}

fun testCategory(id: String, system: Boolean = false): Category =
    Category(id = id, name = id, slug = id, isSystem = system)

fun testCategorySummary(id: String, products: Int = 0): CategorySummary =
    CategorySummary(testCategory(id), products)

fun testMerchantSummary(id: String, name: String = id): MerchantSummary = MerchantSummary(
    id = id,
    name = name,
    legalName = name,
    vatNumber = "000000000",
    nameSetByUser = false,
    address = null,
    defaultCategory = null,
    receiptCount = 1,
    lastReceiptAt = null,
)

fun testProduct(id: String, name: String = id): Product = Product(
    id = id,
    name = name,
    category = null,
    brand = null,
    packageQuantity = null,
    packageUnit = null,
    lineCount = 1,
)

/** No purchases: an empty history and nothing to compare. */
fun testPrices(): ProductPrices = ProductPrices(
    history = PriceHistory(entries = emptyList(), stats = emptyList()),
    comparison = PriceComparison(
        comparable = false,
        reason = null,
        normalizedUnit = null,
        currency = null,
        merchants = emptyList(),
        availableUnits = emptyList(),
    ),
)

/** [spent] from [from] to [to], against nothing the period before. */
fun testSummary(
    spent: String = "10.00",
    from: String = "2026-11-01",
    to: String = "2026-11-20",
    needsReview: Int = 0,
): SpendingSummary {
    fun totals(amount: String) = PeriodTotals(
        from = LocalDate.parse(from),
        to = LocalDate.parse(to),
        spending = BigDecimal(amount),
        receiptCount = 1,
        salesCount = 1,
        averageSale = null,
        creditNotesTotal = BigDecimal.ZERO,
        otherCurrencyReceipts = 0,
    )
    return SpendingSummary("EUR", totals(spent), totals("0"), needsReview)
}

fun testCategorySpending(total: String = "10.00"): CategorySpending = CategorySpending(
    from = LocalDate.parse("2026-11-01"),
    to = LocalDate.parse("2026-11-20"),
    currency = "EUR",
    total = BigDecimal(total),
    buckets = emptyList(),
)

/** This month's budgets, [over] of them passed by the spending. */
fun testMonthBudgets(over: Int = 0, within: Int = 0): MonthBudgets {
    fun progress(level: BudgetLevel, index: Int) = BudgetProgress(
        category = testCategory("category-$level-$index"),
        budget = BigDecimal.TEN,
        spent = BigDecimal.ONE,
        left = BigDecimal.ONE,
        usedPercent = null,
        level = level,
    )
    return MonthBudgets(
        currency = "EUR",
        month = YearMonth.parse("2026-11"),
        elapsedPercent = BigDecimal.ONE,
        total = null,
        categories = List(over) { progress(BudgetLevel.OVER, it) } +
            List(within) { progress(BudgetLevel.UNDER, it) },
    )
}
