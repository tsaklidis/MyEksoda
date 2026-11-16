package io.eksoda.core.data.repository

import io.eksoda.core.database.dao.OptionRow
import io.eksoda.core.database.dao.ReceiptDao
import io.eksoda.core.model.Option
import io.eksoda.engine.text.normalizeText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReceiptOptionsRepositoryImpl @Inject constructor(private val receipts: ReceiptDao) :
    ReceiptOptionsRepository {
    override fun merchantOptions(): Flow<List<Option>> =
        receipts.merchantOptions().map { rows -> rows.map(OptionRow::toModel) }

    override fun storeOptions(merchantId: String): Flow<List<Option>> =
        receipts.storeOptions(merchantId).map { rows -> rows.map(OptionRow::toModel) }

    override fun categoryOptions(): Flow<List<Option>> =
        receipts.categoryOptions().map { rows -> rows.map(OptionRow::toModel) }

    override suspend fun searchProducts(query: String): List<Option> {
        val key = normalizeText(query)
        if (key.isEmpty()) return emptyList()
        return receipts.searchProducts(key).map(OptionRow::toModel)
    }
}

private fun OptionRow.toModel() = Option(id, name)
