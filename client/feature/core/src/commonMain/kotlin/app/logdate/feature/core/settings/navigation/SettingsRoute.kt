package app.logdate.feature.core.settings.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.logdate.feature.core.settings.ui.SettingsScreen
import kotlinx.serialization.Serializable

@Serializable
data class SettingsRoute(
    val settingId: String? = null,
)

/**
 * Navigates to the settings screen.
 *
 * @param settingId The ID of the setting to navigate to.
 */
fun NavController.navigateToSettings(settingId: String? = null) {
    navigate(SettingsRoute(settingId))
}

/**
 * Navigation graph for all app settings.
 *
 * @param onGoBack The action to perform when the user navigates back.
 * @param onAppReset The action to perform after the user has reset the app.
 */
fun NavGraphBuilder.settingsDestination(
    onGoBack: () -> Unit,
    onAppReset: () -> Unit,
) {
    composable<SettingsRoute> {
        SettingsScreen(
            onBack = onGoBack,
            onAppReset = onAppReset,
        )
    }
}