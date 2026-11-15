package io.eksoda.core.data.repository

import io.eksoda.core.model.CategorySpending
import io.eksoda.core.model.MonthlyTrend
import io.eksoda.core.model.Period
import io.eksoda.core.model.SpendingSummary

/** Spending analytics computed from the local database (docs/android-standalone.md §6.6, L6). */
interface AnalyticsRepository {
    suspend fun getSummary(period: Period): Result<SpendingSummary>

    suspend fun getMonthlyTrend(months: Int): Result<MonthlyTrend>

    suspend fun getSpendingByCategory(period: Period): Result<CategorySpending>
}
