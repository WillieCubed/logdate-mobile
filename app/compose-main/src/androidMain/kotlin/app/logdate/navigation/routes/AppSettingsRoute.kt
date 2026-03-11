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
import app.logdate.feature.core.settings.ui.DangerZoneSettingsScreen
import app.logdate.feature.core.settings.ui.DataSettingsScreen
import app.logdate.feature.core.settings.ui.LocationSettingsScreen
import app.logdate.feature.core.settings.ui.PrivacySettingsScreen
import app.logdate.feature.core.settings.ui.SettingsOverviewScreen
import app.logdate.feature.core.settings.ui.devices.DevicesScreen
import app.logdate.feature.location.timeline.ui.LocationTimelineBottomSheet
import app.logdate.navigation.MainAppNavigator
import app.logdate.navigation.routes.core.AccountSettingsRoute
import app.logdate.navigation.routes.core.AdvancedSettingsRoute
import app.logdate.navigation.routes.core.DangerZoneSettingsRoute
import app.logdate.navigation.routes.core.DataSettingsRoute
import app.logdate.navigation.routes.core.DevicesSettingsRoute
import app.logdate.navigation.routes.core.LocationSettingsRoute
import app.logdate.navigation.routes.core.OnboardingStart
import app.logdate.navigation.routes.core.PrivacySettingsRoute
import app.logdate.navigation.routes.core.SettingsOverviewRoute
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
 * Opens the data and storage settings screen.
 */
fun MainAppNavigator.openDataSettings() {
    backStack.add(DataSettingsRoute)
}

/**
 * Opens the danger zone settings screen with destructive actions.
 */
fun MainAppNavigator.openDangerZoneSettings() {
    backStack.add(DangerZoneSettingsRoute)
}

/**
 * Opens the advanced settings screen for server configuration and developer options.
 */
fun MainAppNavigator.openAdvancedSettings() {
    backStack.add(AdvancedSettingsRoute)
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
    onNavigateToCloudAccountCreation: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToAccount: () -> Unit,
    onNavigateToPrivacy: () -> Unit,
    onNavigateToData: () -> Unit,
    onNavigateToDevices: () -> Unit,
    onNavigateToDangerZone: () -> Unit,
    onNavigateToLocation: () -> Unit,
    onOpenLocationTimeline: () -> Unit,
    onNavigateToAdvanced: () -> Unit,
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
            onNavigateToPrivacy = onNavigateToPrivacy,
            onNavigateToData = onNavigateToData,
            onNavigateToDangerZone = onNavigateToDangerZone,
            onNavigateToDevices = onNavigateToDevices,
            onNavigateToLocation = onNavigateToLocation,
            onNavigateToAdvanced = onNavigateToAdvanced,
        )
    }

    // Profile screen (navigates away from settings context, no pane metadata)
    routeEntry<ProfileRoute>(
        metadata = ListDetailSceneStrategy.detailPane(),
    ) { _ ->
        ProfileScreen(
            onBack = onBack,
        )
    }

    // Account management settings screen (detail pane)
    routeEntry<AccountSettingsRoute>(
        metadata = ListDetailSceneStrategy.detailPane(),
    ) { _ ->
        AccountSettingsScreen(
            onBack = onBack,
            onNavigateToCloudAccountCreation = onNavigateToCloudAccountCreation,
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

    // Data and storage settings screen (detail pane)
    routeEntry<DataSettingsRoute>(
        metadata = ListDetailSceneStrategy.detailPane(),
    ) { _ ->
        val context = LocalContext.current
        DataSettingsScreen(
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

    // Location settings screen (detail pane)
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

    // Advanced settings screen (detail pane)
    routeEntry<AdvancedSettingsRoute>(
        metadata = ListDetailSceneStrategy.detailPane(),
    ) { _ ->
        AdvancedSettingsScreen(
            onBack = onBack,
        )
    }
}
