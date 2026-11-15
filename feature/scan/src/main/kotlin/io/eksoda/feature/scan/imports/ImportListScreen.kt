package io.eksoda.feature.scan.imports

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Pending
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.eksoda.core.model.ImportJob
import io.eksoda.core.model.ImportStatus
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportListScreen(
    onBackClick: () -> Unit,
    onReceiptClick: (String) -> Unit,
    viewModel: ImportListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recent Imports") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (uiState.isLoading && uiState.imports.isEmpty()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (uiState.imports.isEmpty()) {
                Text(
                    text = "No recent imports found.",
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(uiState.imports) { job ->
                        ImportJobItem(
                            job = job,
                            onRetryClick = { viewModel.retryImport(job.id) },
                            onReceiptClick = { job.receiptId?.let(onReceiptClick) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
fun ImportJobItem(job: ImportJob, onRetryClick: () -> Unit, onReceiptClick: () -> Unit) {
    val timeFormatter =
        remember { DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault()) }

    ListItem(
        headlineContent = {
            Text(
                text = job.sourceHost.ifEmpty { job.providerCode },
                fontWeight = FontWeight.SemiBold,
            )
        },
        supportingContent = {
            Column {
                Text(job.providerCode, maxLines = 1)
                job.errorCode?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = timeFormatter.format(job.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(end = 8.dp),
                )
                StatusIcon(job, onRetryClick, onReceiptClick)
            }
        },
    )
}

@Composable
fun StatusIcon(job: ImportJob, onRetryClick: () -> Unit, onReceiptClick: () -> Unit) {
    when (job.status) {
        ImportStatus.PENDING -> Icon(
            Icons.Default.Pending,
            contentDescription = "Pending",
            tint = Color.Gray,
        )

        ImportStatus.COMPLETED -> IconButton(onClick = onReceiptClick) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = "Completed",
                tint = Color(0xFF2E7D32),
            )
        }

        ImportStatus.FAILED -> IconButton(onClick = onRetryClick) {
            Icon(
                Icons.Default.Refresh,
                contentDescription = "Retry",
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}
