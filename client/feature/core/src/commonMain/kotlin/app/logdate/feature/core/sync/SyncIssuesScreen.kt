@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.logdate.client.sync.metadata.SyncDeadLetterRecord
import app.logdate.ui.adaptive.FoldableBookLayout
import app.logdate.ui.platform.PlatformIcons
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncIssuesScreen(
    onGoBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SyncIssuesViewModel = koinViewModel(),
) {
    val records by viewModel.records.collectAsStateWithLifecycle()
    SyncIssuesContent(
        records = records,
        onRetry = viewModel::retry,
        onDiscard = viewModel::discard,
        onGoBack = onGoBack,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncIssuesContent(
    records: List<SyncDeadLetterRecord>,
    onRetry: (String) -> Unit,
    onDiscard: (String) -> Unit,
    onGoBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Sync issues") },
                navigationIcon = {
                    IconButton(onClick = onGoBack) {
                        Icon(painter = PlatformIcons.back(), contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (records.isEmpty()) {
            EmptyState(modifier = Modifier.padding(padding))
        } else {
            FoldableBookLayout(
                modifier = Modifier.fillMaxSize().padding(padding),
                minPaneWidth = 320.dp,
                startPane = {
                    SyncIssuesSummaryPane(
                        records = records,
                        modifier = Modifier.fillMaxSize(),
                    )
                },
                endPane = {
                    SyncIssuesList(
                        records = records,
                        onRetry = onRetry,
                        onDiscard = onDiscard,
                        modifier = Modifier.fillMaxSize(),
                    )
                },
                standardContent = {
                    SyncIssuesList(
                        records = records,
                        onRetry = onRetry,
                        onDiscard = onDiscard,
                        modifier = Modifier.fillMaxSize(),
                    )
                },
            )
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "Everything is synced.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SyncIssuesSummaryPane(
    records: List<SyncDeadLetterRecord>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Review sync queue",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "${records.size} item${if (records.size == 1) "" else "s"} need attention.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text =
                        "Retry items when the source data should still sync. " +
                            "Discard items only when the local change is no longer useful.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        records
            .groupingBy { it.entityType }
            .eachCount()
            .forEach { (entityType, count) ->
                Text(
                    text = "$count ${describeThing(entityType, count)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
    }
}

@Composable
private fun SyncIssuesList(
    records: List<SyncDeadLetterRecord>,
    onRetry: (String) -> Unit,
    onDiscard: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(records, key = { it.id }) { record ->
            SyncIssueCard(
                record = record,
                onRetry = { onRetry(record.id) },
                onDiscard = { onDiscard(record.id) },
            )
        }
    }
}

@Composable
private fun SyncIssueCard(
    record: SyncDeadLetterRecord,
    onRetry: () -> Unit,
    onDiscard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = describeIssue(record),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = explainIssue(record),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDiscard) {
                    Text("Discard")
                }
                Spacer(Modifier.size(8.dp))
                OutlinedButton(onClick = onRetry) {
                    Text("Retry")
                }
            }
        }
    }
}

/**
 * The dead-letter record is written for diagnosis: it holds enum names, an operation, a retry
 * count, and a raw exception message that includes on-disk paths. None of that belongs on screen,
 * so the card is written from the record rather than printing it.
 */
private fun describeThing(
    entityType: String,
    count: Int,
): String {
    val singular =
        when (entityType.uppercase()) {
            "NOTE" -> "entry"
            "JOURNAL" -> "journal"
            "MEDIA" -> "photo or recording"
            "DRAFT" -> "draft"
            "ASSOCIATION" -> "entry link"
            "HEALTH" -> "health record"
            else -> "item"
        }
    return if (count == 1) singular else "${singular}s"
}

private fun describeIssue(record: SyncDeadLetterRecord): String {
    val thing = describeThing(record.entityType, count = 1)
    return if (record.isMissingFile()) {
        "This $thing's file is missing"
    } else {
        "This $thing didn't upload"
    }
}

private fun explainIssue(record: SyncDeadLetterRecord): String =
    if (record.isMissingFile()) {
        "The file it points to is no longer on this device, so there is nothing left to upload. " +
            "Discarding it removes it from the queue and leaves the entry itself alone."
    } else {
        "It was tried several times without success. Retry if you are back online, " +
            "or discard it if you no longer need it synced."
    }

private fun SyncDeadLetterRecord.isMissingFile(): Boolean =
    lastError.contains("no longer exists", ignoreCase = true) ||
        lastError.contains("ENOENT", ignoreCase = true) ||
        lastError.contains("No such file", ignoreCase = true)
