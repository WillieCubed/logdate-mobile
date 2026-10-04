@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.settings.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import app.logdate.feature.core.export.UserDataExportViewModel
import app.logdate.feature.core.restore.RestoreState
import app.logdate.feature.core.restore.UserDataRestoreViewModel
import app.logdate.ui.common.SettingsScaffold
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.data_management
import logdate.client.feature.core.generated.resources.sync_feedback_failed
import logdate.client.feature.core.generated.resources.sync_feedback_needs_account
import logdate.client.feature.core.generated.resources.sync_feedback_sign_in_action
import logdate.client.feature.core.generated.resources.sync_feedback_started
import logdate.client.feature.core.generated.resources.sync_feedback_succeeded
import logdate.client.feature.core.generated.resources.sync_feedback_up_to_date
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun DataSettingsScreen(
    onBack: () -> Unit,
    onNavigateToCloudAccountCreation: () -> Unit = {},
    onNavigateToSignIn: () -> Unit = {},
    onNavigateToRecoveryPhrase: () -> Unit = {},
    onBrowseFile: (String) -> Unit = {},
    viewModel: DataSettingsViewModel = koinViewModel(),
    exportViewModel: UserDataExportViewModel = koinViewModel(),
    restoreViewModel: UserDataRestoreViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val exportState by exportViewModel.exportState.collectAsState()
    val isExportSheetVisible by exportViewModel.isSheetVisible.collectAsState()
    val restoreState by restoreViewModel.restoreState.collectAsState()
    val isRestoreSheetVisible by restoreViewModel.isSheetVisible.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(restoreState) {
        if (restoreState is RestoreState.Completed) {
            viewModel.runIntegrityCheck()
        }
    }

    // Report what Sync Now actually did. Without this the button is silent whether the sync
    // succeeded, failed, or was refused for want of an account.
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

            SyncFeedback.Started ->
                snackbarHostState.showSnackbar(getString(Res.string.sync_feedback_started))

            is SyncFeedback.Failed ->
                snackbarHostState.showSnackbar(
                    getString(Res.string.sync_feedback_failed, feedback.message),
                )
        }
        viewModel.consumeSyncFeedback()
    }

    // The session flow starts at null meaning "not read yet", and stateIn's seed used to call
    // that signed out - so the screen opened on Create Account for someone already signed in and
    // syncing, then corrected itself a frame later. Hold the screen until the answer is known.
    val isAuthenticated = uiState.isAuthenticated
    if (isAuthenticated == null) {
        SettingsScaffold(
            title = stringResource(Res.string.data_management),
            onBack = onBack,
            snackbarHostState = snackbarHostState,
        ) {}
        return
    }

    DataSettingsContent(
        onBack = onBack,
        quotaUsage = uiState.quotaState.orDefault().toStorageQuotaUi(),
        isQuotaAvailable = uiState.isQuotaAvailable && uiState.hasAuthoritativeQuota,
        exportState = exportState,
        isExportSheetVisible = isExportSheetVisible,
        onShowExportOptions = exportViewModel::showExportOptions,
        onUpdateExportOptions = exportViewModel::updateExportOptions,
        onConfirmExport = exportViewModel::confirmExport,
        onCancelExport = exportViewModel::cancelExport,
        onRetryExport = exportViewModel::retryExport,
        onDismissExport = exportViewModel::dismissSheet,
        onBrowseExport = { path -> onBrowseFile(path) },
        restoreState = restoreState,
        isRestoreSheetVisible = isRestoreSheetVisible,
        onShowRestoreSheet = restoreViewModel::showRestoreSheet,
        onSelectRestoreFile = restoreViewModel::selectFile,
        onUpdateImportOptions = restoreViewModel::updateImportOptions,
        onConfirmImport = restoreViewModel::confirmImport,
        onCancelRestore = restoreViewModel::cancelRestore,
        onRetryRestore = restoreViewModel::retryRestore,
        onDismissRestore = restoreViewModel::dismissSheet,
        integrityState = uiState.integrityState,
        onRunIntegrityCheck = viewModel::runIntegrityCheck,
        onRepairIntegrity = viewModel::repairIntegrity,
        snackbarHostState = snackbarHostState,
        syncStatus = uiState.syncStatus,
        cloudArchiveStatus = uiState.cloudArchiveStatus,
        isAuthenticated = isAuthenticated,
        onSyncNow = viewModel::syncNow,
        onArchiveBackupNow = viewModel::backupArchiveNow,
        onNavigateToRecoveryPhrase = onNavigateToRecoveryPhrase,
        onNavigateToCloudAccountCreation = onNavigateToCloudAccountCreation,
        onNavigateToSignIn = onNavigateToSignIn,
    )
}
