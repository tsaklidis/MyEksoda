package io.eksoda.feature.scan.imports

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.eksoda.core.designsystem.theme.EksodaTheme
import io.eksoda.core.model.ImportJob
import io.eksoda.core.model.ImportOutcome
import io.eksoda.core.model.ImportStatus
import io.eksoda.core.ui.component.DelayedProgressIndicator
import io.eksoda.core.ui.component.EmptyState
import io.eksoda.core.ui.component.MoneyText
import io.eksoda.core.ui.text.appLocale
import io.eksoda.core.ui.text.dateFormatter
import io.eksoda.core.ui.text.dayLabel
import io.eksoda.core.ui.text.importErrorRes
import io.eksoda.feature.scan.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun ImportListRoute(
    onBack: () -> Unit,
    onReceiptClick: (String) -> Unit,
    viewModel: ImportListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val retryFailed = stringResource(R.string.imports_retry_failed)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                ImportListEffect.RetryFailed -> snackbarHostState.showSnackbar(retryFailed)
            }
        }
    }
    ImportListScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        onReceiptClick = onReceiptClick,
        snackbarHostState = snackbarHostState,
    )
}

/** The imports screen, from the local `import_job` table. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportListScreen(
    state: ImportListUiState,
    onEvent: (ImportListEvent) -> Unit,
    onBack: () -> Unit,
    onReceiptClick: (String) -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.imports_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            FilterRow(selected = state.filter, onSelect = {
                onEvent(ImportListEvent.FilterChanged(it))
            })
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    state.loading -> DelayedProgressIndicator(Modifier.align(Alignment.Center))

                    state.imports.isEmpty() -> EmptyState(
                        title = stringResource(R.string.imports_empty),
                        description = stringResource(R.string.imports_empty_hint),
                        icon = Icons.Default.Inbox,
                    )

                    else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(state.imports, key = { it.id }) { job ->
                            Column(modifier = Modifier.animateItem()) {
                                ImportRow(
                                    job = job,
                                    onRetry = { onEvent(ImportListEvent.Retry(job.id)) },
                                    onOpen = onReceiptClick,
                                )
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterRow(selected: ImportFilter, onSelect: (ImportFilter) -> Unit) {
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ImportFilter.entries.forEach { filter ->
            FilterChip(
                selected = filter == selected,
                onClick = { onSelect(filter) },
                label = { Text(stringResource(filter.label)) },
            )
        }
    }
}

private val ImportFilter.label: Int
    get() = when (this) {
        ImportFilter.ALL -> R.string.imports_filter_all
        ImportFilter.IMPORTING -> R.string.imports_filter_importing
        ImportFilter.FAILED -> R.string.imports_filter_failed
        ImportFilter.DONE -> R.string.imports_filter_done
    }

@Composable
private fun ImportRow(job: ImportJob, onRetry: () -> Unit, onOpen: (String) -> Unit) {
    val deleted = job.outcome == ImportOutcome.RECEIPT_DELETED
    val receiptId = job.receiptId?.takeIf { job.status == ImportStatus.COMPLETED && !deleted }
    ListItem(
        modifier = if (receiptId != null) {
            Modifier.clickable { onOpen(receiptId) }
        } else {
            Modifier
        },
        leadingContent = { StatusIcon(job.status, deleted) },
        headlineContent = { Text(job.merchantName ?: job.sourceHost, maxLines = 1) },
        supportingContent = { ImportDetail(job, deleted) },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = timeLabel(job.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (job.status == ImportStatus.FAILED) {
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.imports_retry)) }
                } else if (receiptId != null) {
                    job.total?.let { MoneyText(money = it) }
                }
            }
        },
    )
}

@Composable
private fun ImportDetail(job: ImportJob, deleted: Boolean) {
    val error = job.errorCode?.let { stringResource(importErrorRes(it)) }
    val retryAt = job.nextAttemptAt
    val text = when {
        deleted -> stringResource(R.string.imports_receipt_deleted)

        job.status == ImportStatus.COMPLETED -> job.sourceHost

        job.status == ImportStatus.FAILED -> error.orEmpty()

        retryAt != null && error != null ->
            stringResource(R.string.imports_retrying, clockTime(retryAt), error)

        else -> stringResource(R.string.imports_importing)
    }
    Text(
        text = text,
        color = if (job.status == ImportStatus.FAILED) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun StatusIcon(status: ImportStatus, deleted: Boolean) {
    when {
        deleted -> Icon(
            Icons.Default.Delete,
            contentDescription = stringResource(R.string.imports_receipt_deleted),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        status == ImportStatus.PENDING -> Icon(
            Icons.Default.HourglassTop,
            contentDescription = stringResource(R.string.imports_filter_importing),
            tint = EksodaTheme.colors.compare,
        )

        status == ImportStatus.COMPLETED -> Icon(
            Icons.Default.CheckCircle,
            contentDescription = stringResource(R.string.imports_filter_done),
            tint = EksodaTheme.colors.ink2,
        )

        else -> Icon(
            Icons.Default.Error,
            contentDescription = stringResource(R.string.imports_filter_failed),
            tint = MaterialTheme.colorScheme.error,
        )
    }
}

/** Today: the time; otherwise the day. Shown in the phone's timezone. */
@Composable
private fun timeLabel(instant: Instant): String {
    val zone = ZoneId.systemDefault()
    val date = instant.atZone(zone).toLocalDate()
    val today = LocalDate.now(zone)
    return if (date == today) clockTime(instant) else dayLabel(date, today)
}

@Composable
private fun clockTime(instant: Instant): String = dateFormatter("HH:mm", appLocale())
    .format(instant.atZone(ZoneId.systemDefault()))
