package app.logdate.client

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavKey
import app.logdate.client.database.DatabaseStartupState
import app.logdate.feature.core.GlobalAppUiLoadedState
import app.logdate.feature.core.requiresUnlock
import app.logdate.feature.core.settings.updates.AppUpdateStatus
import app.logdate.feature.core.settings.updates.AppUpdateUiState
import app.logdate.navigation.MainAppNavigator
import app.logdate.navigation.MainNavigationRoot
import app.logdate.navigation.rememberMainAppNavigator
import app.logdate.navigation.routes.core.DataSettingsRoute
import app.logdate.navigation.routes.core.NavigationStart
import app.logdate.navigation.routes.core.SettingsOverviewRoute
import app.logdate.navigation.routes.core.navigateHomeFromLaunch
import app.logdate.navigation.routes.openDataSettings
import app.logdate.navigation.routes.openSettings
import app.logdate.navigation.routes.startOnboarding
import app.logdate.ui.LocalSharedTransitionScope
import app.logdate.ui.theme.LogDateTheme
import logdate.app.composemain.generated.resources.Res
import logdate.app.composemain.generated.resources.cancel
import logdate.app.composemain.generated.resources.encrypted_data_recovery_required
import logdate.app.composemain.generated.resources.i_understand_reset
import logdate.app.composemain.generated.resources.open_recovery_tools
import logdate.app.composemain.generated.resources.reset_encrypted_storage
import logdate.app.composemain.generated.resources.reset_encrypted_storage_2
import logdate.app.composemain.generated.resources.restart
import logdate.app.composemain.generated.resources.update_ready_restart_to_finish_installing
import org.jetbrains.compose.resources.stringResource

