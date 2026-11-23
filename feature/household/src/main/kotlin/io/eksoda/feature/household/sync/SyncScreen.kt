package io.eksoda.feature.household.sync

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import io.eksoda.core.designsystem.component.EksodaButton
import io.eksoda.core.designsystem.component.SectionCard
import io.eksoda.core.designsystem.component.SwitchRow
import io.eksoda.core.designsystem.component.groupedItem
import io.eksoda.core.designsystem.theme.EksodaTheme
import io.eksoda.core.model.SyncEvent
import io.eksoda.core.model.SyncPeer
import io.eksoda.core.model.SyncResult
import io.eksoda.core.model.SyncStart
import io.eksoda.core.model.SyncStatus
import io.eksoda.core.ui.text.dayTimeLabel
import io.eksoda.feature.household.R
import kotlinx.coroutines.flow.flowOf
import java.time.Duration
import java.time.Instant

/** Household sync, opened from Settings. Everything about sync lives on this screen. */
@Composable
fun SyncRoute(onBack: () -> Unit, viewModel: SyncViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val history = viewModel.history.collectAsLazyPagingItems()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            val message = when (effect) {
                is SyncEffect.Joined -> resources.getString(R.string.sync_joined, effect.phoneName)
                SyncEffect.NotACode -> resources.getString(R.string.sync_join_not_a_code)
                SyncEffect.OwnCode -> resources.getString(R.string.sync_join_own_code)
                SyncEffect.JoinFailed -> resources.getString(R.string.sync_join_failed)
                SyncEffect.CodeFailed -> resources.getString(R.string.sync_code_failed)
                SyncEffect.Left -> resources.getString(R.string.sync_left)
                SyncEffect.LeaveFailed -> resources.getString(R.string.sync_leave_failed)
                SyncEffect.SwitchFailed -> resources.getString(R.string.sync_switch_failed)
            }
            snackbarHostState.showSnackbar(message)
        }
    }
    SyncScreen(
        state = state,
        history = history,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        snackbarHostState = snackbarHostState,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncScreen(
    state: SyncUiState,
    history: LazyPagingItems<SyncEvent>,
    onEvent: (SyncScreenEvent) -> Unit,
    onBack: () -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    if (state.scanning) {
        BackHandler { onEvent(SyncScreenEvent.ScanCancelled) }
        JoinScanner(
            onCode = { onEvent(SyncScreenEvent.CodeScanned(it)) },
            onCancel = { onEvent(SyncScreenEvent.ScanCancelled) },
        )
        return
    }
    SyncDialogs(state.dialog, onEvent)
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.sync_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.sync_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            // Small gaps join the history rows into one block; cards add their own space.
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item(key = "switch") {
                SwitchCard(
                    enabled = state.enabled,
                    loading = state.loading,
                    onChange = { onEvent(SyncScreenEvent.EnabledChanged(it)) },
                )
            }
            if (state.enabled && state.hasKey) {
                item(key = "status") {
                    StatusCard(
                        status = state.status,
                        onSyncNow = { onEvent(SyncScreenEvent.SyncNowClicked) },
                        modifier = Modifier.padding(top = CARD_GAP),
                    )
                }
            }
            if (state.enabled) {
                item(key = "phones") {
                    HouseholdCard(
                        peers = state.peers,
                        hasKey = state.hasKey,
                        onEvent = onEvent,
                        modifier = Modifier.padding(top = CARD_GAP),
                    )
                }
            }
            item(key = "history-title") {
                Text(
                    text = stringResource(R.string.sync_history),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .padding(top = CARD_GAP + 8.dp, start = 4.dp, bottom = 8.dp)
                        .semantics { heading() },
                )
            }
            if (history.itemCount == 0 && history.loadState.refresh is LoadState.NotLoading) {
                item(key = "history-empty") {
                    Text(
                        text = stringResource(R.string.sync_history_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = EksodaTheme.colors.ink2,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
            items(count = history.itemCount, key = history.itemKey { it.id }) { index ->
                val event = history[index] ?: return@items
                HistoryRow(
                    event = event,
                    modifier = Modifier
                        .animateItem()
                        .groupedItem(index, history.itemCount),
                )
            }
        }
    }
}

private val CARD_GAP = 12.dp

@Composable
private fun SwitchCard(enabled: Boolean, loading: Boolean, onChange: (Boolean) -> Unit) {
    SectionCard {
        SwitchRow(
            label = stringResource(R.string.sync_switch),
            supporting = stringResource(R.string.sync_switch_hint),
            checked = enabled,
            onCheckedChange = onChange,
            enabled = !loading,
        )
    }
}

/** What sync is doing on this network, and "Sync now". */
@Composable
private fun StatusCard(status: SyncStatus, onSyncNow: () -> Unit, modifier: Modifier = Modifier) {
    val text = when {
        status.syncing -> stringResource(R.string.sync_status_syncing)

        !status.listening -> stringResource(R.string.sync_status_searching)

        status.phonesFound == 0 -> stringResource(R.string.sync_status_none)

        else -> pluralStringResource(
            R.plurals.sync_status_found,
            status.phonesFound,
            status.phonesFound,
        )
    }
    SectionCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = EksodaTheme.colors.ink2,
            )
            EksodaButton(
                onClick = onSyncNow,
                text = stringResource(R.string.sync_now),
                isLoading = status.syncing,
            )
        }
    }
}

