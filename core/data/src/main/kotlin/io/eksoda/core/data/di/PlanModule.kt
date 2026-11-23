package io.eksoda.core.data.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.eksoda.core.data.repository.BillRepository
import io.eksoda.core.data.repository.BillRepositoryImpl
import io.eksoda.core.data.repository.GoalsRepository
import io.eksoda.core.data.repository.GoalsRepositoryImpl
import io.eksoda.core.data.repository.ManualExpenseRepository
import io.eksoda.core.data.repository.ManualExpenseRepositoryImpl
import io.eksoda.core.data.repository.PriceBoardRepository
import io.eksoda.core.data.repository.PriceBoardRepositoryImpl
import io.eksoda.core.database.EksodaDatabase
import io.eksoda.core.database.dao.BillDao
import io.eksoda.core.database.dao.BudgetDao

/** The month's plan: budgets and limits, bills, expenses typed in by hand, and prices. */
@Module
@InstallIn(SingletonComponent::class)
interface PlanModule {
    @Binds
    fun bindGoalsRepository(implementation: GoalsRepositoryImpl): GoalsRepository

    @Binds
    fun bindBillRepository(implementation: BillRepositoryImpl): BillRepository

    @Binds
    fun bindPriceBoardRepository(implementation: PriceBoardRepositoryImpl): PriceBoardRepository

    @Binds
    fun bindManualExpenseRepository(
        implementation: ManualExpenseRepositoryImpl,
    ): ManualExpenseRepository
}

@Module
@InstallIn(SingletonComponent::class)
object PlanDaoModule {
    @Provides fun budgetDao(database: EksodaDatabase): BudgetDao = database.budgetDao()

    @Provides fun billDao(database: EksodaDatabase): BillDao = database.billDao()
}
