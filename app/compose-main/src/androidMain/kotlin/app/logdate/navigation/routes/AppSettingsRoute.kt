@file:OptIn(ExperimentalMaterial3AdaptiveApi::class)

package app.logdate.navigation.routes

import android.content.Intent
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import app.logdate.client.permissions.rememberHealthConnectPermissionState
import app.logdate.client.settings.NotificationSettingsScreen
import app.logdate.feature.core.profile.ui.ProfileScreen
import app.logdate.feature.core.settings.ui.AccountSettingsScreen
import app.logdate.feature.core.settings.ui.AdvancedSettingsScreen
import app.logdate.feature.core.settings.ui.BirthdaySettingsScreen
import app.logdate.feature.core.settings.ui.ClearDataSettingsScreen
import app.logdate.feature.core.settings.ui.DayBoundarySettingsScreen
import app.logdate.feature.core.settings.ui.ExportSettingsScreen
import app.logdate.feature.core.settings.ui.LibrarySettingsScreen
import app.logdate.feature.core.settings.ui.LocationAdvancedScreen
import app.logdate.feature.core.settings.ui.LocationIntervalScreen
import app.logdate.feature.core.settings.ui.LocationSettingsScreen
import app.logdate.feature.core.settings.ui.LocationTrackingOptionsScreen
import app.logdate.feature.core.settings.ui.MemoriesSettingsScreen
import app.logdate.feature.core.settings.ui.PrivacySettingsScreen
import app.logdate.feature.core.settings.ui.RecommendationSettingsScreen
import app.logdate.feature.core.settings.ui.ResetAppSettingsScreen
import app.logdate.feature.core.settings.ui.ResetSettingsScreen
import app.logdate.feature.core.settings.ui.SettingsOverviewScreen
import app.logdate.feature.core.settings.ui.StreakSettingsScreen
import app.logdate.feature.core.settings.ui.SyncSettingsScreen
import app.logdate.feature.core.settings.ui.TimelineSettingsScreen
import app.logdate.feature.core.settings.ui.TimelineSettingsViewModel
import app.logdate.feature.core.settings.ui.devices.DevicesScreen
import app.logdate.feature.core.settings.ui.watch.WatchNotificationSettingsScreen
import app.logdate.feature.core.settings.ui.watch.WatchSettingsScreen
import app.logdate.feature.core.settings.ui.watch.WatchSettingsViewModel
import app.logdate.feature.core.settings.ui.watch.WatchSyncSettingsScreen
import app.logdate.feature.core.settings.ui.watch.WatchTroubleshootingScreen
import app.logdate.feature.location.timeline.ui.LocationTimelineBottomSheet
import app.logdate.navigation.MainAppNavigator
import app.logdate.navigation.routes.core.AccountSettingsRoute
import app.logdate.navigation.routes.core.AdvancedSettingsRoute
import app.logdate.navigation.routes.core.BirthdaySettingsRoute
import app.logdate.navigation.routes.core.ClearDataSettingsRoute
import app.logdate.navigation.routes.core.DayBoundarySettingsRoute
import app.logdate.navigation.routes.core.DevicesSettingsRoute
import app.logdate.navigation.routes.core.ExportSettingsRoute
import app.logdate.navigation.routes.core.LibrarySettingsRoute
import app.logdate.navigation.routes.core.LocationAdvancedRoute
import app.logdate.navigation.routes.core.LocationIntervalRoute
import app.logdate.navigation.routes.core.LocationSettingsRoute
import app.logdate.navigation.routes.core.LocationTrackingOptionsRoute
import app.logdate.navigation.routes.core.MemoriesSettingsRoute
import app.logdate.navigation.routes.core.NotificationsSettingsRoute
import app.logdate.navigation.routes.core.OnboardingStart
import app.logdate.navigation.routes.core.PrivacySettingsRoute
import app.logdate.navigation.routes.core.RecommendationSettingsRoute
import app.logdate.navigation.routes.core.ResetAppSettingsRoute
import app.logdate.navigation.routes.core.ResetSettingsRoute
import app.logdate.navigation.routes.core.SettingsOverviewRoute
import app.logdate.navigation.routes.core.StreakSettingsRoute
import app.logdate.navigation.routes.core.SyncSettingsRoute
import app.logdate.navigation.routes.core.TimelineSettingsRoute
import app.logdate.navigation.routes.core.WatchNotificationSettingsRoute
import app.logdate.navigation.routes.core.WatchSettingsRoute
import app.logdate.navigation.routes.core.WatchSyncSettingsRoute
import app.logdate.navigation.routes.core.WatchTroubleshootingRoute
import app.logdate.navigation.scenes.SettingsEmptyDetailPane
import io.github.aakira.napier.Napier
import org.koin.compose.viewmodel.koinViewModel

