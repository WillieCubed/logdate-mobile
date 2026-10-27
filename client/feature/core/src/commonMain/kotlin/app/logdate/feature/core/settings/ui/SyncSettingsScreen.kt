@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.settings.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import app.logdate.client.device.restore.NativeBackupStatus
import app.logdate.client.domain.journals.MergeJournalsUseCase
import app.logdate.client.repository.journals.JournalMergeOperation
import app.logdate.client.sync.space.migration.JournalMoveState
import app.logdate.client.sync.space.migration.JournalStorageMove
import app.logdate.feature.core.settings.updates.AppUpdateCheckTrigger
import app.logdate.feature.core.settings.updates.AppUpdateController
import app.logdate.feature.core.settings.updates.AppUpdateStatus
import app.logdate.ui.common.SettingsScaffold
import app.logdate.ui.theme.Spacing
import kotlinx.coroutines.launch
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.sync_and_backup
import logdate.client.feature.core.generated.resources.sync_feedback_failed
import logdate.client.feature.core.generated.resources.sync_feedback_needs_account
import logdate.client.feature.core.generated.resources.sync_feedback_sign_in_action
import logdate.client.feature.core.generated.resources.sync_feedback_started
import logdate.client.feature.core.generated.resources.sync_feedback_succeeded
import logdate.client.feature.core.generated.resources.sync_feedback_up_to_date
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * Sync settings screen.
 *
 * This screen handles cloud sync status, background sync, storage quota,
 * and sync conflict resolution.
 */
@Composable
fun SyncSettingsScreen(
    onBack: () -> Unit,
    onNavigateToCloudAccountCreation: () -> Unit = {},
    onNavigateToSignIn: () -> Unit = {},
    onNavigateToRecoveryPhrase: () -> Unit = {},
    onNavigateToExport: () -> Unit = {},
    onRecoverJournalMerge: (JournalMergeOperation) -> Unit = {},
    viewModel: DataSettingsViewModel = koinViewModel(),
    mergeJournals: MergeJournalsUseCase = koinInject(),
    journalStorage: JournalStorageMove = koinInject(),
    appUpdates: AppUpdateController = koinInject(),
) {
    val appUpdateState by appUpdates.uiState.collectAsState()
    val mergeIssues by remember(mergeJournals) { mergeJournals.observeIssues() }.collectAsState(emptyList())
    val journalMove by journalStorage.state.collectAsState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(journalStorage) { journalStorage.refresh() }
    val uiState by viewModel.uiState.collectAsState()
    val nativeBackupStatus by viewModel.nativeBackupStatus.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // This screen owns the Sync Now the user actually reaches from Settings, so it reports the
    // outcome the same way Data & Storage does. Both share DataSettingsViewModel.
    val syncFeedback by viewModel.syncFeedback.collectAsState()
    val needsAccountMessage = stringResource(Res.string.sync_feedback_needs_account)
    val signInActionLabel = stringResource(Res.string.sync_feedback_sign_in_action)
    val upToDateMessage = stringResource(Res.string.sync_feedback_up_to_date)
    LaunchedEffect(syncFeedback) {
        val feedback = syncFeedback ?: return@LaunchedEffect
        when (feedback) {
            is SyncFeedback.NeedsAccount -> {
                val action = snackbarHostState.showSnackbar(needsAccountMessage, signInActionLabel)
                if (action == SnackbarResult.ActionPerformed) {
                    onNavigateToSignIn()
                }
            }

            is SyncFeedback.Succeeded -> {
                val moved = feedback.uploadedItems + feedback.downloadedItems
                snackbarHostState.showSnackbar(
                    if (moved == 0) {
                        upToDateMessage
                    } else {
                        getString(
                            Res.string.sync_feedback_succeeded,
                            feedback.uploadedItems,
                            feedback.downloadedItems,
                        )
                    },
                )
            }

            SyncFeedback.Started -> {
                snackbarHostState.showSnackbar(getString(Res.string.sync_feedback_started))
            }

            is SyncFeedback.Failed -> {
                snackbarHostState.showSnackbar(
                    getString(Res.string.sync_feedback_failed, getString(feedback.message)),
                )
            }
        }
        viewModel.consumeSyncFeedback()
    }

    // The session flow starts at null meaning "not read yet", and stateIn's seed used to call
    // that signed out - so the screen opened on Create Account for someone already signed in and
    // syncing, then corrected itself a frame later. Hold the screen until the answer is known.
    val isAuthenticated = uiState.isAuthenticated
    if (isAuthenticated == null) {
        SettingsScaffold(
            title = stringResource(Res.string.sync_and_backup),
            onBack = onBack,
            snackbarHostState = snackbarHostState,
        ) {}
        return
    }

    SyncSettingsContent(
        onBack = onBack,
        mergeIssues = mergeIssues,
        onRecoverJournalMerge = onRecoverJournalMerge,
        journalMove = journalMove,
        onRetryJournalMove = { scope.launch { journalStorage.retry() } },
        onSwitchJournalBack = { scope.launch { journalStorage.switchBack() } },
        onUpdateApp =
            if (appUpdateState.status == AppUpdateStatus.Unsupported) {
                null
            } else {
                { scope.launch { appUpdates.checkForUpdates(AppUpdateCheckTrigger.Manual) } }
            },
        syncStatus = uiState.syncStatus,
        cloudArchiveStatus = uiState.cloudArchiveStatus,
        nativeBackupStatus = nativeBackupStatus,
        onExportArchive = onNavigateToExport,
        onRetryNativeRecovery = viewModel::retryNativeRecovery,
        isAuthenticated = isAuthenticated,
        onSyncNow = { viewModel.syncNow() },
        onSyncUsingMobileData = { viewModel.syncNow(allowMeteredMedia = true) },
        onArchiveBackupNow = viewModel::backupArchiveNow,
        onNavigateToCloudAccountCreation = onNavigateToCloudAccountCreation,
        onNavigateToSignIn = onNavigateToSignIn,
        onNavigateToRecoveryPhrase = onNavigateToRecoveryPhrase,
        quotaUsage = uiState.quotaState.orDefault().toStorageQuotaUi(),
        isQuotaAvailable = uiState.isQuotaAvailable && uiState.hasAuthoritativeQuota,
        snackbarHostState = snackbarHostState,
    )
}