/** One sync: the phone, what happened, how it started and how long it took, and when. */
@Composable
private fun HistoryRow(event: SyncEvent, modifier: Modifier = Modifier) {
    val colors = EksodaTheme.colors
    val failed = event.result == SyncResult.FAILED
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ResultIcon(event.result)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                event.peerName ?: stringResource(R.string.sync_no_phone),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.ink,
            )
            Text(
                text = outcome(event),
                style = MaterialTheme.typography.bodySmall,
                color = if (failed) MaterialTheme.colorScheme.error else colors.ink2,
            )
            Text(
                text = startAndDuration(event),
                style = MaterialTheme.typography.bodySmall,
                color = colors.ink2,
            )
        }
        Text(
            dayTimeLabel(event.startedAt),
            style = MaterialTheme.typography.bodySmall,
            color = colors.ink2,
        )
    }
}

@Composable
private fun ResultIcon(result: SyncResult) {
    when (result) {
        SyncResult.DONE -> Icon(
            Icons.Default.CheckCircle,
            contentDescription = stringResource(R.string.sync_result_done),
            tint = EksodaTheme.colors.ink2,
        )

        SyncResult.FAILED -> Icon(
            Icons.Default.Error,
            contentDescription = stringResource(R.string.sync_result_failed),
            tint = MaterialTheme.colorScheme.error,
        )

        SyncResult.RUNNING -> Icon(
            Icons.Default.Sync,
            contentDescription = stringResource(R.string.sync_result_running),
            tint = EksodaTheme.colors.compare,
        )
    }
}

@Composable
private fun outcome(event: SyncEvent): String = when (event.result) {
    SyncResult.RUNNING -> stringResource(R.string.sync_result_running)

    SyncResult.FAILED -> stringResource(syncErrorRes(event.errorCode))

    SyncResult.DONE -> if (event.inserted + event.updated + event.deleted == 0) {
        stringResource(R.string.sync_no_changes)
    } else {
        stringResource(R.string.sync_counts, event.inserted, event.updated, event.deleted)
    }
}

@Composable
private fun startAndDuration(event: SyncEvent): String {
    val started = stringResource(
        when (event.startedBy) {
            SyncStart.AUTO -> R.string.sync_started_auto
            SyncStart.USER -> R.string.sync_started_user
            SyncStart.PEER -> R.string.sync_started_peer
        },
    )
    val finished = event.finishedAt ?: return started
    // At least one second: a quick sync never reads "0 s".
    val seconds = Duration.between(event.startedAt, finished).seconds.coerceAtLeast(1)
    return stringResource(R.string.sync_started_duration, started, seconds)
}

/** The local text for a sync error code; unknown codes get the generic one. */
@StringRes
internal fun syncErrorRes(code: String?): Int = when (code) {
    "peer_unreachable" -> R.string.sync_error_peer_unreachable
    "not_paired" -> R.string.sync_error_not_paired
    "schema_mismatch" -> R.string.sync_error_schema_mismatch
    "permission_denied" -> R.string.sync_error_permission_denied
    "interrupted" -> R.string.sync_error_interrupted
    "merge_failed" -> R.string.sync_error_merge_failed
    "damaged" -> R.string.sync_error_damaged
    "busy" -> R.string.sync_error_busy
    else -> R.string.sync_error_unknown
}

private val PREVIEW_NOW: Instant = Instant.parse("2026-11-19T12:00:00Z")

private val PREVIEW_EVENTS = PREVIEW_NOW.let { now ->
    listOf(
        SyncEvent(
            id = "2",
            peerName = "Pixel 8",
            startedBy = SyncStart.AUTO,
            startedAt = now.minusSeconds(3_600),
            finishedAt = now.minusSeconds(3_597),
            result = SyncResult.DONE,
            errorCode = null,
            inserted = 12,
            updated = 3,
            deleted = 1,
        ),
        SyncEvent(
            id = "1",
            peerName = null,
            startedBy = SyncStart.USER,
            startedAt = now.minusSeconds(90_000),
            finishedAt = now.minusSeconds(89_990),
            result = SyncResult.FAILED,
            errorCode = "peer_unreachable",
            inserted = 0,
            updated = 0,
            deleted = 0,
        ),
        SyncEvent(
            id = "0",
            peerName = "Galaxy A55",
            startedBy = SyncStart.AUTO,
            startedAt = now.minusSeconds(200_000),
            finishedAt = now.minusSeconds(199_998),
            result = SyncResult.DONE,
            errorCode = null,
            inserted = 0,
            updated = 0,
            deleted = 0,
        ),
    )
}

/** The screen; a static preview shows no paged rows, [HistoryRowsPreview] shows those. */
@Preview
@Composable
private fun SyncScreenPreview() {
    val now = PREVIEW_NOW
    EksodaTheme {
        SyncScreen(
            state = SyncUiState(
                loading = false,
                enabled = true,
                hasKey = true,
                status = SyncStatus(listening = true, phonesFound = 1),
                peers = listOf(
                    SyncPeer("d1", "Pixel 8", now, now, now.minusSeconds(3_597)),
                    SyncPeer("d2", "Galaxy A55", now, null, null),
                ),
            ),
            history = flowOf(PagingData.from(PREVIEW_EVENTS)).collectAsLazyPagingItems(),
            onEvent = {},
            onBack = {},
        )
    }
}

@Preview
@Composable
private fun HistoryRowsPreview() {
    EksodaTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            PREVIEW_EVENTS.forEachIndexed { index, event ->
                HistoryRow(event, Modifier.groupedItem(index, PREVIEW_EVENTS.size))
            }
        }
    }
}
