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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavKey
import app.logdate.client.database.DatabaseStartupState
import app.logdate.client.sharing.SharingLauncher
import app.logdate.client.ui.LockableContent
import app.logdate.feature.core.GlobalAppUiLoadedState
import app.logdate.feature.core.requiresUnlock
import app.logdate.feature.core.settings.updates.AppUpdateStatus
import app.logdate.feature.core.settings.updates.AppUpdateUiState
import app.logdate.navigation.MainAppNavigator
import app.logdate.navigation.MainNavigationRoot
import app.logdate.navigation.rememberMainAppNavigator
import app.logdate.navigation.routes.core.ExportSettingsRoute
import app.logdate.navigation.routes.core.NavigationStart
import app.logdate.navigation.routes.core.NoteViewerRoute
import app.logdate.navigation.routes.core.OnboardingAccountCreationRoute
import app.logdate.navigation.routes.core.OnboardingAppOverviewRoute
import app.logdate.navigation.routes.core.OnboardingBirthdayRoute
import app.logdate.navigation.routes.core.OnboardingCompleteRoute
import app.logdate.navigation.routes.core.OnboardingDayBoundariesRoute
import app.logdate.navigation.routes.core.OnboardingImportRoute
import app.logdate.navigation.routes.core.OnboardingLocationTimelineRoute
import app.logdate.navigation.routes.core.OnboardingMemorySelectionRoute
import app.logdate.navigation.routes.core.OnboardingNotificationsRoute
import app.logdate.navigation.routes.core.OnboardingRecommendationsRoute
import app.logdate.navigation.routes.core.OnboardingStart
import app.logdate.navigation.routes.core.OnboardingWelcomeBackRoute
import app.logdate.navigation.routes.core.PersonalIntroRoute
import app.logdate.navigation.routes.core.SettingsOverviewRoute
import app.logdate.navigation.routes.core.navigateHomeFromLaunch
import app.logdate.navigation.routes.openExportSettings
import app.logdate.navigation.routes.openSettings
import app.logdate.navigation.routes.startOnboarding
import app.logdate.ui.LocalSharedTransitionScope
import app.logdate.ui.audio.AudioPlaybackProvider
import app.logdate.ui.audio.LocalAudioPlaybackState
import app.logdate.ui.audio.MiniAudioPlayer
import app.logdate.ui.audio.TranscriptionProvider
import app.logdate.ui.audio.TranscriptionState
import app.logdate.ui.restore.LocalAcknowledgeCloudRestore
import app.logdate.ui.restore.LocalIsPostCloudRestore
import app.logdate.ui.theme.LogDateTheme
import kotlinx.coroutines.flow.distinctUntilChanged
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
    isPostCloudRestore: Boolean = false,
    onAcknowledgeCloudRestore: () -> Unit = {},
    appUpdateUiState: AppUpdateUiState = AppUpdateUiState(),
    onCompleteAppUpdate: () -> Unit = {},
    onCurrentDestinationChanged: (NavKey) -> Unit = {},
    mainAppNavigator: MainAppNavigator = rememberMainAppNavigator(initialRoute = NavigationStart),
    sharingLauncher: SharingLauncher,
) {
    var hasRequestedUnlock by rememberSaveable { mutableStateOf(false) }
    var hasHandledInitialNavigation by rememberSaveable { mutableStateOf(false) }
    var hasReportedInitialNavigation by rememberSaveable { mutableStateOf(false) }
    var hasStartedOnboardingNavigation by rememberSaveable { mutableStateOf(false) }
    var lastOnboardingRouteName by rememberSaveable { mutableStateOf<String?>(null) }
    var showResetConfirmation by rememberSaveable { mutableStateOf(false) }
    var hideRecoveryDialog by rememberSaveable { mutableStateOf(false) }
    val appUpdateSnackbarHostState = remember { SnackbarHostState() }
    val currentRoute = mainAppNavigator.backStack.lastOrNull()

    LaunchedEffect(Unit) {
        snapshotFlow { mainAppNavigator.backStack.lastOrNull() }
            .distinctUntilChanged()
            .collect { key -> key?.let { onCurrentDestinationChanged(it) } }
    }

    LaunchedEffect(appUiState.isOnboarded, appUiState.requiresUnlock, pendingNavKey, databaseStartupState, currentRoute) {
        if (databaseStartupState is DatabaseStartupState.Ready) {
            hideRecoveryDialog = false
        }
        if (databaseStartupState is DatabaseStartupState.RecoveryRequired) {
            return@LaunchedEffect
        }
        if (!appUiState.isOnboarded) {
            if (currentRoute.isOnboardingRoute()) {
                hasStartedOnboardingNavigation = true
                lastOnboardingRouteName = currentRoute?.onboardingRouteName()
            } else if (!hasStartedOnboardingNavigation) {
                val restoredRoute = onboardingRouteForName(lastOnboardingRouteName)
                if (restoredRoute != null) {
                    mainAppNavigator.restoreOnboardingRoute(restoredRoute)
                } else {
                    mainAppNavigator.startOnboarding()
                }
                hasStartedOnboardingNavigation = true
            }
            if (!hasReportedInitialNavigation) {
                hasReportedInitialNavigation = true
                onInitialNavigationReady()
            }
            return@LaunchedEffect
        }
        hasStartedOnboardingNavigation = false
        lastOnboardingRouteName = null
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
                LocalIsPostCloudRestore provides isPostCloudRestore,
                LocalAcknowledgeCloudRestore provides onAcknowledgeCloudRestore,
            ) {
                LockableContent(
                    isLocked = appUiState.requiresUnlock,
                    displayName = appUiState.displayName,
                    onUsePasscode = onShowUnlockPrompt,
                ) {
                    AudioPlaybackProvider {
                        TranscriptionProvider(state = TranscriptionState()) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                MainNavigationRoot(mainAppNavigator, sharingLauncher)

                                val audioState = LocalAudioPlaybackState.current
                                MiniAudioPlayer(
                                    onOpenFullPlayer = {
                                        audioState.currentlyPlayingId?.let { noteId ->
                                            mainAppNavigator.backStack.add(NoteViewerRoute(noteId))
                                        }
                                    },
                                    modifier = Modifier.align(Alignment.BottomCenter),
                                )

                                SnackbarHost(
                                    hostState = appUpdateSnackbarHostState,
                                    modifier = Modifier.align(Alignment.BottomCenter),
                                )
                            }
                        }
                    }
                }

                if (databaseStartupState is DatabaseStartupState.RecoveryRequired && !hideRecoveryDialog && !isPostCloudRestore) {
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
                                    if (!mainAppNavigator.backStack.contains(ExportSettingsRoute)) {
                                        mainAppNavigator.openExportSettings()
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

private fun NavKey?.isOnboardingRoute(): Boolean =
    when (this) {
        OnboardingStart,
        PersonalIntroRoute,
        OnboardingAppOverviewRoute,
        OnboardingImportRoute,
        OnboardingMemorySelectionRoute,
        OnboardingAccountCreationRoute,
        OnboardingBirthdayRoute,
        OnboardingRecommendationsRoute,
        OnboardingDayBoundariesRoute,
        OnboardingLocationTimelineRoute,
        OnboardingNotificationsRoute,
        OnboardingCompleteRoute,
        OnboardingWelcomeBackRoute,
        -> true

        else -> false
    }

private fun NavKey.onboardingRouteName(): String =
    when (this) {
        OnboardingStart -> "start"
        PersonalIntroRoute -> "personal_intro"
        OnboardingAppOverviewRoute -> "app_overview"
        OnboardingImportRoute -> "memory_import"
        OnboardingMemorySelectionRoute -> "memory_selection"
        OnboardingAccountCreationRoute -> "account"
        OnboardingBirthdayRoute -> "birthday"
        OnboardingRecommendationsRoute -> "recommendations"
        OnboardingDayBoundariesRoute -> "day_boundaries"
        OnboardingLocationTimelineRoute -> "location"
        OnboardingNotificationsRoute -> "notifications"
        OnboardingCompleteRoute -> "complete"
        OnboardingWelcomeBackRoute -> "welcome_back"
        else -> "start"
    }

private fun onboardingRouteForName(routeName: String?): NavKey? =
    when (routeName) {
        "start" -> OnboardingStart
        "personal_intro" -> PersonalIntroRoute
        "app_overview" -> OnboardingAppOverviewRoute
        "memory_import" -> OnboardingImportRoute
        "memory_selection" -> OnboardingMemorySelectionRoute
        "account" -> OnboardingAccountCreationRoute
        "birthday" -> OnboardingBirthdayRoute
        "recommendations" -> OnboardingRecommendationsRoute
        "day_boundaries" -> OnboardingDayBoundariesRoute
        "location" -> OnboardingLocationTimelineRoute
        "notifications" -> OnboardingNotificationsRoute
        "complete" -> OnboardingCompleteRoute
        "welcome_back" -> OnboardingWelcomeBackRoute
        else -> null
    }

private fun MainAppNavigator.restoreOnboardingRoute(route: NavKey) {
    safelyClearBackstack(OnboardingStart)
    if (route != OnboardingStart) {
        backStack.add(route)
    }
}