/**
 * Resets the app by safely clearing the back stack and navigating to the onboarding start screen.
 * This implementation ensures the backstack is never empty during the operation.
 */
fun MainAppNavigator.resetApp() {
    // Make sure the onboarding start route is in the backstack
    if (!backStack.contains(OnboardingStart)) {
        backStack.add(OnboardingStart)
    }

    // Navigate to onboarding start, keeping it as the first (and only) entry in the backstack
    safelyPopBackstackTo(OnboardingStart, keepFirst = true)
}

/**
 * Extension function to open the main settings overview screen.
 */
fun MainAppNavigator.openSettings() {
    backStack.add(SettingsOverviewRoute)
}

/**
 * Opens the account management settings screen.
 */
fun MainAppNavigator.openAccountSettings() {
    backStack.add(AccountSettingsRoute)
}

/**
 * Opens the profile screen with Material 3 Expressive design.
 */
fun MainAppNavigator.openProfile() {
    backStack.add(ProfileRoute)
}

/**
 * Opens the privacy and security settings screen.
 */
fun MainAppNavigator.openPrivacySettings() {
    backStack.add(PrivacySettingsRoute)
}

/**
 * Opens the location settings screen.
 */
fun MainAppNavigator.openLocationSettings() {
    backStack.add(LocationSettingsRoute)
}

/**
 * Opens the connected devices settings screen.
 */
fun MainAppNavigator.openDevicesSettings() {
    backStack.add(DevicesSettingsRoute)
}

/**
 * Opens Android notification settings for the app.
 */
fun MainAppNavigator.openNotificationSettings() {
    backStack.add(NotificationsSettingsRoute)
}

/**
 * Opens the library settings screen.
 */
fun MainAppNavigator.openLibrarySettings() {
    backStack.add(LibrarySettingsRoute)
}

/**
 * Opens the memories personalization settings screen.
 */
fun MainAppNavigator.openMemoriesSettings() {
    backStack.add(MemoriesSettingsRoute)
}

/**
 * Opens the reset settings hub screen.
 */
fun MainAppNavigator.openResetSettings() {
    backStack.add(ResetSettingsRoute)
}

/**
 * Opens the clear data detail screen.
 */
fun MainAppNavigator.openClearDataSettings() {
    backStack.add(ClearDataSettingsRoute)
}

/**
 * Opens the reset app detail screen.
 */
fun MainAppNavigator.openResetAppSettings() {
    backStack.add(ResetAppSettingsRoute)
}

/**
 * Opens the recommendations detail settings screen.
 */
fun MainAppNavigator.openRecommendationSettings() {
    backStack.add(RecommendationSettingsRoute)
}

/**
 * Opens the streak settings detail screen.
 */
fun MainAppNavigator.openStreakSettings() {
    backStack.add(StreakSettingsRoute)
}

fun MainAppNavigator.openTimelineSettings() {
    backStack.add(TimelineSettingsRoute)
}

fun MainAppNavigator.openDayBoundarySettings() {
    backStack.add(DayBoundarySettingsRoute)
}

