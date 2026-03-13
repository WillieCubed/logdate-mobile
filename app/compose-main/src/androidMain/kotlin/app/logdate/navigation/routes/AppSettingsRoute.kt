@file:OptIn(ExperimentalMaterial3AdaptiveApi::class)

package app.logdate.navigation.routes

import android.content.Intent
import android.net.Uri
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import app.logdate.feature.core.profile.ui.ProfileScreen
import app.logdate.feature.core.settings.ui.AccountSettingsScreen
import app.logdate.feature.core.settings.ui.AdvancedSettingsScreen
import app.logdate.feature.core.settings.ui.BirthdaySettingsScreen
import app.logdate.feature.core.settings.ui.DangerZoneSettingsScreen
import app.logdate.feature.core.settings.ui.ExportSettingsScreen
import app.logdate.feature.core.settings.ui.LocationAdvancedScreen
import app.logdate.feature.core.settings.ui.LocationIntervalScreen
import app.logdate.feature.core.settings.ui.LocationSettingsScreen
import app.logdate.feature.core.settings.ui.LocationTrackingOptionsScreen
import app.logdate.feature.core.settings.ui.MemoriesSettingsScreen
import app.logdate.feature.core.settings.ui.PrivacySettingsScreen
import app.logdate.feature.core.settings.ui.RecommendationSettingsScreen
import app.logdate.feature.core.settings.ui.SettingsOverviewScreen
import app.logdate.feature.core.settings.ui.SyncSettingsScreen
import app.logdate.feature.core.settings.ui.devices.DevicesScreen
import app.logdate.feature.location.timeline.ui.LocationTimelineBottomSheet
import app.logdate.navigation.MainAppNavigator
import app.logdate.navigation.routes.core.AccountSettingsRoute
import app.logdate.navigation.routes.core.AdvancedSettingsRoute
import app.logdate.navigation.routes.core.BirthdaySettingsRoute
import app.logdate.navigation.routes.core.DangerZoneSettingsRoute
import app.logdate.navigation.routes.core.DevicesSettingsRoute
import app.logdate.navigation.routes.core.ExportSettingsRoute
import app.logdate.navigation.routes.core.LocationAdvancedRoute
import app.logdate.navigation.routes.core.LocationIntervalRoute
import app.logdate.navigation.routes.core.LocationSettingsRoute
import app.logdate.navigation.routes.core.LocationTrackingOptionsRoute
import app.logdate.navigation.routes.core.MemoriesSettingsRoute
import app.logdate.navigation.routes.core.OnboardingStart
import app.logdate.navigation.routes.core.PrivacySettingsRoute
import app.logdate.navigation.routes.core.RecommendationSettingsRoute
import app.logdate.navigation.routes.core.SettingsOverviewRoute
import app.logdate.navigation.routes.core.SyncSettingsRoute
import app.logdate.navigation.scenes.SettingsEmptyDetailPane
import io.github.aakira.napier.Napier

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
 * Opens the danger zone settings screen with destructive actions.
 */
fun MainAppNavigator.openDangerZoneSettings() {
    backStack.add(DangerZoneSettingsRoute)
}

/**
 * Opens the memories personalization settings screen.
 */
fun MainAppNavigator.openMemoriesSettings() {
    backStack.add(MemoriesSettingsRoute)
}

/**
 * Opens the recommendations detail settings screen.
 */
fun MainAppNavigator.openRecommendationSettings() {
    backStack.add(RecommendationSettingsRoute)
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
    onNavigateToDangerZone: () -> Unit,
    onNavigateToLocation: () -> Unit,
    onOpenLocationTimeline: () -> Unit,
    onNavigateToMemories: () -> Unit,
    onNavigateToRecommendations: () -> Unit,
    onNavigateToAdvanced: () -> Unit,
    onNavigateToSync: () -> Unit,
    onNavigateToExport: () -> Unit,
    onNavigateToLocationTrackingOptions: () -> Unit,
    onNavigateToLocationInterval: () -> Unit,
    onNavigateToLocationAdvanced: () -> Unit,
    onNavigateToBirthday: () -> Unit,
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
            onNavigateToDangerZone = onNavigateToDangerZone,
            onNavigateToLocation = onNavigateToLocation,
            onNavigateToMemories = onNavigateToMemories,
            onNavigateToSync = onNavigateToSync,
            onNavigateToExport = onNavigateToExport,
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
        )
    }

    // Export & Import settings screen (detail pane)
    routeEntry<ExportSettingsRoute>(
        metadata = ListDetailSceneStrategy.detailPane(),
    ) { _ ->
        val context = LocalContext.current
        ExportSettingsScreen(
            onBack = onBack,
            onShareFile = { path ->
                try {
                    val uri = Uri.parse(path)
                    val shareIntent =
                        Intent(Intent.ACTION_SEND).apply {
                            type = "application/zip"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                    context.startActivity(Intent.createChooser(shareIntent, null))
                } catch (e: Exception) {
                    Napier.e("Failed to share export file", e)
                }
            },
        )
    }

    // Danger zone settings screen with destructive actions (detail pane)
    routeEntry<DangerZoneSettingsRoute>(
        metadata = ListDetailSceneStrategy.detailPane(),
    ) { _ ->
        DangerZoneSettingsScreen(
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

    // Memories settings overview (detail pane)
    routeEntry<MemoriesSettingsRoute>(
        metadata = ListDetailSceneStrategy.detailPane(),
    ) { _ ->
        MemoriesSettingsScreen(
            onBack = onBack,
            onNavigateToRecommendations = onNavigateToRecommendations,
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

    // Advanced settings screen (hidden from main settings overview)
    routeEntry<AdvancedSettingsRoute>(
        metadata = ListDetailSceneStrategy.detailPane(),
    ) { _ ->
        AdvancedSettingsScreen(
            onBack = onBack,
        )
    }
}
