package io.eksoda.core.testing

import androidx.paging.PagingData
import io.eksoda.core.data.repository.ReceiptRepository
import io.eksoda.core.model.ImportJob
import io.eksoda.core.model.ImportStatus
import io.eksoda.core.model.ImportSubmission
import io.eksoda.core.model.Receipt
import io.eksoda.core.model.ReceiptDetail
import io.eksoda.core.model.ReceiptFilter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * [ReceiptRepository] in memory. Tests set what it answers ([submission], [receipts],
 * [details]) and read what it was asked ([submitted], [calls]). Each write succeeds unless
 * [failure] is set, and then fails with it.
 */
class FakeReceiptRepository : ReceiptRepository {
    /** What [importReceipt] answers for a link; a queued import by default. */
    var submission: (String) -> ImportSubmission = { ImportSubmission.Queued("job-$it") }

    /** When set, every suspend call fails with it. */
    var failure: Throwable? = null

    val receipts = MutableStateFlow<List<Receipt>>(emptyList())
    val details = MutableStateFlow<Map<String, ReceiptDetail>>(emptyMap())
    val pending = MutableStateFlow(0)
    var links: Map<String, String> = emptyMap()

    /** Links in the order they were submitted. */
    val submitted = mutableListOf<String>()

    /** Every write, as "name(arguments)", in order. */
    val calls = mutableListOf<String>()

    private fun <T> answer(call: String, value: () -> T): Result<T> {
        calls += call
        return failure?.let { Result.failure(it) } ?: Result.success(value())
    }

    override fun getReceipts(filter: ReceiptFilter): Flow<PagingData<Receipt>> =
        receipts.map { PagingData.from(it) }

    override fun getReceipt(id: String): Flow<ReceiptDetail?> = details.map { it[id] }

    override fun getRecentReceipts(limit: Int): Flow<List<Receipt>> =
        receipts.map { it.take(limit) }

    override suspend fun importReceipt(url: String): Result<ImportSubmission> {
        submitted += url
        return failure?.let { Result.failure(it) } ?: Result.success(submission(url))
    }

    override fun getImportJobs(status: ImportStatus?): Flow<PagingData<ImportJob>> =
        flowOf(PagingData.empty())

    override val pendingImportCount: Flow<Int> = pending

    override suspend fun retryImport(id: String): Result<Unit> = answer("retryImport($id)") {}

    override suspend fun deleteReceipt(id: String): Result<Unit> =
        answer("deleteReceipt($id)") { details.value -= id }

    override suspend fun setNote(id: String, note: String): Result<Unit> =
        answer("setNote($id, $note)") {}

    override suspend fun setExcludedFromAnalytics(id: String, excluded: Boolean): Result<Unit> =
        answer("setExcludedFromAnalytics($id, $excluded)") {}

    override suspend fun setStore(id: String, storeId: String?): Result<Unit> =
        answer("setStore($id, $storeId)") {}

    override suspend fun originalLink(id: String): String? = links[id]
}