@Composable
fun SyncSettingsContent(
    onBack: () -> Unit,
    syncStatus: app.logdate.client.sync.SyncStatus?,
    cloudArchiveStatus: CloudArchiveStatus = CloudArchiveStatus(CloudArchivePhase.CHECKING),
    nativeBackupStatus: NativeBackupStatus = NativeBackupStatus.Unsupported,
    onExportArchive: () -> Unit = {},
    onRetryNativeRecovery: () -> Unit = {},
    isAuthenticated: Boolean,
    onSyncNow: () -> Unit,
    onSyncUsingMobileData: () -> Unit = {},
    onArchiveBackupNow: () -> Unit = {},
    onNavigateToCloudAccountCreation: () -> Unit = {},
    onNavigateToSignIn: () -> Unit,
    onNavigateToRecoveryPhrase: () -> Unit = {},
    quotaUsage: StorageQuotaUi,
    isQuotaAvailable: Boolean,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    mergeIssues: List<JournalMergeOperation> = emptyList(),
    onRecoverJournalMerge: (JournalMergeOperation) -> Unit = {},
    journalMove: JournalMoveState = JournalMoveState.NotOffered,
    onRetryJournalMove: () -> Unit = {},
    onSwitchJournalBack: () -> Unit = {},
    onUpdateApp: (() -> Unit)? = null,
) {
    SettingsScaffold(
        title = stringResource(Res.string.sync_and_backup),
        onBack = onBack,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    ) {
        item {
            BackupCloudSection(
                isAuthenticated = isAuthenticated,
                syncStatus = syncStatus,
                cloudArchiveStatus = cloudArchiveStatus,
                onSyncUsingMobileData = onSyncUsingMobileData,
                onCreateAccount = onNavigateToCloudAccountCreation,
                onSignIn = onNavigateToSignIn,
                onEnterRecoveryPhrase = onNavigateToRecoveryPhrase,
                modifier = Modifier.padding(horizontal = Spacing.lg),
                onUpdateApp = onUpdateApp,
            )
        }
        if (isAuthenticated && journalMove != JournalMoveState.NotOffered) {
            item {
                JournalStorageSection(
                    state = journalMove,
                    onTryAgain = onRetryJournalMove,
                    onSwitchBack = onSwitchJournalBack,
                    onEnterRecoveryPhrase = onNavigateToRecoveryPhrase,
                    modifier = Modifier.padding(horizontal = Spacing.lg),
                )
            }
        }
        if (nativeBackupStatus is NativeBackupStatus.Available) {
            item {
                NativeBackupSettingsSection(
                    nativeBackupStatus,
                    modifier = Modifier.padding(horizontal = Spacing.lg),
                    onRetryRecovery = onRetryNativeRecovery,
                    onOpenArchive = onExportArchive,
                )
            }
        }
        item {
            BackupArchiveItem(onExportArchive, Modifier.padding(horizontal = Spacing.lg))
        }
        if (isAuthenticated) {
            items(mergeIssues, key = { it.operationId }) { operation ->
                JournalMergeRecoveryItem(operation) { onRecoverJournalMerge(operation) }
            }
        }
    }
}
