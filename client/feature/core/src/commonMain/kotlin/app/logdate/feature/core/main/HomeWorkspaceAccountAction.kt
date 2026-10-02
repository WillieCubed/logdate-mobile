@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.main

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import app.logdate.feature.core.sync.SyncAction
import app.logdate.feature.core.sync.SyncPresentation
import app.logdate.ui.streak.CampfirePresentation
import app.logdate.ui.workspace.WorkspaceAccountAction
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.last_sync_failed
import logdate.client.feature.core.generated.resources.settings
import logdate.client.feature.core.generated.resources.sync_banner_conflicts
import logdate.client.feature.core.generated.resources.sync_banner_enter_recovery_phrase
import logdate.client.feature.core.generated.resources.sync_banner_manage
import logdate.client.feature.core.generated.resources.sync_banner_needs_recovery
import logdate.client.feature.core.generated.resources.sync_banner_review
import logdate.client.feature.core.generated.resources.sync_banner_session_expired
import logdate.client.feature.core.generated.resources.sync_banner_storage_full
import logdate.client.feature.core.generated.resources.sync_feedback_sign_in_action
import logdate.client.feature.core.generated.resources.sync_status_title
import logdate.client.feature.core.generated.resources.sync_status_unavailable
import logdate.client.feature.core.generated.resources.sync_status_waiting
import logdate.client.feature.core.generated.resources.syncing
import logdate.client.feature.core.generated.resources.workspace_journaling_streak
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun HomeWorkspaceAccountAction(
    sync: SyncPresentation,
    campfire: CampfirePresentation?,
    onOpenSettings: () -> Unit,
    onOpenStreak: () -> Unit,
    onSyncAction: (SyncAction) -> Unit,
) {
    val summary = sync.accountSummary()
    WorkspaceAccountAction(sync.accountIndicator(), summary) { dismiss ->
        DropdownMenuItem(
            text = {
                Column {
                    Text(stringResource(Res.string.sync_status_title))
                    summary?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            leadingIcon = { Icon(Icons.Default.CloudUpload, null) },
            onClick = {
                dismiss()
                onSyncAction(SyncAction.OpenStatus)
            },
        )
        sync.accountRecoveryAction()?.let { action ->
            DropdownMenuItem(text = { Text(action.accountLabel()) }, onClick = {
                dismiss()
                onSyncAction(action)
            })
        }
        HorizontalDivider()
        if (campfire != null) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.workspace_journaling_streak)) },
                leadingIcon = { Icon(Icons.Default.LocalFireDepartment, null) },
                onClick = {
                    dismiss()
                    onOpenStreak()
                },
            )
        }
        DropdownMenuItem(
            text = { Text(stringResource(Res.string.settings)) },
            leadingIcon = { Icon(Icons.Default.Settings, null) },
            onClick = {
                dismiss()
                onOpenSettings()
            },
        )
    }
}

internal fun SyncPresentation.accountRecoveryAction(): SyncAction? =
    when (this) {
        SyncPresentation.AuthError -> SyncAction.SignIn
        SyncPresentation.NeedsRecovery -> SyncAction.EnterRecoveryPhrase
        is SyncPresentation.StorageError -> SyncAction.ManageStorage
        is SyncPresentation.ConflictError -> SyncAction.ReviewConflicts
        is SyncPresentation.NetworkError -> SyncAction.ReviewIssues.takeIf { pendingCount > 0 }
        else -> null
    }

@Composable
private fun SyncPresentation.accountSummary(): String? =
    when (this) {
        SyncPresentation.Hidden -> null
        is SyncPresentation.Syncing -> stringResource(Res.string.syncing)
        is SyncPresentation.Pending ->
            pluralStringResource(Res.plurals.sync_status_waiting, pendingCount, pendingCount).takeIf {
                pendingCount >
                    0
            }
        SyncPresentation.StatusUnavailable -> stringResource(Res.string.sync_status_unavailable)
        SyncPresentation.AuthError -> stringResource(Res.string.sync_banner_session_expired)
        SyncPresentation.NeedsRecovery -> stringResource(Res.string.sync_banner_needs_recovery)
        is SyncPresentation.StorageError -> stringResource(Res.string.sync_banner_storage_full)
        is SyncPresentation.ConflictError -> pluralStringResource(Res.plurals.sync_banner_conflicts, conflictCount, conflictCount)
        is SyncPresentation.NetworkError -> stringResource(Res.string.last_sync_failed)
    }

@Composable
private fun SyncAction.accountLabel(): String =
    stringResource(
        when (this) {
            SyncAction.SignIn -> Res.string.sync_feedback_sign_in_action
            SyncAction.ManageStorage -> Res.string.sync_banner_manage
            SyncAction.EnterRecoveryPhrase -> Res.string.sync_banner_enter_recovery_phrase
            else -> Res.string.sync_banner_review
        },
    )
