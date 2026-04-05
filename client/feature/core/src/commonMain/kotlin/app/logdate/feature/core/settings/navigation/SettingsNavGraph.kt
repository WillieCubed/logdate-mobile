package app.logdate.feature.core.settings.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.logdate.feature.core.profile.navigation.ProfileRoute
import app.logdate.feature.core.settings.ui.AccountSettingsScreen
import app.logdate.feature.core.settings.ui.AdvancedSettingsScreen
import app.logdate.feature.core.settings.ui.BirthdaySettingsScreen
import app.logdate.feature.core.settings.ui.DataSettingsScreen
import app.logdate.feature.core.settings.ui.DayBoundarySettingsScreen
import app.logdate.feature.core.settings.ui.ExportSettingsScreen
import app.logdate.feature.core.settings.ui.LibrarySettingsScreen
import app.logdate.feature.core.settings.ui.LocationSettingsScreen
import app.logdate.feature.core.settings.ui.MemoriesSettingsScreen
import app.logdate.feature.core.settings.ui.PrivacySettingsScreen
import app.logdate.feature.core.settings.ui.RecommendationSettingsScreen
import app.logdate.feature.core.settings.ui.ResetSettingsScreen
import app.logdate.feature.core.settings.ui.SettingsOverviewScreen
import app.logdate.feature.core.settings.ui.StreakSettingsScreen
import app.logdate.feature.core.settings.ui.SyncSettingsScreen
import app.logdate.feature.core.settings.ui.TimelineSettingsScreen
import app.logdate.feature.core.settings.ui.devices.DevicesScreen

/**
 * Registers all settings routes in the common navigation graph.
 *
 * This provides the full settings experience for non-Android platforms (desktop, iOS)
 * that don't use Navigation3.
 */
fun NavGraphBuilder.settingsGraph(navController: NavController) {
    composable<SettingsRoute> {
        SettingsOverviewScreen(
            onBack = { navController.popBackStack() },
            onNavigateToProfile = { navController.navigate(ProfileRoute) },
            onNavigateToAccount = { navController.navigate(AccountSettingsRoute) },
            onNavigateToDevices = { navController.navigate(DevicesRoute()) },
            onNavigateToWatch = { /* No Wear OS on desktop */ },
            onNavigateToReset = { navController.navigate(ResetSettingsRoute) },
            onNavigateToLocation = { navController.navigate(LocationSettingsRoute) },
            onNavigateToPrivacy = { navController.navigate(PrivacySettingsRoute) },
            onNavigateToLibrarySettings = { navController.navigate(LibrarySettingsRoute) },
            onNavigateToMemories = { navController.navigate(MemoriesSettingsRoute) },
            onNavigateToStreaks = { navController.navigate(StreakSettingsRoute) },
            onNavigateToTimeline = { navController.navigate(TimelineSettingsRoute) },
            onNavigateToSync = { navController.navigate(SyncSettingsRoute) },
            onNavigateToExport = { navController.navigate(ExportSettingsRoute) },
        )
    }
    composable<AccountSettingsRoute> {
        AccountSettingsScreen(
            onBack = { navController.popBackStack() },
        )
    }
    composable<PrivacySettingsRoute> {
        PrivacySettingsScreen(
            onBack = { navController.popBackStack() },
            onNavigateToLocationSettings = { navController.navigate(LocationSettingsRoute) },
        )
    }
    composable<DataSettingsRoute> {
        DataSettingsScreen(
            onBack = { navController.popBackStack() },
        )
    }
    composable<AdvancedSettingsRoute> {
        AdvancedSettingsScreen(
            onBack = { navController.popBackStack() },
        )
    }
    composable<LocationSettingsRoute> {
        LocationSettingsScreen(
            onBack = { navController.popBackStack() },
            onOpenLocationTimeline = { /* Location timeline not routed from settings on desktop */ },
        )
    }
    composable<MemoriesSettingsRoute> {
        MemoriesSettingsScreen(
            onBack = { navController.popBackStack() },
            onNavigateToRecommendations = { navController.navigate(RecommendationSettingsRoute) },
        )
    }
    composable<StreakSettingsRoute> {
        StreakSettingsScreen(
            onBack = { navController.popBackStack() },
        )
    }
    composable<TimelineSettingsRoute> {
        TimelineSettingsScreen(
            onBack = { navController.popBackStack() },
            onNavigateToDayBoundary = { navController.navigate(DayBoundarySettingsRoute) },
        )
    }
    composable<DayBoundarySettingsRoute> {
        DayBoundarySettingsScreen(
            onBack = { navController.popBackStack() },
        )
    }
    composable<SyncSettingsRoute> {
        SyncSettingsScreen(
            onBack = { navController.popBackStack() },
        )
    }
    composable<ExportSettingsRoute> {
        ExportSettingsScreen(
            onBack = { navController.popBackStack() },
        )
    }
    composable<LibrarySettingsRoute> {
        LibrarySettingsScreen(
            onBack = { navController.popBackStack() },
        )
    }
    composable<ResetSettingsRoute> {
        ResetSettingsScreen(
            onBack = { navController.popBackStack() },
            onNavigateToClearData = { /* Not critical for desktop MVP */ },
            onNavigateToResetApp = { /* Not critical for desktop MVP */ },
        )
    }
    composable<BirthdaySettingsRoute> {
        BirthdaySettingsScreen(
            onBack = { navController.popBackStack() },
        )
    }
    composable<RecommendationSettingsRoute> {
        RecommendationSettingsScreen(
            onBack = { navController.popBackStack() },
        )
    }
    composable<DevicesRoute> {
        DevicesScreen(
            onBackClick = { navController.popBackStack() },
        )
    }
}
