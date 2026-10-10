@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.main

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.logdate.feature.core.sync.AccountSyncStatus
import app.logdate.feature.core.sync.SyncAction
import app.logdate.feature.core.sync.SyncPresentation
import app.logdate.feature.core.sync.messageResource
import app.logdate.ui.streak.CampfirePresentation
import app.logdate.ui.workspace.WorkspaceAccountAction
import app.logdate.ui.workspace.WorkspaceAccountIndicator
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.journal_merge_recovery_status
import logdate.client.feature.core.generated.resources.journal_merge_recovery_title
import logdate.client.feature.core.generated.resources.settings
import logdate.client.feature.core.generated.resources.sync_account_mobile_data
import logdate.client.feature.core.generated.resources.sync_banner_enter_recovery_phrase
import logdate.client.feature.core.generated.resources.sync_banner_manage
import logdate.client.feature.core.generated.resources.sync_banner_review
import logdate.client.feature.core.generated.resources.sync_feedback_sign_in_action
import logdate.client.feature.core.generated.resources.workspace_journaling_streak
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun HomeWorkspaceAccountAction(
    sync: SyncPresentation,
    campfire: CampfirePresentation?,
    onOpenSettings: () -> Unit,
    onOpenStreak: () -> Unit,
    onSyncAction: (SyncAction) -> Unit,
    accountStatus: AccountSyncStatus? = null,
    hasMergeRecovery: Boolean = false,
) {
    val summary =
        if (hasMergeRecovery) {
            stringResource(Res.string.journal_merge_recovery_status)
        } else {
            accountStatus?.let { stringResource(it.messageResource()) } ?: sync.accountSummaryResource()?.let { stringResource(it) }
        }
    val indicator = if (hasMergeRecovery) WorkspaceAccountIndicator.Attention else accountStatus?.indicator() ?: sync.accountIndicator()
    WorkspaceAccountAction(indicator, summary) { dismiss ->
        summary?.let {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            }
        }
        if (accountStatus == AccountSyncStatus.WAITING_FOR_WIFI) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.sync_account_mobile_data)) },
                onClick = {
                    dismiss()
                    onSyncAction(SyncAction.UseMobileData)
                },
            )
        }
        sync.accountRecoveryAction()?.let { action ->
            DropdownMenuItem(text = { Text(action.accountLabel()) }, onClick = {
                dismiss()
                onSyncAction(action)
            })
        }
        if (hasMergeRecovery) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.journal_merge_recovery_title)) },
                onClick = {
                    dismiss()
                    onSyncAction(SyncAction.OpenStatus)
                },
            )
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
        SyncPresentation.NeedsRecovery -> null
        is SyncPresentation.StorageError -> null
        is SyncPresentation.ConflictError, is SyncPresentation.NetworkError -> null
        else -> null
    }

internal fun SyncPresentation.accountSummaryResource(): StringResource? =
    when (this) {
        SyncPresentation.Hidden -> null
        is SyncPresentation.Syncing -> AccountSyncStatus.SYNCING.messageResource()
        is SyncPresentation.Pending -> AccountSyncStatus.CHECKING.messageResource()
        SyncPresentation.StatusUnavailable -> AccountSyncStatus.LOCAL_DATA_UNAVAILABLE.messageResource()
        SyncPresentation.AuthError -> AccountSyncStatus.SIGN_IN_REQUIRED.messageResource()
        SyncPresentation.NeedsRecovery -> AccountSyncStatus.DEVICE_ACCESS_REQUIRED.messageResource()
        is SyncPresentation.StorageError -> AccountSyncStatus.STORAGE_FULL.messageResource()
        is SyncPresentation.ConflictError -> AccountSyncStatus.CONFLICT.messageResource()
        is SyncPresentation.NetworkError -> AccountSyncStatus.CONNECTION_UNAVAILABLE.messageResource()
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

private fun AccountSyncStatus.indicator(): WorkspaceAccountIndicator =
    when (this) {
        AccountSyncStatus.UP_TO_DATE -> WorkspaceAccountIndicator.None
        AccountSyncStatus.SYNCING -> WorkspaceAccountIndicator.Working
        AccountSyncStatus.SIGN_IN_REQUIRED -> WorkspaceAccountIndicator.Attention
        else -> WorkspaceAccountIndicator.Waiting
    }