/**
 * Opens the advanced settings screen for app updates.
 */
fun MainAppNavigator.openAdvancedSettings() {
    backStack.add(AdvancedSettingsRoute)
}

/**
 * Opens the sync and backup settings screen.
 */
fun MainAppNavigator.openSyncSettings() {
    backStack.add(SyncSettingsRoute)
}

/**
 * Opens the export and import settings screen.
 */
fun MainAppNavigator.openExportSettings() {
    backStack.add(ExportSettingsRoute)
}

/**
 * Opens the location tracking options detail screen.
 */
fun MainAppNavigator.openLocationTrackingOptions() {
    backStack.add(LocationTrackingOptionsRoute)
}

/**
 * Opens the location update interval detail screen.
 */
fun MainAppNavigator.openLocationInterval() {
    backStack.add(LocationIntervalRoute)
}

/**
 * Opens the location advanced settings detail screen.
 */
fun MainAppNavigator.openLocationAdvanced() {
    backStack.add(LocationAdvancedRoute)
}

/**
 * Opens the birthday personalization detail screen.
 */
fun MainAppNavigator.openBirthdaySettings() {
    backStack.add(BirthdaySettingsRoute)
}

/**
 * Opens the watch settings hub screen.
 */
fun MainAppNavigator.openWatchSettings() {
    backStack.add(WatchSettingsRoute)
}

/**
 * Opens the watch sync settings detail screen.
 */
fun MainAppNavigator.openWatchSyncSettings() {
    backStack.add(WatchSyncSettingsRoute)
}

/**
 * Opens the watch notification settings detail screen.
 */
fun MainAppNavigator.openWatchNotificationSettings() {
    backStack.add(WatchNotificationSettingsRoute)
}

/**
 * Opens the watch troubleshooting detail screen.
 */
fun MainAppNavigator.openWatchTroubleshooting() {
    backStack.add(WatchTroubleshootingRoute)
}

/**
 * Provides the navigation routes for app settings-related screens.
 *
 * Uses Navigation3's [ListDetailSceneStrategy] for adaptive list-detail layouts.
 * The settings overview is the list pane, and all detail screens are detail panes.
 * On tablets (≥600dp), both panes are shown side-by-side. On phones, detail screens
 * are shown fullscreen with a back button.
 */
