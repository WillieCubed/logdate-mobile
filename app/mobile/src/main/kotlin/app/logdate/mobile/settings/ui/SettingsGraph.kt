package app.logdate.mobile.settings.ui

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation

const val ROUTE_SETTINGS = "settings"
const val ROUTE_SETTINGS_HOME = "settings/overview"

/**
 * Navigation graph for all app settings.
 */
fun NavGraphBuilder.settingsGraph(
    onGoBack: () -> Unit,
    onReset: () -> Unit,
) {
    navigation(startDestination = ROUTE_SETTINGS_HOME, route = ROUTE_SETTINGS) {
        composable(route = ROUTE_SETTINGS_HOME) {
            SettingsScreen(
                onBack = onGoBack,
                onReset = onReset,
            )
        }
    }
}