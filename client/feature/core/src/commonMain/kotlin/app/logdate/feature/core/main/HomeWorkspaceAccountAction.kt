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
import app.logdate.ui.streak.CampfirePresentation
import app.logdate.ui.workspace.WorkspaceAccountAction
import app.logdate.ui.workspace.WorkspaceAccountIndicator
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.settings
import logdate.client.feature.core.generated.resources.sync_account_background
import logdate.client.feature.core.generated.resources.sync_account_conflict
import logdate.client.feature.core.generated.resources.sync_account_connection_unavailable
import logdate.client.feature.core.generated.resources.sync_account_device_access
import logdate.client.feature.core.generated.resources.sync_account_disabled
import logdate.client.feature.core.generated.resources.sync_account_local_unavailable
import logdate.client.feature.core.generated.resources.sync_account_media_too_large
import logdate.client.feature.core.generated.resources.sync_account_offline
import logdate.client.feature.core.generated.resources.sync_account_server_unavailable
import logdate.client.feature.core.generated.resources.sync_account_signed_out
import logdate.client.feature.core.generated.resources.sync_account_storage_full
import logdate.client.feature.core.generated.resources.sync_account_unknown
import logdate.client.feature.core.generated.resources.sync_account_waiting
import logdate.client.feature.core.generated.resources.sync_account_wifi
import logdate.client.feature.core.generated.resources.sync_banner_enter_recovery_phrase
import logdate.client.feature.core.generated.resources.sync_banner_manage
import logdate.client.feature.core.generated.resources.sync_banner_review
import logdate.client.feature.core.generated.resources.sync_feedback_sign_in_action
import logdate.client.feature.core.generated.resources.sync_feedback_up_to_date
import logdate.client.feature.core.generated.resources.syncing
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
) {
    val summary = accountStatus?.let { stringResource(it.messageResource()) } ?: sync.accountSummary()
    WorkspaceAccountAction(accountStatus?.indicator() ?: sync.accountIndicator(), summary) { dismiss ->
        summary?.let {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            }
        }
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
        SyncPresentation.NeedsRecovery -> null
        is SyncPresentation.StorageError -> SyncAction.ManageStorage
        is SyncPresentation.ConflictError, is SyncPresentation.NetworkError -> null
        else -> null
    }

@Composable
private fun SyncPresentation.accountSummary(): String? =
    when (this) {
        SyncPresentation.Hidden -> null
        is SyncPresentation.Syncing -> stringResource(Res.string.syncing)
        is SyncPresentation.Pending -> stringResource(Res.string.sync_account_waiting)
        SyncPresentation.StatusUnavailable -> stringResource(Res.string.sync_account_local_unavailable)
        SyncPresentation.AuthError -> stringResource(Res.string.sync_account_signed_out)
        SyncPresentation.NeedsRecovery -> stringResource(Res.string.sync_account_device_access)
        is SyncPresentation.StorageError -> stringResource(Res.string.sync_account_storage_full)
        is SyncPresentation.ConflictError -> stringResource(Res.string.sync_account_conflict)
        is SyncPresentation.NetworkError -> stringResource(Res.string.sync_account_unknown)
    }

private fun AccountSyncStatus.messageResource(): StringResource =
    when (this) {
        AccountSyncStatus.UP_TO_DATE -> Res.string.sync_feedback_up_to_date
        AccountSyncStatus.SYNCING -> Res.string.syncing
        AccountSyncStatus.WAITING -> Res.string.sync_account_waiting
        AccountSyncStatus.OFFLINE -> Res.string.sync_account_offline
        AccountSyncStatus.SERVER_UNAVAILABLE -> Res.string.sync_account_server_unavailable
        AccountSyncStatus.CONNECTION_UNAVAILABLE -> Res.string.sync_account_connection_unavailable
        AccountSyncStatus.SIGN_IN_REQUIRED -> Res.string.sync_account_signed_out
        AccountSyncStatus.STORAGE_FULL -> Res.string.sync_account_storage_full
        AccountSyncStatus.WAITING_FOR_WIFI -> Res.string.sync_account_wifi
        AccountSyncStatus.BACKGROUND_RESTRICTED -> Res.string.sync_account_background
        AccountSyncStatus.DEVICE_ACCESS_REQUIRED -> Res.string.sync_account_device_access
        AccountSyncStatus.CONFLICT -> Res.string.sync_account_conflict
        AccountSyncStatus.LOCAL_DATA_UNAVAILABLE -> Res.string.sync_account_local_unavailable
        AccountSyncStatus.MEDIA_TOO_LARGE -> Res.string.sync_account_media_too_large
        AccountSyncStatus.UNKNOWN -> Res.string.sync_account_unknown
        AccountSyncStatus.DISABLED -> Res.string.sync_account_disabled
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
        AccountSyncStatus.WAITING, AccountSyncStatus.WAITING_FOR_WIFI -> WorkspaceAccountIndicator.Waiting
        else -> WorkspaceAccountIndicator.Attention
    }
