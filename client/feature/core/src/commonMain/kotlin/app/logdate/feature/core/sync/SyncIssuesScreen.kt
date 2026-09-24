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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.logdate.client.sync.metadata.SyncDeadLetterReason
import app.logdate.client.sync.metadata.SyncDeadLetterRecord
import app.logdate.client.sync.metadata.effectiveReason
import app.logdate.ui.platform.PlatformIcons
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.sync_feedback_up_to_date
import logdate.client.feature.core.generated.resources.sync_issue_count_association
import logdate.client.feature.core.generated.resources.sync_issue_count_draft
import logdate.client.feature.core.generated.resources.sync_issue_count_health
import logdate.client.feature.core.generated.resources.sync_issue_count_journal
import logdate.client.feature.core.generated.resources.sync_issue_count_media
import logdate.client.feature.core.generated.resources.sync_issue_count_note
import logdate.client.feature.core.generated.resources.sync_issue_count_other
import logdate.client.feature.core.generated.resources.sync_issue_discard
import logdate.client.feature.core.generated.resources.sync_issue_discard_description
import logdate.client.feature.core.generated.resources.sync_issue_discard_title
import logdate.client.feature.core.generated.resources.sync_issue_explain_app_closed
import logdate.client.feature.core.generated.resources.sync_issue_explain_failed
import logdate.client.feature.core.generated.resources.sync_issue_explain_file_too_large
import logdate.client.feature.core.generated.resources.sync_issue_explain_missing_file
import logdate.client.feature.core.generated.resources.sync_issue_explain_network_unavailable
import logdate.client.feature.core.generated.resources.sync_issue_explain_server_unavailable
import logdate.client.feature.core.generated.resources.sync_issue_explain_sign_in_required
import logdate.client.feature.core.generated.resources.sync_issue_items_need_attention
import logdate.client.feature.core.generated.resources.sync_issue_missing_file_association
import logdate.client.feature.core.generated.resources.sync_issue_missing_file_draft
import logdate.client.feature.core.generated.resources.sync_issue_missing_file_health
import logdate.client.feature.core.generated.resources.sync_issue_missing_file_journal
import logdate.client.feature.core.generated.resources.sync_issue_missing_file_media
import logdate.client.feature.core.generated.resources.sync_issue_missing_file_note
import logdate.client.feature.core.generated.resources.sync_issue_missing_file_other
import logdate.client.feature.core.generated.resources.sync_issue_retry_failed
import logdate.client.feature.core.generated.resources.sync_issue_retry_requested
import logdate.client.feature.core.generated.resources.sync_issue_review_queue_description
import logdate.client.feature.core.generated.resources.sync_issue_upload_failed_association
import logdate.client.feature.core.generated.resources.sync_issue_upload_failed_draft
import logdate.client.feature.core.generated.resources.sync_issue_upload_failed_health
import logdate.client.feature.core.generated.resources.sync_issue_upload_failed_journal
import logdate.client.feature.core.generated.resources.sync_issue_upload_failed_media
import logdate.client.feature.core.generated.resources.sync_issue_upload_failed_note
import logdate.client.feature.core.generated.resources.sync_issue_upload_failed_other
import logdate.client.feature.core.generated.resources.sync_issues_title
import logdate.client.feature.core.generated.resources.sync_status_waiting
import logdate.client.ui.generated.resources.common_back
import logdate.client.ui.generated.resources.common_cancel
import logdate.client.ui.generated.resources.common_retry
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import logdate.client.ui.generated.resources.Res as UiRes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncIssuesScreen(
    onGoBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SyncIssuesViewModel = koinViewModel(),
) {
    val records by viewModel.records.collectAsStateWithLifecycle()
    val pendingCount by viewModel.pendingCount.collectAsStateWithLifecycle()
    val labels by viewModel.labels.collectAsStateWithLifecycle()
    val retryFeedback by viewModel.retryFeedback.collectAsStateWithLifecycle()
    SyncIssuesContent(
        records = records,
        pendingCount = pendingCount,
        labels = labels,
        retryFeedback = retryFeedback,
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
    pendingCount: Int = 0,
    labels: Map<String, String> = emptyMap(),
    retryFeedback: SyncIssueRetryFeedback? = null,
    onRetry: (String) -> Unit,
    onDiscard: (String) -> Unit,
    onGoBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.sync_issues_title)) },
                navigationIcon = {
                    IconButton(onClick = onGoBack) {
                        Icon(painter = PlatformIcons.back(), contentDescription = stringResource(UiRes.string.common_back))
                    }
                },
            )
        },
    ) { padding ->
        if (records.isEmpty()) {
            EmptyState(pendingCount = pendingCount, retryFeedback = retryFeedback, modifier = Modifier.padding(padding))
        } else {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
                SyncIssuesList(
                    records = records,
                    labels = labels,
                    retryFeedback = retryFeedback,
                    onRetry = onRetry,
                    onDiscard = onDiscard,
                    modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun EmptyState(
    pendingCount: Int,
    retryFeedback: SyncIssueRetryFeedback?,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text =
                if (retryFeedback != null) {
                    stringResource(retryFeedback.messageResource())
                } else if (pendingCount > 0) {
                    pluralStringResource(Res.plurals.sync_status_waiting, pendingCount, pendingCount)
                } else {
                    stringResource(Res.string.sync_feedback_up_to_date)
                },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SyncIssuesList(
    records: List<SyncDeadLetterRecord>,
    labels: Map<String, String>,
    retryFeedback: SyncIssueRetryFeedback?,
    onRetry: (String) -> Unit,
    onDiscard: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = pluralStringResource(Res.plurals.sync_issue_items_need_attention, records.size, records.size),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(Res.string.sync_issue_review_queue_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (retryFeedback != null) {
            item {
                Text(
                    text = stringResource(retryFeedback.messageResource()),
                    style = MaterialTheme.typography.bodyMedium,
                    color =
                        if (retryFeedback ==
                            SyncIssueRetryFeedback.COULD_NOT_START
                        ) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
            }
        }
        items(records, key = { it.id }) { record ->
            SyncIssueCard(
                record = record,
                label = labels[record.id],
                onRetry = { onRetry(record.id) },
                onDiscard = { onDiscard(record.id) },
            )
        }
    }
}

private fun SyncIssueRetryFeedback.messageResource(): StringResource =
    when (this) {
        SyncIssueRetryFeedback.REQUESTED -> Res.string.sync_issue_retry_requested
        SyncIssueRetryFeedback.COULD_NOT_START -> Res.string.sync_issue_retry_failed
    }

@Composable
private fun SyncIssueCard(
    record: SyncDeadLetterRecord,
    label: String?,
    onRetry: () -> Unit,
    onDiscard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmDiscard by remember(record.id) { mutableStateOf(false) }
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(Res.string.sync_issue_discard_title)) },
            text = { Text(stringResource(Res.string.sync_issue_discard_description)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    onDiscard()
                }) { Text(stringResource(Res.string.sync_issue_discard)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(UiRes.string.common_cancel)) }
            },
        )
    }
    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (label != null) {
                Text(text = label, style = MaterialTheme.typography.titleMedium)
            }
            Text(
                text = stringResource(issueMessageFor(record)),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = stringResource(explainIssue(record)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = { confirmDiscard = true }) {
                    Text(stringResource(Res.string.sync_issue_discard))
                }
                Spacer(Modifier.size(8.dp))
                OutlinedButton(onClick = onRetry) {
                    Text(stringResource(UiRes.string.common_retry))
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
internal fun countPluralFor(entityType: String): PluralStringResource =
    when (entityType.uppercase()) {
        "NOTE" -> Res.plurals.sync_issue_count_note
        "JOURNAL" -> Res.plurals.sync_issue_count_journal
        "MEDIA" -> Res.plurals.sync_issue_count_media
        "DRAFT" -> Res.plurals.sync_issue_count_draft
        "ASSOCIATION" -> Res.plurals.sync_issue_count_association
        "HEALTH" -> Res.plurals.sync_issue_count_health
        else -> Res.plurals.sync_issue_count_other
    }

private fun issueMessageFor(record: SyncDeadLetterRecord): StringResource =
    if (record.effectiveReason() == SyncDeadLetterReason.MISSING_FILE) {
        when (record.entityType.uppercase()) {
            "NOTE" -> Res.string.sync_issue_missing_file_note
            "JOURNAL" -> Res.string.sync_issue_missing_file_journal
            "MEDIA" -> Res.string.sync_issue_missing_file_media
            "DRAFT" -> Res.string.sync_issue_missing_file_draft
            "ASSOCIATION" -> Res.string.sync_issue_missing_file_association
            "HEALTH" -> Res.string.sync_issue_missing_file_health
            else -> Res.string.sync_issue_missing_file_other
        }
    } else {
        when (record.entityType.uppercase()) {
            "NOTE" -> Res.string.sync_issue_upload_failed_note
            "JOURNAL" -> Res.string.sync_issue_upload_failed_journal
            "MEDIA" -> Res.string.sync_issue_upload_failed_media
            "DRAFT" -> Res.string.sync_issue_upload_failed_draft
            "ASSOCIATION" -> Res.string.sync_issue_upload_failed_association
            "HEALTH" -> Res.string.sync_issue_upload_failed_health
            else -> Res.string.sync_issue_upload_failed_other
        }
    }

private fun explainIssue(record: SyncDeadLetterRecord): StringResource =
    when (record.effectiveReason()) {
        SyncDeadLetterReason.MISSING_FILE -> Res.string.sync_issue_explain_missing_file
        SyncDeadLetterReason.SERVER_UNAVAILABLE -> Res.string.sync_issue_explain_server_unavailable
        SyncDeadLetterReason.SIGN_IN_REQUIRED -> Res.string.sync_issue_explain_sign_in_required
        SyncDeadLetterReason.APP_CLOSED -> Res.string.sync_issue_explain_app_closed
        SyncDeadLetterReason.NETWORK_UNAVAILABLE -> Res.string.sync_issue_explain_network_unavailable
        SyncDeadLetterReason.FILE_TOO_LARGE -> Res.string.sync_issue_explain_file_too_large
        SyncDeadLetterReason.UNKNOWN -> Res.string.sync_issue_explain_failed
    }
