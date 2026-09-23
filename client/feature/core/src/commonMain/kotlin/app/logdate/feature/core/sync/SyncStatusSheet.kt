@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.SyncProblem
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_MEDIUM_LOWER_BOUND
import app.logdate.client.sync.SyncPausedReason
import app.logdate.ui.platform.PlatformSheet
import app.logdate.ui.theme.Spacing
import app.logdate.util.toReadableDateTimeShort
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.backing_up_progress
import logdate.client.feature.core.generated.resources.last_backed_up_time
import logdate.client.feature.core.generated.resources.never_synced
import logdate.client.feature.core.generated.resources.sync_banner_review
import logdate.client.feature.core.generated.resources.sync_feedback_needs_account
import logdate.client.feature.core.generated.resources.sync_feedback_sign_in_action
import logdate.client.feature.core.generated.resources.sync_feedback_started
import logdate.client.feature.core.generated.resources.sync_feedback_up_to_date
import logdate.client.feature.core.generated.resources.sync_now
import logdate.client.feature.core.generated.resources.sync_paused_background_data_off
import logdate.client.feature.core.generated.resources.sync_paused_background_data_off_fix
import logdate.client.feature.core.generated.resources.sync_paused_media_waiting_for_wifi
import logdate.client.feature.core.generated.resources.sync_paused_offline
import logdate.client.feature.core.generated.resources.sync_paused_signed_out
import logdate.client.feature.core.generated.resources.sync_status_could_not_start
import logdate.client.feature.core.generated.resources.sync_status_failed_items
import logdate.client.feature.core.generated.resources.sync_status_last_attempt_failed
import logdate.client.feature.core.generated.resources.sync_status_open_settings
import logdate.client.feature.core.generated.resources.sync_status_queue_unavailable
import logdate.client.feature.core.generated.resources.sync_status_retrying
import logdate.client.feature.core.generated.resources.sync_status_title
import logdate.client.feature.core.generated.resources.sync_status_waiting
import logdate.client.feature.core.generated.resources.sync_status_waiting_heading
import logdate.client.feature.core.generated.resources.syncing
import logdate.client.feature.core.generated.resources.syncing_remaining
import logdate.client.ui.generated.resources.common_done
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import logdate.client.ui.generated.resources.Res as UiRes

const val SYNC_STATUS_SHEET_TAG = "logdate_sync_status_sheet"

/**
 * What is waiting to back up, and why it isn't moving. Opened from the backup status button in
 * the timeline's top app bar.
 *
 * A bottom sheet on phones, a dialog once the window is wide enough that a sheet would stretch
 * across the whole screen.
 */
@Composable
fun SyncStatusSheet(
    onDismiss: () -> Unit,
    onOpenSyncSettings: () -> Unit,
    onOpenSyncIssues: () -> Unit,
    onSignIn: () -> Unit,
    viewModel: SyncStatusViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val feedback by viewModel.feedback.collectAsStateWithLifecycle()
    SyncStatusSurface(
        uiState = uiState,
        feedback = feedback,
        onDismiss = onDismiss,
        onSyncNow = viewModel::syncNow,
        onOpenSyncSettings = {
            onDismiss()
            onOpenSyncSettings()
        },
        onOpenSyncIssues = {
            onDismiss()
            onOpenSyncIssues()
        },
        onSignIn = {
            onDismiss()
            onSignIn()
        },
    )
}