/**
 * Renders the Android root UI once startup gates have produced a loaded app state.
 *
 * In addition to normal navigation, this root owns two global surfaces:
 * - the encrypted-database recovery dialogs,
 * - the persistent restart snackbar shown after a flexible Play update finishes downloading.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Suppress("ktlint:standard:function-naming")
@Composable
fun MainActivityUiRoot(
    appUiState: GlobalAppUiLoadedState,
    onShowUnlockPrompt: () -> Unit,
    pendingNavKey: NavKey? = null,
    onDeepLinkHandled: () -> Unit = {},
    onInitialNavigationReady: () -> Unit = {},
    databaseStartupState: DatabaseStartupState = DatabaseStartupState.Ready,
    onResetEncryptedStorage: () -> Unit = {},
    appUpdateUiState: AppUpdateUiState = AppUpdateUiState(),
    onCompleteAppUpdate: () -> Unit = {},
    mainAppNavigator: MainAppNavigator = rememberMainAppNavigator(initialRoute = NavigationStart),
) {
    var hasRequestedUnlock by remember { mutableStateOf(false) }
    var hasHandledInitialNavigation by remember { mutableStateOf(false) }
    var hasReportedInitialNavigation by remember { mutableStateOf(false) }
    var showResetConfirmation by remember { mutableStateOf(false) }
    var hideRecoveryDialog by remember { mutableStateOf(false) }
    val appUpdateSnackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(appUiState.isOnboarded, appUiState.requiresUnlock, pendingNavKey, databaseStartupState) {
        if (databaseStartupState is DatabaseStartupState.Ready) {
            hideRecoveryDialog = false
        }
        if (databaseStartupState is DatabaseStartupState.RecoveryRequired) {
            return@LaunchedEffect
        }
        if (!appUiState.isOnboarded) {
//        // Ensure that onboarding is completed before proceeding
            mainAppNavigator.startOnboarding()
            if (!hasReportedInitialNavigation) {
                hasReportedInitialNavigation = true
                onInitialNavigationReady()
            }
            return@LaunchedEffect
        }
        if (appUiState.requiresUnlock) {
            if (!hasRequestedUnlock) {
                hasRequestedUnlock = true
                onShowUnlockPrompt()
            }
            return@LaunchedEffect
        }
        hasRequestedUnlock = false
        if (pendingNavKey != null) {
            if (!mainAppNavigator.backStack.contains(pendingNavKey)) {
                mainAppNavigator.navigateHomeFromLaunch()
                // Guard: navigateHomeFromLaunch may have already added the key (e.g. TimelineListRoute)
                if (!mainAppNavigator.backStack.contains(pendingNavKey)) {
                    mainAppNavigator.backStack.add(pendingNavKey)
                }
            }
            onDeepLinkHandled()
            hasHandledInitialNavigation = true
        } else {
            if (!hasHandledInitialNavigation) {
                mainAppNavigator.navigateHomeFromLaunch()
                hasHandledInitialNavigation = true
            }
        }

        if (hasHandledInitialNavigation && !hasReportedInitialNavigation) {
            hasReportedInitialNavigation = true
            onInitialNavigationReady()
        }
    }

    LogDateTheme {
        SharedTransitionLayout {
            CompositionLocalProvider(
                LocalSharedTransitionScope provides this,
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    MainNavigationRoot(mainAppNavigator)

                    SnackbarHost(
                        hostState = appUpdateSnackbarHostState,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }

                if (databaseStartupState is DatabaseStartupState.RecoveryRequired && !hideRecoveryDialog) {
                    val recovery = databaseStartupState
                    AlertDialog(
                        onDismissRequest = {},
                        title = { Text(stringResource(Res.string.encrypted_data_recovery_required)) },
                        text = {
                            Text(
                                "LogDate could not open your encrypted local database.\n\n" +
                                    "Reason: ${recovery.reason}\n\n" +
                                    "No local data has been deleted automatically. " +
                                    "Open recovery tools first to import/restore a backup.",
                            )
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    hideRecoveryDialog = true
                                    mainAppNavigator.safelyClearBackstack(SettingsOverviewRoute)
                                    if (!mainAppNavigator.backStack.contains(DataSettingsRoute)) {
                                        mainAppNavigator.openDataSettings()
                                    } else if (!mainAppNavigator.backStack.contains(SettingsOverviewRoute)) {
                                        mainAppNavigator.openSettings()
                                    }
                                },
                            ) {
                                Text(stringResource(Res.string.open_recovery_tools))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showResetConfirmation = true }) {
                                Text(stringResource(Res.string.reset_encrypted_storage))
                            }
                        },
                    )
                }

                if (showResetConfirmation) {
                    AlertDialog(
                        onDismissRequest = { showResetConfirmation = false },
                        title = { Text(stringResource(Res.string.reset_encrypted_storage_2)) },
                        text = {
                            Text(
                                "This will clear the local encryption key and move the current " +
                                    "database file to a recovery backup. You should only do this " +
                                    "if you have a verified backup to restore.",
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    showResetConfirmation = false
                                    onResetEncryptedStorage()
                                },
                                colors = ButtonDefaults.buttonColors(),
                            ) {
                                Text(stringResource(Res.string.i_understand_reset))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showResetConfirmation = false }) {
                                Text(stringResource(Res.string.cancel))
                            }
                        },
                    )
                }
            }
        }
    }

    val updateReadyMessage = stringResource(Res.string.update_ready_restart_to_finish_installing)
    val restartLabel = stringResource(Res.string.restart)

    // Keep the restart affordance visible until the downloaded flexible update is completed
    // or Play clears the downloaded state.
    LaunchedEffect(appUpdateUiState.status) {
        if (appUpdateUiState.status != AppUpdateStatus.Downloaded) {
            appUpdateSnackbarHostState.currentSnackbarData?.dismiss()
            return@LaunchedEffect
        }

        val result =
            appUpdateSnackbarHostState.showSnackbar(
                message = updateReadyMessage,
                actionLabel = restartLabel,
                withDismissAction = true,
                duration = SnackbarDuration.Indefinite,
            )

        if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
            onCompleteAppUpdate()
        }
    }
}
