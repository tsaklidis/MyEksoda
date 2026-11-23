package io.eksoda.core.data.repository

import io.eksoda.core.data.db.WriteCoordinator
import io.eksoda.core.data.util.Fixed
import io.eksoda.core.database.dao.BillDao
import io.eksoda.core.database.dao.HouseholdDao
import io.eksoda.core.database.dao.TombstoneDao
import io.eksoda.core.database.entity.BillEntity
import io.eksoda.core.database.entity.BillMonthEntity
import io.eksoda.core.database.entity.TombstoneEntity
import io.eksoda.core.model.Bill
import io.eksoda.core.model.BillKind
import io.eksoda.core.model.BillLine
import io.eksoda.core.model.BillsMonth
import io.eksoda.engine.analytics.amountFor
import io.eksoda.engine.analytics.monthEditable
import io.eksoda.engine.analytics.monthsToFreeze
import io.eksoda.engine.sync.SyncSchema.monthScoped
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.YearMonth
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BillRepositoryImpl @Inject constructor(
    private val bills: BillDao,
    private val households: HouseholdDao,
    private val tombstones: TombstoneDao,
    private val analytics: AnalyticsRepository,
    private val coordinator: WriteCoordinator,
    private val clock: Clock,
) : BillRepository {

    override fun getMonth(month: YearMonth): Flow<BillsMonth> =
        combine(bills.observeBills(), bills.observeMonths()) { all, months ->
            val today = analytics.today()
            val byBill = months.groupBy { it.billId }
            val key = month.toString()
            val lines = all
                .filter { it.startMonth <= key && (it.endMonth?.let { end -> key <= end } ?: true) }
                .map { row ->
                    val bill = row.toModel()
                    val rows = byBill[row.id].orEmpty().associate { m ->
                        YearMonth.parse(m.month) to m.amountMinor?.let(::money)
                    }
                    val amount = when (bill.kind) {
                        BillKind.FIXED -> amountFor(month, bill.amount, rows)
                        BillKind.VARIABLE -> rows[month]
                    }
                    BillLine(
                        bill = bill,
                        amount = amount,
                        changed = bill.kind == BillKind.FIXED && month in rows &&
                            amount?.compareTo(bill.amount ?: BigDecimal.ZERO) != 0 &&
                            month == YearMonth.from(today),
                        editable = monthEditable(month, today, bill.kind == BillKind.VARIABLE),
                    )
                }
            BillsMonth(
                currency = households.get()?.defaultCurrency ?: DEFAULT_CURRENCY,
                month = month,
                lines = lines,
                total = lines.mapNotNull { it.amount }.fold(BigDecimal.ZERO, BigDecimal::add),
                toEnter = lines.count { it.amount == null },
                current = month == YearMonth.from(today),
            )
        }

    override suspend fun currentMonth(): YearMonth = YearMonth.from(analytics.today())

    override suspend fun add(name: String, kind: BillKind, amount: BigDecimal?): Result<String> =
        runCatching {
            val text = checkedName(name)
            val minor = checkedAmount(kind, amount)
            coordinator.write {
                val now = clock.millis()
                val id = UUID.randomUUID().toString()
                bills.upsert(
                    BillEntity(
                        id = id,
                        name = text,
                        kind = kind.code,
                        amountMinor = minor,
                        startMonth = currentMonth().toString(),
                        endMonth = null,
                        position = bills.nextPosition(),
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
                id
            }
        }

    override suspend fun update(
        id: String,
        name: String,
        kind: BillKind,
        amount: BigDecimal?,
    ): Result<Unit> = runCatching {
        val text = checkedName(name)
        val minor = checkedAmount(kind, amount)
        coordinator.write {
            val bill = checkNotNull(bills.get(id)) { "no bill" }
            val now = clock.millis()
            val current = currentMonth()
            // What the past months showed stays: a fixed bill's months without their own amount
            // get the old one before it (or the kind) changes.
            if (bill.kind == BillEntity.FIXED &&
                (bill.amountMinor != minor || kind.code != bill.kind)
            ) {
                val withRows = bills.months(id).map { YearMonth.parse(it.month) }.toSet()
                val frozen = monthsToFreeze(YearMonth.parse(bill.startMonth), current, withRows)
                    .map { month -> monthRow(id, month, bill.amountMinor, now) }
                if (frozen.isNotEmpty()) bills.insertMonths(frozen)
            }
            bills.upsert(
                bill.copy(name = text, kind = kind.code, amountMinor = minor, updatedAt = now),
            )
        }
    }

    override suspend fun setAmount(
        id: String,
        month: YearMonth,
        amount: BigDecimal?,
    ): Result<Unit> = runCatching {
        require(amount == null || amount.signum() >= 0) { "an amount can't be negative" }
        require(amount == null || amount <= MAX_AMOUNT) { "the amount is too large" }
        coordinator.write {
            val bill = checkNotNull(bills.get(id)) { "no bill" }
            val variable = bill.kind == BillEntity.VARIABLE
            require(monthEditable(month, analytics.today(), variable)) { "a closed month" }
            val rowId = monthScoped(id, month.toString())
            val now = clock.millis()
            val minor = amount?.let {
                Fixed.toLong(it.setScale(2, RoundingMode.HALF_UP), Fixed.MINOR)
            }
            if (minor == null || (!variable && minor == bill.amountMinor)) {
                if (bills.deleteMonth(rowId) == 1) {
                    tombstones.insert(TombstoneEntity(BILL_MONTH, rowId, now))
                }
            } else {
                bills.upsertMonth(monthRow(id, month, minor, now))
            }
        }
    }

    override suspend fun remove(id: String): Result<Unit> = runCatching {
        coordinator.write {
            val bill = checkNotNull(bills.get(id)) { "no bill" }
            val now = clock.millis()
            val current = currentMonth()
            if (YearMonth.parse(bill.startMonth) < current) {
                // Paid in past months: it stays there, ending last month.
                bills.monthIdsFrom(id, current.toString()).forEach { rowId ->
                    bills.deleteMonth(rowId)
                    tombstones.insert(TombstoneEntity(BILL_MONTH, rowId, now))
                }
                val keep = if (bill.kind == BillEntity.FIXED) {
                    val withRows = bills.months(id).map { YearMonth.parse(it.month) }.toSet()
                    monthsToFreeze(YearMonth.parse(bill.startMonth), current, withRows)
                } else {
                    emptyList()
                }
                if (keep.isNotEmpty()) {
                    bills.insertMonths(keep.map { monthRow(id, it, bill.amountMinor, now) })
                }
                bills.upsert(
                    bill.copy(endMonth = current.minusMonths(1).toString(), updatedAt = now),
                )
            } else {
                bills.delete(id)
                tombstones.insert(TombstoneEntity(BILL, id, now))
            }
        }
    }

    private fun monthRow(id: String, month: YearMonth, minor: Long?, now: Long) = BillMonthEntity(
        id = monthScoped(id, month.toString()),
        billId = id,
        month = month.toString(),
        amountMinor = minor,
        createdAt = now,
        updatedAt = now,
    )

    private fun checkedName(name: String): String {
        val text = name.trim().take(MAX_NAME_LENGTH)
        require(text.isNotEmpty()) { "a bill needs a name" }
        return text
    }

    private fun checkedAmount(kind: BillKind, amount: BigDecimal?): Long? {
        require(kind == BillKind.VARIABLE || amount != null) { "a fixed bill needs its amount" }
        require(amount == null || amount.signum() > 0) { "the amount must be above 0" }
        require(amount == null || amount <= MAX_AMOUNT) { "the amount is too large" }
        return amount?.let { Fixed.toLong(it.setScale(2, RoundingMode.HALF_UP), Fixed.MINOR) }
    }

    private companion object {
        const val BILL = "bill"
        const val BILL_MONTH = "bill_month"
        const val DEFAULT_CURRENCY = "EUR"
        const val MAX_NAME_LENGTH = 60

        /** A million: far above any household bill. */
        val MAX_AMOUNT = BigDecimal("1000000")
    }
}

private val BillKind.code: String
    get() = when (this) {
        BillKind.FIXED -> BillEntity.FIXED
        BillKind.VARIABLE -> BillEntity.VARIABLE
    }

private fun BillEntity.toModel() = Bill(
    id = id,
    name = name,
    kind = if (kind == BillEntity.VARIABLE) BillKind.VARIABLE else BillKind.FIXED,
    amount = amountMinor?.let(::money),
)

private fun money(minor: Long): BigDecimal = Fixed.toDecimal(minor, Fixed.MINOR)