fun EntryProviderScope<NavKey>.appSettingsRoutes(
    onBack: () -> Unit,
    onAppReset: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToAccount: () -> Unit,
    onNavigateToDevices: () -> Unit,
    onNavigateToWatch: () -> Unit,
    onNavigateToLocation: () -> Unit,
    onNavigateToPrivacy: () -> Unit,
    onOpenLocationTimeline: () -> Unit,
    onNavigateToLibrarySettings: () -> Unit,
    onNavigateToMemories: () -> Unit,
    onNavigateToNotifications: () -> Unit,
    onNavigateToRecommendations: () -> Unit,
    onNavigateToTimeline: () -> Unit,
    onNavigateToDayBoundary: () -> Unit,
    onNavigateToAdvanced: () -> Unit,
    onNavigateToSync: () -> Unit,
    onNavigateToExport: () -> Unit,
    onNavigateToReset: () -> Unit,
    onNavigateToClearData: () -> Unit,
    onNavigateToResetApp: () -> Unit,
    onNavigateToLocationTrackingOptions: () -> Unit,
    onNavigateToLocationInterval: () -> Unit,
    onNavigateToLocationAdvanced: () -> Unit,
    onNavigateToBirthday: () -> Unit,
    onNavigateToWatchSync: () -> Unit,
    onNavigateToWatchNotifications: () -> Unit,
    onNavigateToWatchTroubleshooting: () -> Unit,
    onNavigateToStreaks: () -> Unit = {},
    onNavigateToCloudAccountCreation: () -> Unit = {},
    onNavigateToSignIn: () -> Unit = {},
) {
    // Main settings overview screen (list pane)
    routeEntry<SettingsOverviewRoute>(
        metadata =
            ListDetailSceneStrategy.listPane(
                detailPlaceholder = { SettingsEmptyDetailPane() },
            ),
    ) { _ ->
        SettingsOverviewScreen(
            onBack = onBack,
            onNavigateToProfile = onNavigateToProfile,
            onNavigateToAccount = onNavigateToAccount,
            onNavigateToDevices = onNavigateToDevices,
            onNavigateToWatch = onNavigateToWatch,
            onNavigateToLocation = onNavigateToLocation,
            onNavigateToPrivacy = onNavigateToPrivacy,
            onNavigateToLibrarySettings = onNavigateToLibrarySettings,
            onNavigateToMemories = onNavigateToMemories,
            onNavigateToNotifications = onNavigateToNotifications,
            onNavigateToStreaks = onNavigateToStreaks,
            onNavigateToTimeline = onNavigateToTimeline,
            onNavigateToSync = onNavigateToSync,
            onNavigateToExport = onNavigateToExport,
            onNavigateToReset = onNavigateToReset,
            onNavigateToCloudAccountCreation = onNavigateToCloudAccountCreation,
            onNavigateToSignIn = onNavigateToSignIn,
        )
    }

    // Profile screen
    routeEntry<ProfileRoute>(
        metadata = ListDetailSceneStrategy.detailPane(),
    ) { _ ->
        ProfileScreen(
            onBack = onBack,
            onNavigateToBirthday = onNavigateToBirthday,
        )
    }

    // Birthday personalization detail screen
    routeEntry<BirthdaySettingsRoute>(
        metadata = ListDetailSceneStrategy.detailPane(),
    ) { _ ->
        BirthdaySettingsScreen(
            onBack = onBack,
        )
    }

    // Account & Sign-In settings screen (detail pane)
    routeEntry<AccountSettingsRoute>(
        metadata = ListDetailSceneStrategy.detailPane(),
    ) { _ ->
        AccountSettingsScreen(
            onBack = onBack,
        )
    }

    // Connected devices screen (detail pane)
    routeEntry<DevicesSettingsRoute>(
        metadata = ListDetailSceneStrategy.detailPane(),
    ) { _ ->
        DevicesScreen(
            onBackClick = onBack,
        )
    }

    // Android notification settings screen (detail pane)
    routeEntry<NotificationsSettingsRoute>(
        metadata = ListDetailSceneStrategy.detailPane(),
    ) { _ ->
        NotificationSettingsScreen(
            onBack = onBack,
        )
    }

    // Privacy and security settings screen (detail pane)
    routeEntry<PrivacySettingsRoute>(
        metadata = ListDetailSceneStrategy.detailPane(),
    ) { _ ->
        PrivacySettingsScreen(
            onBack = onBack,
            onNavigateToLocationSettings = onNavigateToLocation,
        )
    }

    // Sync & Backup settings screen (detail pane)
    routeEntry<SyncSettingsRoute>(
        metadata = ListDetailSceneStrategy.detailPane(),
    ) { _ ->
        SyncSettingsScreen(
            onBack = onBack,
            onNavigateToCloudAccountCreation = onNavigateToCloudAccountCreation,
            onNavigateToSignIn = onNavigateToSignIn,
        )
    }

    // Export & Import settings screen (detail pane)
    routeEntry<ExportSettingsRoute>(
        metadata = ListDetailSceneStrategy.detailPane(),
    ) { _ ->
        val context = LocalContext.current
        ExportSettingsScreen(
            onBack = onBack,
            onBrowseFile = { path ->
                try {
                    // Open the system Downloads folder so the user can see the exported file.
                    // ACTION_VIEW_DOWNLOADS is the most reliable way to open the
                    // containing folder rather than trying to extract the ZIP.
                    val browseIntent =
                        Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS)
                    context.startActivity(browseIntent)
                } catch (e: Exception) {
                    Napier.e("Failed to open downloads folder", e)
                }
            },
        )
    }

    // Reset settings hub (detail pane)
    routeEntry<ResetSettingsRoute>(
        metadata = ListDetailSceneStrategy.detailPane(),
    ) { _ ->
        ResetSettingsScreen(
            onBack = onBack,
            onNavigateToClearData = onNavigateToClearData,
            onNavigateToResetApp = onNavigateToResetApp,
        )
    }

    // Clear data detail screen (detail pane)
    routeEntry<ClearDataSettingsRoute>(
        metadata = ListDetailSceneStrategy.detailPane(),
    ) { _ ->
        ClearDataSettingsScreen(
            onBack = onBack,
        )
    }

    // Reset app detail screen (detail pane)
    routeEntry<ResetAppSettingsRoute>(
        metadata = ListDetailSceneStrategy.detailPane(),
    ) { _ ->
        ResetAppSettingsScreen(
            onBack = onBack,
            onAppReset = onAppReset,
        )
    }

    // Location settings overview screen (detail pane)
    routeEntry<LocationSettingsRoute>(
        metadata = ListDetailSceneStrategy.detailPane(),
    ) { _ ->
        var showLocationQuickPeek by rememberSaveable { mutableStateOf(false) }

        LocationSettingsScreen(
            onBack = onBack,
            onOpenLocationTimeline = onOpenLocationTimeline,
            onShowLocationTimeline = {
                showLocationQuickPeek = true
            },
            onNavigateToTrackingOptions = onNavigateToLocationTrackingOptions,
            onNavigateToInterval = onNavigateToLocationInterval,
            onNavigateToAdvanced = onNavigateToLocationAdvanced,
        )

        if (showLocationQuickPeek) {
            LocationTimelineBottomSheet(
                onDismissRequest = {
                    showLocationQuickPeek = false
                },
                onOpenFullTimeline = {
                    showLocationQuickPeek = false
                    onOpenLocationTimeline()
                },
            )
        }
    }

    // Location tracking options detail screen
    routeEntry<LocationTrackingOptionsRoute>(
        metadata = ListDetailSceneStrategy.detailPane(),
    ) { _ ->
        LocationTrackingOptionsScreen(
            onBack = onBack,
        )
    }

    // Location interval detail screen
    routeEntry<LocationIntervalRoute>(
        metadata = ListDetailSceneStrategy.detailPane(),
    ) { _ ->
        LocationIntervalScreen(
            onBack = onBack,
        )
    }

    // Location advanced detail screen
    routeEntry<LocationAdvancedRoute>(
        metadata = ListDetailSceneStrategy.detailPane(),
    ) { _ ->
        LocationAdvancedScreen(
            onBack = onBack,
        )
    }

    // Library settings (detail pane)
    routeEntry<LibrarySettingsRoute>(
        metadata = ListDetailSceneStrategy.detailPane(),
    ) { _ ->
        LibrarySettingsScreen(
            onBack = onBack,
        )
    }

    // Memories settings overview (detail pane)
    routeEntry<MemoriesSettingsRoute>(
        metadata = ListDetailSceneStrategy.detailPane(),
    ) { _ ->
        MemoriesSettingsScreen(
            onBack = onBack,
            onNavigateToRecommendations = onNavigateToRecommendations,
        )
    }

    // Streak settings detail (detail pane)
    routeEntry<StreakSettingsRoute>(
        metadata = ListDetailSceneStrategy.detailPane(),
    ) { _ ->
        StreakSettingsScreen(
            onBack = onBack,
        )
    }

    // Recommendations detail (detail pane)
    routeEntry<RecommendationSettingsRoute>(
        metadata = ListDetailSceneStrategy.detailPane(),
    ) { _ ->
        RecommendationSettingsScreen(
            onBack = onBack,
        )
    }

    routeEntry<TimelineSettingsRoute>(
        metadata = ListDetailSceneStrategy.detailPane(),
    ) { _ ->
        TimelineSettingsScreen(
            onBack = onBack,
            onNavigateToDayBoundary = onNavigateToDayBoundary,
        )
    }

    routeEntry<DayBoundarySettingsRoute>(
        metadata = ListDetailSceneStrategy.detailPane(),
    ) { _ ->
        val viewModel: TimelineSettingsViewModel = koinViewModel()
        val uiState by viewModel.uiState.collectAsState()
        val healthConnectPermissionState = rememberHealthConnectPermissionState()
        var enableAfterPermission by rememberSaveable { mutableStateOf(false) }

        LaunchedEffect(healthConnectPermissionState.completedRequestCount) {
            if (healthConnectPermissionState.completedRequestCount > 0) {
                viewModel.refreshHealthStatus()
            }
        }

        LaunchedEffect(enableAfterPermission, healthConnectPermissionState.completedRequestCount, uiState.healthConnectStatus) {
            if (!enableAfterPermission || healthConnectPermissionState.completedRequestCount == 0) {
                return@LaunchedEffect
            }

            when (uiState.healthConnectStatus) {
                app.logdate.client.domain.dayboundary.HealthConnectStatus.CONNECTED -> {
                    viewModel.toggleSleepBasedBoundaries(true)
                    enableAfterPermission = false
                }

                app.logdate.client.domain.dayboundary.HealthConnectStatus.PERMISSIONS_NEEDED,
                app.logdate.client.domain.dayboundary.HealthConnectStatus.NOT_AVAILABLE,
                -> {
                    enableAfterPermission = false
                }

                app.logdate.client.domain.dayboundary.HealthConnectStatus.CHECKING -> Unit
            }
        }

        DayBoundarySettingsScreen(
            onBack = onBack,
            viewModel = viewModel,
            onRequestHealthPermissions = healthConnectPermissionState.requestPermission,
            onEnableSleepBasedWithPermissions = {
                enableAfterPermission = true
                healthConnectPermissionState.requestPermission()
            },
        )
    }

    // Advanced settings screen (hidden from main settings overview)
    routeEntry<AdvancedSettingsRoute>(
        metadata = ListDetailSceneStrategy.detailPane(),
    ) { _ ->
        AdvancedSettingsScreen(
            onBack = onBack,
        )
    }

    // Watch settings hub screen
    routeEntry<WatchSettingsRoute>(
        metadata = ListDetailSceneStrategy.detailPane(),
    ) { _ ->
        WatchSettingsScreen(
            onBack = onBack,
            onNavigateToSync = onNavigateToWatchSync,
            onNavigateToNotifications = onNavigateToWatchNotifications,
            onNavigateToTroubleshooting = onNavigateToWatchTroubleshooting,
        )
    }

    // Watch sync settings detail screen
    routeEntry<WatchSyncSettingsRoute>(
        metadata = ListDetailSceneStrategy.detailPane(),
    ) { _ ->
        val viewModel: WatchSettingsViewModel = koinViewModel()
        WatchSyncSettingsScreen(
            onBack = onBack,
            viewModel = viewModel,
        )
    }

    // Watch notification settings detail screen
    routeEntry<WatchNotificationSettingsRoute>(
        metadata = ListDetailSceneStrategy.detailPane(),
    ) { _ ->
        val viewModel: WatchSettingsViewModel = koinViewModel()
        WatchNotificationSettingsScreen(
            onBack = onBack,
            viewModel = viewModel,
        )
    }

    // Watch troubleshooting detail screen
    routeEntry<WatchTroubleshootingRoute>(
        metadata = ListDetailSceneStrategy.detailPane(),
    ) { _ ->
        val viewModel: WatchSettingsViewModel = koinViewModel()
        WatchTroubleshootingScreen(
            onBack = onBack,
            viewModel = viewModel,
        )
    }
}
