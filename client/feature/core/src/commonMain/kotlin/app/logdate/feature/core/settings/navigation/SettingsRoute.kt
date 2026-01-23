package app.logdate.feature.core.settings.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.logdate.feature.core.settings.ui.AccountSettingsScreen
import app.logdate.feature.core.settings.ui.BirthdaySettingsScreen
import app.logdate.feature.core.settings.ui.DangerZoneSettingsScreen
import app.logdate.feature.core.settings.ui.DataSettingsScreen
import app.logdate.feature.core.settings.ui.LocationSettingsScreen
import app.logdate.feature.core.settings.ui.PrivacySettingsScreen
import app.logdate.feature.core.settings.ui.SettingsOverviewScreen
import app.logdate.feature.core.settings.ui.devices.DevicesScreen
import kotlinx.serialization.Serializable

@Serializable
data class SettingsRoute(
    val settingId: String? = null,
    val selectedDetail: String? = null,
)

@Serializable
data class DevicesRoute(val id: String = "devices")

@Serializable
data object AccountSettingsRoute

@Serializable
data object PrivacySettingsRoute

@Serializable
data object DataSettingsRoute

@Serializable
data object LocationSettingsRoute

@Serializable
data object DangerZoneSettingsRoute

@Serializable
data object BirthdaySettingsRoute

/**
 * Navigates to the settings screen.
 *
 * @param settingId The ID of the setting to navigate to.
 * @param selectedDetail The detail setting to show (for list-detail layouts).
 */
fun NavController.navigateToSettings(settingId: String? = null, selectedDetail: String? = null) {
    navigate(SettingsRoute(settingId, selectedDetail))
}

/**
 * Navigates to settings with a specific detail selected.
 *
 * @param detailType The type of detail setting to show.
 */
fun NavController.navigateToSettingsDetail(detailType: String) {
    navigate(SettingsRoute(selectedDetail = detailType))
}

fun NavController.navigateToAccountSettings() {
    navigate(AccountSettingsRoute)
}

fun NavController.navigateToPrivacySettings() {
    navigate(PrivacySettingsRoute)
}

fun NavController.navigateToDataSettings() {
    navigate(DataSettingsRoute)
}

fun NavController.navigateToLocationSettings() {
    navigate(LocationSettingsRoute)
}

fun NavController.navigateToDangerZoneSettings() {
    navigate(DangerZoneSettingsRoute)
}

fun NavController.navigateToBirthdaySettings() {
    navigate(BirthdaySettingsRoute)
}

/**
 * Navigates to the devices screen.
 */
fun NavController.navigateToDevices() {
    navigate(DevicesRoute())
}

/**
 * Navigation graph for all app settings.
 *
 * @param onGoBack The action to perform when the user navigates back.
 * @param onAppReset The action to perform after the user has reset the app.
 * @param onNavigateToCloudAccountCreation The action to perform when navigating to cloud account creation.
 */
fun NavGraphBuilder.settingsDestination(
    onGoBack: () -> Unit,
    onAppReset: () -> Unit,
    onNavigateToCloudAccountCreation: () -> Unit,
    onNavigateToProfile: () -> Unit,
    navController: NavController,
) {
    composable<SettingsRoute> {
        SettingsOverviewScreen(
            onBack = onGoBack,
            onNavigateToProfile = onNavigateToProfile,
            onNavigateToAccount = {
                navController.navigateToAccountSettings()
            },
            onNavigateToData = {
                navController.navigateToDataSettings()
            },
            onNavigateToPrivacy = {
                navController.navigateToPrivacySettings()
            },
            onNavigateToDevices = {
                navController.navigateToDevices()
            },
            onNavigateToDangerZone = {
                navController.navigateToDangerZoneSettings()
            },
            onNavigateToLocation = {
                navController.navigateToLocationSettings()
            }
        )
    }
    
    composable<DevicesRoute> {
        DevicesScreen(
            onBackClick = {
                navController.popBackStack()
            }
        )
    }

    composable<AccountSettingsRoute> {
        AccountSettingsScreen(
            onBack = { navController.popBackStack() },
            onNavigateToCloudAccountCreation = onNavigateToCloudAccountCreation,
            onNavigateToBirthdaySettings = { navController.navigateToBirthdaySettings() },
        )
    }

    composable<PrivacySettingsRoute> {
        PrivacySettingsScreen(
            onBack = { navController.popBackStack() },
            onNavigateToLocationSettings = { navController.navigateToLocationSettings() },
        )
    }

    composable<DataSettingsRoute> {
        DataSettingsScreen(
            onBack = { navController.popBackStack() },
        )
    }

    composable<LocationSettingsRoute> {
        LocationSettingsScreen(
            onBack = { navController.popBackStack() },
        )
    }

    composable<DangerZoneSettingsRoute> {
        DangerZoneSettingsScreen(
            onBack = { navController.popBackStack() },
            onAppReset = onAppReset,
        )
    }

    composable<BirthdaySettingsRoute> {
        BirthdaySettingsScreen(
            onBack = { navController.popBackStack() },
        )
    }
}
