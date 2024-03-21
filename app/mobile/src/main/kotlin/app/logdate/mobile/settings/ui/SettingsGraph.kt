package app.logdate.mobile.settings.ui

import androidx.compose.material3.Text
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import app.logdate.mobile.ui.navigation.RouteDestination

/**
 * Navigation graph for all app settings.
 */
fun NavGraphBuilder.settingsGraph() {
    navigation("overview", route = RouteDestination.Settings.route) {
        composable(route = "overview") {
            Text("Settings")
        }
    }
}