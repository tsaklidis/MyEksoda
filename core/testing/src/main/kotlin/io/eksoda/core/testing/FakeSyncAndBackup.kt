package io.eksoda.core.testing

import android.net.Uri
import androidx.paging.PagingData
import io.eksoda.core.data.repository.BackupRepository
import io.eksoda.core.data.repository.RestorePlan
import io.eksoda.core.data.repository.SyncRepository
import io.eksoda.core.model.BackupSummary
import io.eksoda.core.model.SyncEvent
import io.eksoda.core.model.SyncJoinResult
import io.eksoda.core.model.SyncPeer
import io.eksoda.core.model.SyncStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.Instant

/** [SyncRepository] in memory: off, without a key, until the test or the screen changes it. */
class FakeSyncRepository :
    Recorder(),
    SyncRepository {
    val on = MutableStateFlow(false)
    val key = MutableStateFlow(false)
    val phones = MutableStateFlow<List<SyncPeer>>(emptyList())
    val events = MutableStateFlow<List<SyncEvent>>(emptyList())
    val last = MutableStateFlow<Instant?>(null)
    val now = MutableStateFlow(SyncStatus())

    /** What [join] answers; the codes it was given are in [calls]. */
    var joinResult: SyncJoinResult = SyncJoinResult.Joined("Phone")
    var code: String = "household-code"

    override val enabled: Flow<Boolean> = on

    override suspend fun setEnabled(enabled: Boolean) {
        calls += "setEnabled($enabled)"
        on.value = enabled
        if (enabled) key.value = true
    }

    override val hasHouseholdKey: Flow<Boolean> = key

    override suspend fun pairingCode(): Result<String> = answer("pairingCode()") { code }

    override suspend fun join(text: String): SyncJoinResult {
        calls += "join($text)"
        return joinResult
    }

    override suspend fun leave(): Result<Unit> = answer("leave()") {
        key.value = false
        on.value = false
        phones.value = emptyList()
    }

    override val peers: Flow<List<SyncPeer>> = phones

    override fun getHistory(): Flow<PagingData<SyncEvent>> = events.map { staticPage(it) }

    override val lastSuccess: Flow<Instant?> = last

    override val status: Flow<SyncStatus> = now

    override suspend fun syncNow() {
        calls += "syncNow()"
    }
}

/**
 * [BackupRepository] in memory. Each step answers what the test set ([exportResult],
 * [openResult], [restoreResult]); passphrases are recorded only as their length.
 */
class FakeBackupRepository : BackupRepository {
    var exportResult: Result<BackupSummary> = Result.success(testBackupSummary())
    var openResult: Result<RestorePlan> = Result.success(restorePlan(testBackupSummary()))
    var restoreResult: Result<Unit> = Result.success(Unit)

    /** "export", "open" and "restore", in order. */
    val steps = mutableListOf<String>()

    override suspend fun export(destination: Uri, passphrase: String): Result<BackupSummary> {
        steps += "export"
        return exportResult
    }

    override suspend fun open(source: Uri, passphrase: String): Result<RestorePlan> {
        steps += "open"
        return openResult
    }

    override suspend fun restore(plan: RestorePlan): Result<Unit> {
        steps += "restore"
        return restoreResult
    }

    companion object {
        /**
         * A plan as [open] returns it. Only core:data makes plans (its constructor is internal,
         * so no other code can forge one); a test reaches the constructor by reflection.
         */
        fun restorePlan(summary: BackupSummary): RestorePlan = RestorePlan::class.java
            .getDeclaredConstructor(BackupSummary::class.java, ByteArray::class.java)
            .newInstance(summary, ByteArray(0))
    }
}

fun testBackupSummary(receipts: Int = 2): BackupSummary = BackupSummary(
    createdAt = Instant.parse("2026-11-20T10:00:00Z"),
    householdName = "Home",
    receipts = receipts,
    products = receipts,
    merchants = 1,
    firstIssued = null,
    lastIssued = null,
)