/** [SyncStatusSheet] without its view model. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncStatusSurface(
    uiState: SyncStatusUiState,
    feedback: SyncStatusFeedback?,
    onDismiss: () -> Unit,
    onSyncNow: () -> Unit,
    onOpenSyncSettings: () -> Unit,
    onOpenSyncIssues: () -> Unit,
    onSignIn: () -> Unit,
) {
    val content: @Composable (Modifier) -> Unit = { modifier ->
        SyncStatusContent(
            uiState = uiState,
            feedback = feedback,
            onSyncNow = onSyncNow,
            onOpenSyncSettings = onOpenSyncSettings,
            onOpenSyncIssues = onOpenSyncIssues,
            onSignIn = onSignIn,
            modifier = modifier.verticalScroll(rememberScrollState()),
        )
    }
    val isWide =
        currentWindowAdaptiveInfoV2().windowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_MEDIUM_LOWER_BOUND)
    if (isWide) {
        BasicAlertDialog(
            onDismissRequest = onDismiss,
            modifier = Modifier.widthIn(max = 480.dp).testTag(SYNC_STATUS_SHEET_TAG),
        ) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                tonalElevation = AlertDialogDefaults.TonalElevation,
                color = AlertDialogDefaults.containerColor,
            ) {
                Column(modifier = Modifier.padding(Spacing.xl)) {
                    content(Modifier.weight(1f, fill = false))
                    TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                        Text(stringResource(UiRes.string.common_done))
                    }
                }
            }
        }
    } else {
        PlatformSheet(
            onDismissRequest = onDismiss,
            modifier = Modifier.testTag(SYNC_STATUS_SHEET_TAG),
        ) {
            content(Modifier.padding(horizontal = Spacing.lg).padding(bottom = Spacing.xl))
        }
    }
}

/**
 * Everything the sheet and the dialog show, without the container: the title, what is happening,
 * why a backup is paused, what to do about it, and what is waiting.
 */
@Composable
fun SyncStatusContent(
    uiState: SyncStatusUiState,
    feedback: SyncStatusFeedback?,
    onSyncNow: () -> Unit,
    onOpenSyncSettings: () -> Unit,
    onOpenSyncIssues: () -> Unit,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(Res.string.sync_status_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = Spacing.md),
        )
        SyncStatusBody(
            uiState = uiState,
            feedback = feedback,
            onSyncNow = onSyncNow,
            onOpenSyncSettings = onOpenSyncSettings,
            onOpenSyncIssues = onOpenSyncIssues,
            onSignIn = onSignIn,
        )
    }
}

@Composable
private fun ColumnScope.SyncStatusBody(
    uiState: SyncStatusUiState,
    feedback: SyncStatusFeedback?,
    onSyncNow: () -> Unit,
    onOpenSyncSettings: () -> Unit,
    onOpenSyncIssues: () -> Unit,
    onSignIn: () -> Unit,
) {
    StatusHeadline(uiState)

    val pausedReason = uiState.pausedReason
    if (pausedReason != null) {
        PausedReason(pausedReason, modifier = Modifier.padding(top = Spacing.sm))
    } else if (uiState.lastAttemptFailed && !uiState.isSyncing) {
        Text(
            text = stringResource(Res.string.sync_status_last_attempt_failed),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.sm),
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (pausedReason == SyncPausedReason.NOT_SIGNED_IN) {
            Button(onClick = onSignIn) { Text(stringResource(Res.string.sync_feedback_sign_in_action)) }
        } else {
            Button(onClick = onSyncNow, enabled = !uiState.isSyncing) {
                Text(stringResource(Res.string.sync_now))
            }
        }
        TextButton(onClick = onOpenSyncSettings) {
            Text(stringResource(Res.string.sync_status_open_settings))
        }
    }
    feedback?.let { SyncNowFeedback(it, pausedReason) }

    if (uiState.queueUnavailable) {
        Text(
            text = stringResource(Res.string.sync_status_queue_unavailable),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = Spacing.lg),
        )
    } else if (uiState.groups.isNotEmpty()) {
        HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.lg))
        Text(
            text = stringResource(Res.string.sync_status_waiting_heading),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(bottom = Spacing.xs),
        )
        uiState.groups.forEach { group -> QueuedGroupRow(group) }
    }

    if (uiState.failedCount > 0) {
        HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.lg))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Icon(Icons.Filled.SyncProblem, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            Text(
                text = pluralStringResource(Res.plurals.sync_status_failed_items, uiState.failedCount, uiState.failedCount),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onOpenSyncIssues) { Text(stringResource(Res.string.sync_banner_review)) }
        }
    }
}

