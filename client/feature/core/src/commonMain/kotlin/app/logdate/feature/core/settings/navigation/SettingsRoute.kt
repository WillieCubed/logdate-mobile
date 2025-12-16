package app.logdate.feature.core.settings.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
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
    onNavigateToLocation: () -> Unit = {}
) {
    composable<SettingsRoute> {
        SettingsOverviewScreen(
            onBack = onGoBack,
            onNavigateToProfile = onNavigateToProfile,
            onNavigateToAccount = {
                // TODO: Navigate to account settings
                // This will use onNavigateToCloudAccountCreation when needed
            },
            onNavigateToData = {
                // TODO: Implement navigation to data settings
            },
            onNavigateToPrivacy = {
                // TODO: Implement navigation to privacy settings
            },
            onNavigateToDevices = {
                navController.navigateToDevices()
            },
            onNavigateToDangerZone = {
                // This includes app reset functionality
                onAppReset()
            },
            onNavigateToLocation = onNavigateToLocation
        )
    }
    
    composable<DevicesRoute> {
        DevicesScreen(
            onBackClick = {
                navController.popBackStack()
            }
        )
    }
}