@Composable
private fun StatusHeadline(uiState: SyncStatusUiState) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        if (uiState.isSyncing) {
            SyncProgressIndicator(
                total = uiState.totalForRun,
                completed = uiState.completedInRun,
                modifier = Modifier.size(32.dp),
            )
        } else {
            Icon(
                imageVector =
                    when {
                        uiState.pendingCount == 0 -> Icons.Filled.CloudDone
                        uiState.pausedReason != null -> Icons.Filled.CloudOff
                        else -> Icons.Filled.CloudUpload
                    },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp),
            )
        }
        Column {
            Text(
                text =
                    when {
                        uiState.isSyncing && uiState.totalForRun != null ->
                            stringResource(Res.string.backing_up_progress, uiState.completedInRun, uiState.totalForRun)
                        uiState.isSyncing && uiState.pendingCount > 0 ->
                            stringResource(Res.string.syncing_remaining, uiState.pendingCount)
                        uiState.isSyncing -> stringResource(Res.string.syncing)
                        uiState.pendingCount > 0 ->
                            pluralStringResource(Res.plurals.sync_status_waiting, uiState.pendingCount, uiState.pendingCount)
                        else -> stringResource(Res.string.sync_feedback_up_to_date)
                    },
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text =
                    uiState.lastSyncTime?.let {
                        stringResource(Res.string.last_backed_up_time, it.toReadableDateTimeShort())
                    } ?: stringResource(Res.string.never_synced),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PausedReason(
    reason: SyncPausedReason,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text =
                when (reason) {
                    SyncPausedReason.BACKGROUND_DATA_OFF -> stringResource(Res.string.sync_paused_background_data_off)
                    SyncPausedReason.OFFLINE -> stringResource(Res.string.sync_paused_offline)
                    SyncPausedReason.MEDIA_WAITING_FOR_WIFI -> stringResource(Res.string.sync_paused_media_waiting_for_wifi)
                    SyncPausedReason.NOT_SIGNED_IN -> stringResource(Res.string.sync_paused_signed_out)
                },
            style = MaterialTheme.typography.bodyMedium,
            // Only what the user has to fix is coloured as a problem; the rest clears itself.
            color =
                when (reason) {
                    SyncPausedReason.BACKGROUND_DATA_OFF,
                    SyncPausedReason.NOT_SIGNED_IN,
                    -> MaterialTheme.colorScheme.error
                    SyncPausedReason.OFFLINE,
                    SyncPausedReason.MEDIA_WAITING_FOR_WIFI,
                    -> MaterialTheme.colorScheme.onSurfaceVariant
                },
        )
        if (reason == SyncPausedReason.BACKGROUND_DATA_OFF) {
            Text(
                text = stringResource(Res.string.sync_paused_background_data_off_fix),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SyncNowFeedback(
    feedback: SyncStatusFeedback,
    pausedReason: SyncPausedReason?,
) {
    val text =
        when (feedback) {
            SyncStatusFeedback.NeedsAccount -> stringResource(Res.string.sync_feedback_needs_account)
            SyncStatusFeedback.CouldNotStart -> stringResource(Res.string.sync_status_could_not_start)
            // A request while offline sits until the connection returns; the paused line above
            // already says so, and "backing up" here would contradict it.
            SyncStatusFeedback.Requested ->
                if (pausedReason == SyncPausedReason.OFFLINE) null else stringResource(Res.string.sync_feedback_started)
        } ?: return
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color =
            if (feedback == SyncStatusFeedback.CouldNotStart) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        modifier = Modifier.padding(top = Spacing.sm),
    )
}

@Composable
private fun QueuedGroupRow(group: QueuedGroup) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.sm)) {
        Text(
            text = pluralStringResource(countPluralFor(group.kind?.name.orEmpty()), group.count, group.count),
            style = MaterialTheme.typography.bodyLarge,
        )
        if (group.retrying > 0) {
            Text(
                text = pluralStringResource(Res.plurals.sync_status_retrying, group.retrying, group.retrying),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
